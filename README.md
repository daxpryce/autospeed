# Autospeed

Autospeed is a small, purpose-built Android driving interface for vehicles whose
instrument cluster shows kilometers per hour. It displays location-derived
speed in miles per hour and kilometers per hour, controls the current media
session, and opens one configured map app or media app with a compact speed
overlay.

It is not a navigation app, media player, trip recorder, general app launcher,
or general-purpose Android Auto replacement.

## Features

- simultaneous mph and km/h display with configurable primary unit;
- play/pause, previous-track, and next-track controls;
- a selected map app with a speed-and-media overlay;
- a selected media app with a speed-only overlay;
- tap the overlay speed display to return to Autospeed;
- optional suppression of notification interruptions while Autospeed is active;
- automatic high-contrast day/night appearance using the phone's ambient-light
  sensor, with manual day and night modes;
- system-first location with direct GNSS fallback;
- optional Google Play fused-location fallback in a separate build variant.

Autospeed exposes no browser, messaging, calling, notification-browsing,
assistant, or arbitrary application-launching interface.

## Privacy

Autospeed has no account, advertising, analytics, crash-reporting service, or
`INTERNET` permission. It does not persist location, speed, trips, routes,
notification contents, or media metadata.

The framework APK has no Google Play services dependency. The Play-compatible
APK still tries Android's system fused provider first and uses Google Play
location only when enabled and the system provider fails the configured quality
budget. On GrapheneOS, Play location requests are normally rerouted to the OS
implementation; other operating systems and configurations may allow Google
Play services to process location.

## Build and install

The supported toolchain is the repository's pinned devcontainer. The host needs
only Docker, `curl`, and Git:

```bash
./scripts/container-up
./scripts/container-run ./scripts/check
./scripts/container-run ./gradlew assemblePublicFrameworkDebug
```

See [`DEVELOPING.md`](DEVELOPING.md) for build variants, the devcontainer
workflow, and ADB installation. See [`docs/releases.md`](docs/releases.md) for signed GitHub
releases, artifact attestations, and Obtainium setup.

## Releases

Release APKs are built only by the tagged GitHub Actions workflow using the
same pinned devcontainer. Each release includes framework and Play-compatible
APKs, SHA-256 checksums, and GitHub build-provenance attestations.

F-Droid publication is not currently planned. The framework variant avoids
proprietary runtime dependencies so a future submission remains possible.

## Safety

GPS-derived speed can be delayed, inaccurate, or unavailable. Autospeed is not
a calibrated vehicle instrument and does not replace the vehicle speedometer.

Public builds may show a brief non-blocking driving advisory. Personal builds
contain no app-authored motion warning or lockout and require an explicit
build-time acknowledgment to produce.

## Contributing and forks

Forking is welcome. Upstream changes are expected to be limited to narrowly
scoped bug fixes and security fixes; feature pull requests are unlikely to be
accepted.

## License

Autospeed is released under the [MIT License](LICENSE). Third-party asset
attributions are recorded in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Autospeed was written by Dax Pryce together with a large language model for his
own personal use. It is provided as-is, with no warranty; anyone else who runs
it assumes all liability and responsibility for doing so.
