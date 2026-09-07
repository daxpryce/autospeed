# Developing Autospeed

Autospeed builds **only** inside its devcontainer. Nothing in this repository
expects a host JDK, Gradle, Android SDK, Node.js, or global npm package, and no
build step reaches outside the pinned toolchain.

The host needs exactly three things:

- Docker Engine with BuildKit (Podman works if it provides a Docker-compatible
  socket);
- `curl`;
- Git.

`scripts/container-up` downloads Node.js and the Dev Container CLI into the
git-ignored `.tools/` directory. Both archives are pinned by version and
cryptographic hash.

## Repository layout

| Path | Contents |
| --- | --- |
| `app/` | The Android application: activities, overlay service, UI adapters, resources. |
| `core/` | Pure-Kotlin domain logic with no Android dependency. This is the part held to 100% coverage. |
| `config/` | detekt and ktlint configuration. |
| `docs/` | `autospeed-design.md` (authoritative requirements), `releases.md`. |
| `scripts/` | Every supported entry point. See below. |
| `.devcontainer/` | Image definition and `TOOLCHAIN.md`, which records why each version is pinned. |

`docs/autospeed-design.md` is the source of truth for behaviour. When an
implementation has to deviate from it, amend the document in the same change
rather than letting the code and the design drift apart.

## Scripts

Every script is meant to be run through the container. `scripts/container-run`
starts the container if needed and executes a command inside it.

| Script | Purpose |
| --- | --- |
| `scripts/container-up` | Build/start the devcontainer. |
| `scripts/container-run CMD` | Run `CMD` inside the container. |
| `scripts/devcontainer` | Raw Dev Container CLI, for flags `container-run` does not expose. |
| `scripts/check` | The full quality gate. Run this before every commit. |
| `scripts/build-apk [VARIANT]` | Assemble a variant and copy the APK to `dist/`. |
| `scripts/install-apk PATH` | Install over ADB, refusing to guess between devices. |
| `scripts/release-build` | Signed release artifacts into `release/`. |
| `scripts/update-dependency-locks` | Regenerate dependency locks and checksums. |

Typical loop:

```bash
./scripts/container-run ./scripts/check
./scripts/container-run ./scripts/build-apk
./scripts/container-run ./scripts/install-apk dist/autospeed-personalFramework-debug.apk
```

## Build variants

Two independent flavor dimensions:

| Dimension | Variants |
| --- | --- |
| Advisory | `public`, `personal` |
| Location | `framework`, `play` |

Framework variants contain no Google Play services dependency. Play variants
contain the optional location client but use it only under the privacy-first
fallback policy in design section 2.2.1.

```bash
./scripts/container-run ./gradlew assemblePublicFrameworkDebug
```

### The personal-use acknowledgment

A personal build requires the exact acknowledgment string (design section 2.1).
Pass it for a one-off build:

```bash
./scripts/devcontainer exec \
  --workspace-folder "$PWD" \
  --remote-env "AUTOSPEED_PERSONAL_USE_ACK=Do not configure or interact with Autospeed while driving. Obey applicable laws and remain attentive." \
  ./gradlew assemblePersonalFrameworkDebug
```

For repeated builds, `devcontainer.json` forwards the host's
`AUTOSPEED_PERSONAL_USE_ACK` into the container via
`remoteEnv: "${localEnv:AUTOSPEED_PERSONAL_USE_ACK}"`, so any host mechanism
that exports the variable works with the ordinary entry point:

```bash
set -a; source .env; set +a
./scripts/container-run ./scripts/build-apk
```

The Dev Container CLI has no dotenv support of its own; a `.env` file is read
only because the host shell sourced it first. Copy `.env.example` to `.env` to
get started.

`.env` is git-ignored on purpose. The acknowledgment is not a secret, but
committing it to `containerEnv`, a tracked `.env`, or any other tracked file
would make every build carry it and would defeat the deliberate-selection gate
design section 2.1 requires. It must be absent by default so a personal
artifact cannot be produced accidentally.

## Build output location

The repository is bind-mounted into the container, so everything Gradle writes
is directly visible on the host. No Docker volume has to be opened to retrieve a
build. Only the Gradle cache (`~/.gradle`) and the adb keys (`~/.android`) live
in named volumes.

Artifacts land in the **`app`** subproject, not the repository root. The root
`build/` directory holds only reports, which makes it look as though nothing was
produced:

