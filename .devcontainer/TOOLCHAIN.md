# Toolchain lock

The devcontainer is the source of truth for build tooling.

| Component | Version | Integrity |
| --- | --- | --- |
| Debian slim, amd64 | bookworm, 2026-08-24 image | `sha256:5ae3c39ebd15e229dcedd5cee596b2497182493d41ff162e824ba13fc1b2b867` |
| Temurin JDK | 25.0.4.1+1 LTS | `sha256:dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e` |
| Gradle | 9.7.1 | `sha256:acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a` |
| Android command-line tools | 23.0 / 16111833 | `sha256:0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8` |
| Android platform | API 36 (`android-36`) and API 37 (`android-37.0`) | exact SDK package revisions |
| Android build-tools | 36.0.0 and 37.0.0 | exact SDK package revisions |
| typos | 1.50.1 | `sha256:edf0545109aee6a22751d04ddecb97c45be47d3aa0409564fb895eeeace91b1e` |
| Node.js bootstrap | 22.23.2 | architecture-specific SHA-256 in `scripts/devcontainer` |
| Dev Container CLI | 0.87.0 | SHA-512 in `scripts/devcontainer` |

The Android SDK manager verifies repository-provided archive hashes when
installing the exact package revisions listed above. Android SDK platforms are
published with a minor version component, so API 37 is installed as the
`platforms;android-37.0` package and selected in Gradle with
`compileSdk = 37` plus `compileSdkMinor = 0`. Gradle dependencies have a
separate lock and checksum allowlist under `gradle/`.

Changing any version requires updating its immutable hash in the same change
and rebuilding the container from scratch.
