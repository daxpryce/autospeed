# Trusted releases

GitHub Actions is the only supported release builder. Local release builds are
useful for testing but are not publication candidates.

## Trust boundary

The release workflow:

1. accepts only an annotated `vMAJOR.MINOR.PATCH` tag;
2. checks out the exact tagged commit;
3. builds with the repository's pinned Dev Container CLI and devcontainer;
4. verifies Gradle dependencies and runs every quality gate;
5. signs public framework and Play-compatible APKs with the protected release
   key;
6. rejects an APK that declares `android.permission.INTERNET`;
7. emits SHA-256 checksums;
8. creates GitHub artifact attestations through GitHub OIDC;
9. publishes the APKs and checksums to the matching GitHub release.

The workflow references third-party and GitHub actions by full commit SHA.
Configure the GitHub `release` environment with required reviewers and restrict
deployment to protected tags.

## Repository secrets

Create an Android signing key offline and retain an encrypted backup. Configure
these secrets on the protected `release` environment:

| Secret | Value |
| --- | --- |
| `AUTOSPEED_SIGNING_KEY_BASE64` | Base64 encoding of the JKS file |
| `AUTOSPEED_SIGNING_STORE_PASSWORD` | Keystore password |
| `AUTOSPEED_SIGNING_KEY_ALIAS` | Signing-key alias |
| `AUTOSPEED_SIGNING_KEY_PASSWORD` | Signing-key password |

The signing key is materialized only for the build job and removed in an
`always()` cleanup step. Losing this key prevents installed copies and
Obtainium from accepting future updates.

Generate the key from the pinned container rather than a host JDK:

```bash
./scripts/container-run keytool -genkeypair \
  -keystore autospeed-release.jks \
  -storetype JKS \
  -alias autospeed \
  -keyalg EC \
  -groupname secp256r1 \
  -validity 10000
base64 -w0 autospeed-release.jks
```

Move the keystore to encrypted offline storage after configuring the GitHub
secret. Do not commit it or leave it in the repository directory.

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
