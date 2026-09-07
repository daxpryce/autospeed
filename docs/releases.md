# Trusted releases

GitHub Actions is the only supported release builder. Local release builds are
useful for testing but are not publication candidates.

## Trust boundary

The release workflow:

1. accepts only an annotated `vMAJOR.MINOR.PATCH` tag;
2. requires that tag to point at a commit on `main`;
3. checks out the exact tagged commit;
4. builds with the repository's pinned Dev Container CLI and devcontainer;
5. verifies Gradle dependencies and runs every quality gate;
6. signs public framework and Play-compatible APKs with the protected release
   key;
7. rejects an APK that declares `android.permission.INTERNET`;
8. emits SHA-256 checksums;
9. creates GitHub artifact attestations through GitHub OIDC;
10. publishes the APKs and checksums to the matching GitHub release.

The workflow references third-party and GitHub actions by full commit SHA.
Configure the GitHub `release` environment with required reviewers and restrict
deployment to protected tags.

An environment's deployment rules match a tag by name only and cannot express
"this tag is on `main`", so that requirement is asserted in the workflow with
`git merge-base --is-ancestor`. It is a check rather than a gate: the job starts
and fails before the signing key is ever materialized. Branch protection on
`main` is what makes the assertion meaningful.

## Signing key

Generate the key with the devcontainer's `keytool` rather than a host JDK, so
the key is produced by the same pinned toolchain that signs with it:

```bash
mkdir -p .release-secrets && chmod 700 .release-secrets
# Write the password to .release-secrets/store.pw first; never pass it as an
# argument, where it would be visible in the host process list.
./scripts/container-run bash -lc '
  cd /workspaces/autospeed
  keytool -genkeypair -v \
    -keystore .release-secrets/autospeed-release.jks \
    -storetype PKCS12 -alias autospeed \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=YOUR NAME" \
    -storepass:file .release-secrets/store.pw \
    -keypass:file .release-secrets/store.pw'
```

PKCS12 does not support a key password that differs from the store password;
`keytool` warns and ignores a separate `-keypass`. Use one value for both, and
set both GitHub secrets to it.

Move the keystore outside the repository (`~/.keys/autospeed/`, mode 0600),
shred the staging copies, and keep an off-machine backup: a base64 encoding of
the `.jks` in a password-manager secure note, with the password and alias as
hidden fields on the same item so the key and its password cannot be separated.

Losing this key means no installed copy and no Obtainium subscriber can ever
accept another update.

## Published signing fingerprint

Every Autospeed release APK is signed with this certificate:

| Field | Value |
| --- | --- |
| Subject | `CN=Dax Pryce` |
| Key | RSA 4096, SHA384withRSA |
| SHA-256 | `10:DD:51:9E:7B:25:48:87:BA:81:56:04:57:9B:C2:42:70:26:C9:CA:BD:00:11:93:F9:1D:F0:09:2D:F1:93:80` |

This value is public and is not a secret; it is derivable from any signed APK.
It is published so a downloaded APK can be checked against it, and so a release
signed with the wrong key is detectable rather than silent:

```bash
apksigner verify --print-certs autospeed-framework-vX.Y.Z.apk
```

An APK reporting any other fingerprint did not come from this project,
regardless of where it was downloaded. The fingerprint changes only if the
signing key is deliberately rotated, which would be announced in the release
notes for the version that introduces it.

## Repository secrets

Configure these secrets on the protected `release` environment:

| Secret | Value |
| --- | --- |
| `AUTOSPEED_SIGNING_KEY_BASE64` | Base64 encoding of the JKS file |
| `AUTOSPEED_SIGNING_STORE_PASSWORD` | Keystore password |
| `AUTOSPEED_SIGNING_KEY_ALIAS` | Signing-key alias |
| `AUTOSPEED_SIGNING_KEY_PASSWORD` | Signing-key password |

Set `AUTOSPEED_SIGNING_STORE_PASSWORD` and `AUTOSPEED_SIGNING_KEY_PASSWORD` to
the same value, per the PKCS12 note above.