| Path | Contents |
| --- | --- |
| `build/` | Gradle reports only |
| `app/build/outputs/apk/<flavor>/<buildType>/` | the actual APKs |

`scripts/build-apk` avoids that hunt by copying the APK to `dist/` under a
stable name:

```bash
./scripts/container-run ./scripts/build-apk                       # personalFrameworkDebug
./scripts/container-run ./scripts/build-apk publicFrameworkDebug
```

This produces `dist/autospeed-<flavor>-<buildType>.apk`. `dist/` and `release/`
are both git-ignored.

## Installing on a device

Wireless debugging avoids passing a USB device through the container.

1. On the phone, enable **Developer options > Wireless debugging**.
2. Choose **Pair device with pairing code** and note its IP, pairing port, and
   code.
3. Pair and connect:

   ```bash
   ./scripts/container-run adb pair PHONE_IP:PAIRING_PORT
   ./scripts/container-run adb connect PHONE_IP:DEBUG_PORT
   ```

4. Install:

   ```bash
   ./scripts/container-run ./scripts/build-apk
   ./scripts/container-run ./scripts/install-apk dist/autospeed-personalFramework-debug.apk
   ```

`adb` keys persist in a named Docker volume, so pairing survives container
rebuilds. `scripts/install-apk` refuses to continue unless exactly one
authorized device is connected.

### Pairing gotchas

- **A VPN on the phone breaks pairing.** Wireless debugging advertises whatever
  address the phone considers local, so with WireGuard (or similar) up it
  advertises the tunnel address, which the container cannot reach. The symptom
  is `error: protocol fault (couldn't read status message): Success`. Disable
  the VPN, pair, then re-enable it.
- **The pairing port is not the connect port.** Both are shown on the phone and
  they differ; the pairing port changes every time the dialog is opened.
- Each devcontainer instance runs its own adb server. If two containers exist
  for this folder, a device paired in one is not visible in the other.

For USB installation, install platform-tools on the host, authorize the phone,
and use `-r` rather than the long form:

```bash
adb install -r dist/autospeed-personalFramework-debug.apk
```

`pm` rejects `--replace` on current Android with
`IllegalArgumentException: Unknown option --replace`.

### Useful device diagnostics

```bash
adb exec-out screencap -p > shot.png                     # verify UI changes for real
adb shell dumpsys power | grep -A3 -i "Wake Locks"       # confirm keep-screen-on is held
adb shell dumpsys window | grep mDreamingLockscreen      # is the phone locked?
adb shell wm size                                        # screen coordinates for `input tap`
```

`adb shell input tap X Y` takes **device** pixels. A screenshot viewed at a
different scale will give the wrong coordinates.

## Quality gates

```bash
./scripts/container-run ./scripts/check
```

runs, in order:

- ktlint formatting enforcement;
- detekt static analysis;
- Android lint with warnings treated as errors;
- all unit tests;
- Kover's 100% line and branch coverage requirement for `core`;
- `typos` across source and documentation.

Android framework adapters are kept thin and are covered by Android tests where
platform behaviour cannot be represented honestly in JVM unit tests.

Things that trip the gate regularly:

- **Formatting.** Run `./scripts/container-run ./gradlew ktlintFormat -q` after
  editing Kotlin; it fixes import ordering and most spacing automatically.
- **Unused resources are errors.** Do not add a string, colour, or dimension
  before something references it.
- **detekt caps line length and parameter counts.** Wrap long constructor calls
  rather than raising the limits.
- **XML comments cannot contain `--`.**

## Dependencies

Application dependencies are exact-versioned and dependency-locked
(`settings-gradle.lockfile`, `core/gradle.lockfile`). When intentionally
changing a version, regenerate inside the container and review every diff:

```bash
./scripts/container-run ./scripts/update-dependency-locks
```

This also writes `gradle/verification-metadata.xml` if dependency verification
is enabled. Never add a checksum simply because verification failed during an
otherwise unrelated build; establish first why the artifact changed.

## Application identifiers

| Variant | Application ID |
| --- | --- |
| Namespace | `io.pryce.android.autospeed` |
| Personal debug | `io.pryce.android.autospeed.personal.debug` |

Debug builds carry a suffix, so a debug and a release build can coexist on one
device. Use the full suffixed ID with `adb shell am start`, `pm uninstall`, and
`dumpsys`.

Note that `SettingsActivity` is not exported: launch `MainActivity` and open
settings from the gear control rather than starting it directly.