The signing key is materialized only for the build job and removed in an
`always()` cleanup step.

Restrict the environment so the key cannot be used by an ordinary branch push:

```bash
gh api -X PUT repos/OWNER/REPO/environments/release --input - <<'JSON'
{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}
JSON
gh api -X POST repos/OWNER/REPO/environments/release/deployment-branch-policies \
  -f name='v*' -f type=tag
```

## How signing is wired

`app/build.gradle.kts` reads `AUTOSPEED_SIGNING_STORE_FILE`,
`AUTOSPEED_SIGNING_STORE_PASSWORD`, `AUTOSPEED_SIGNING_KEY_ALIAS`, and
`AUTOSPEED_SIGNING_KEY_PASSWORD` from the environment. Nothing about the key is
committed. When the variables are absent the signing configuration is not
created at all, so a clean clone still builds and passes `scripts/check`;
`scripts/release-build` is what requires them.

`AUTOSPEED_VERSION_NAME` and `AUTOSPEED_VERSION_CODE` override the defaults in
`defaultConfig`, so a tagged release carries the tag's version.

The workflow derives both from the tag itself, never from the workflow run
counter. `v1.2.3` becomes version name `1.2.3` and version code
`1 * 1000000 + 2 * 1000 + 3` = `1002003`. That keeps the version code monotonic
with the version and reproducible from source, so rebuilding a tag yields the
same value. `github.run_number` would not: it is scoped to a single workflow
file and restarts at 1 if that file is renamed or recreated, which would
republish version code 1. The encoding caps major at 2099 and minor and patch
at 999, so every tag the workflow accepts is inside the range the validators
below enforce; a tag outside those limits fails the build with an explicit
error.

On the release path both are **required**: `scripts/release-build` exits if
either is unset, then rejects a version name that is not dotted numeric and a
version code outside 1 to 2100000000. Gradle applies the same bounds, so a bad
value fails whichever entry point is used. The fallback to the values in
`defaultConfig` applies only to ordinary local builds, where no version has
been supplied at all.

The validation exists because a silent fallback to `versionCode` 1 would
publish a release Android treats as a downgrade of every existing install, and
that cannot be undone by republishing under the same version.

Release APKs are signed with APK Signature Scheme **v3 only**: `enableV1Signing`
and `enableV2Signing` are both false. This is correct here, because v1 is
consulted only below API 24 and v2 only below API 28, while minSdk is 36. v3
also permits key rotation later. Verify a build with:

```bash
./scripts/container-run bash -lc \
  '$(find /opt/android-sdk -name apksigner -type f | head -1) verify --print-certs --verbose release/*.apk'
```

and confirm the reported certificate SHA-256 matches the keystore.

## Publish

After checks pass on `main`, create and push an annotated tag:

```bash
git tag -s v1.0.0 -m "Autospeed 1.0.0"
git push origin v1.0.0
```

The tag signature establishes the maintainer's release intent. GitHub's
artifact attestation separately proves which workflow and commit produced each
APK.

Verify a downloaded artifact:

```bash
sha256sum --check SHA256SUMS
gh attestation verify autospeed-framework-v1.0.0.apk \
  --repo OWNER/autospeed
```

## Obtainium

Point Obtainium at the GitHub repository URL and select GitHub Releases as the
source. Choose exactly one APK track:

- privacy-first framework:
  `autospeed-framework-v[0-9.]+\.apk`
- optional Play fallback:
  `autospeed-play-v[0-9.]+\.apk`

The two public location tracks intentionally use the same application ID and
signing key, so switching between them is an in-place update. Public and
personal profiles use different application IDs and cannot update over one
another. Obtainium compares the release tag/version and installs the APK
published by the workflow.

## F-Droid preparation

F-Droid publication is not currently planned. The framework variant keeps the
important prerequisites intact: no proprietary runtime dependency, no network
permission, reproducible source-driven Gradle build, complete license notices,
and no downloaded executable code at runtime. A future F-Droid submission will
still require metadata, reproducibility review, and confirmation that every
build dependency is accepted by the selected repository.
