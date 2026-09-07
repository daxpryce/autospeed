# Autospeed Design

## 1. Purpose

Autospeed is a small Android application for a driver whose vehicle displays
speed in kilometers per hour. It presents the device's current location-derived
speed in both miles per hour and kilometers per hour, provides basic controls
for an already-running media session, and can open a user-selected mapping or
media application while keeping an appropriate Autospeed overlay visible.

Autospeed is not a navigation application, a media player, a trip recorder, or
a vehicle telemetry system. It is a highly specific, minimal replacement for
the parts of Android Auto needed by this workflow, not a general-purpose
infotainment platform. Features should be added only when they directly support
the speed display, current-media controls, or deliberate handoff to a selected
companion application. Its design priorities, in order, are:

1. No first-party tracking and minimal exposure to third parties.
2. Fast startup and a quickly visible speed display.
3. A readable, low-distraction interface.
4. Reliable operation on Android, particularly GrapheneOS.
5. A small implementation and dependency footprint.

## 2. Supported Environment

- Android on a physical phone, with GrapheneOS as the primary target.
- Distribution through F-Droid-compatible builds and/or Obtainium.
- Full speedometer functionality without Google Play services.
- Optional use of Google Play services location APIs when included in the build
  and available on the device.
- No `INTERNET` permission in any build; location providers may perform their
  own network-assisted work outside the Autospeed process.
- No account, cloud service, advertising SDK, analytics SDK, or crash-reporting
  service.

The initial supported devices are the Pixel 10 and Pixel 10a running
GrapheneOS. The initial build uses API 36 as its minimum, targets API 37, and
compiles against API 37. Autospeed does not carry compatibility code for older
Android releases unless the supported-device list changes.

### 2.1 Build profiles and driving advisory

Autospeed has separate **public** and **personal** build profiles. The public
profile is the normal distribution build and may show this brief, non-blocking
advisory during initial setup:

> Do not configure or interact with Autospeed while driving. Obey applicable
> laws and remain attentive.

The advisory must not be shown repeatedly, require an acknowledgment before the
speed display appears, or block access to controls or settings. It is a
distribution and liability-policy choice, not a representation that federal or
Washington State law requires an application-authored warning. As of September
2026, the identified authorities regulate driver conduct or provide proposed
voluntary guidance; they do not require a standalone phone application to show
a warning or lock its interface:

- [RCW 46.61.672](https://app.leg.wa.gov/RCW/default.aspx?cite=46.61.672)
  regulates a driver's use of personal electronic devices.
- [81 FR 87656](https://www.federalregister.gov/documents/2016/12/05/2016-29051/visual-manual-nhtsa-driver-distraction-guidelines-for-portable-and-aftermarket-devices)
  is a notice of proposed NHTSA guidelines for portable and aftermarket
  devices, not a binding safety standard.

The personal profile removes all Autospeed-authored driving-warning UI. It must
not detect vehicle motion, infer whether the user is driving, disable features
based on speed, or restrict access to settings or controls. Android permission
and special-access screens remain unaffected because they are provided or
required by the operating system.

Producing the personal profile must be an explicit build-time act. Its release
task must fail unless the build environment contains:

```text
AUTOSPEED_PERSONAL_USE_ACK=Do not configure or interact with Autospeed while driving. Obey applicable laws and remain attentive.
```

The value must match the public advisory exactly; merely defining the variable
is insufficient. The build must not silently fall back to either profile when
the variable is absent or incorrect. This gate records deliberate selection of
the profile without adding runtime friction. It is not a secret and must not be
packaged as user data or treated as authentication. Profile selection is
compile-time only; a public artifact must not acquire personal behavior through
a runtime setting. Public and personal artifacts should have distinct
application IDs and visible build labels so they cannot be confused and can be
installed side by side.

### 2.2 Location backend build variants

The driving-advisory profile and location backend are independent build
dimensions. Autospeed must provide:

- a **framework** variant with no Google Play services dependency, using
  Android's `LocationManager` and the standard fused provider when available;
- a **Play-compatible** variant that includes the Google Play services location
  client as an optional fallback when the preferred framework sources cannot
  supply timely, trustworthy speed.

The Play-compatible variant must retain the complete framework implementation
and must never select Play location merely because it is installed. Installing
Google Play services must never be a prerequisite for displaying speed. The
framework variant is the default for F-Droid-compatible distribution; other
channels may publish either or both variants subject to their dependency
policies.

Neither Android's framework
[`LocationManager`](https://developer.android.com/reference/android/location/LocationManager)
nor Google Play services
[`FusedLocationProviderClient`](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)
requires the client application to hold `INTERNET`; they require the
appropriate location permission. Autospeed must therefore omit `INTERNET` from
every variant. The selected OS or Play location provider may independently use
network-assisted location under the user's system configuration, but that does
not grant Autospeed network access.

On GrapheneOS, sandboxed Google Play is optional and has no special privilege.
GrapheneOS normally reroutes Play geolocation requests to its implementation on
top of the standard OS geolocation service. Users can opt into GrapheneOS
Network Location or explicitly disable rerouting and configure Google's
service. Autospeed must respect that system-level choice rather than attempting
to detect, override, or reproduce it. Autospeed also must not attempt to
distinguish sandboxed Google Play on GrapheneOS from integrated Google Play on a
stock device; the same API is exposed in both cases, while the operating system
and user configuration determine its privileges and routing. See the GrapheneOS
[Network location](https://grapheneos.org/usage#network-location) and
[sandboxed Google Play](https://grapheneos.org/usage#sandboxed-google-play)
documentation.

#### 2.2.1 Privacy-first provider ladder

Location selection is based on both privacy preference and measured result
quality. The Play-compatible variant uses this order:

1. **System fused location:** Request high-quality updates through Android's
   standard `LocationManager.FUSED_PROVIDER`. On GrapheneOS this allows its OS
   geolocation implementation, including optional GrapheneOS Network Location,
   to assist acquisition without deliberately routing the request to Google.
2. **Play fused fallback:** If the system source fails the startup or
   steady-state quality budget, and the user has allowed Play fallback, request
   `FusedLocationProviderClient`. On GrapheneOS this is normally rerouted to OS
   APIs; on systems or configurations where it is not rerouted, Google Play
   services may process location. When GrapheneOS rerouting is enabled, this
   step may be functionally equivalent to step 1 and must not be represented as
   guaranteed to improve speed quality.
3. **Direct GNSS fallback:** Request `LocationManager.GPS_PROVIDER` directly
   when neither fused path supplies an acceptable speed. This path is private
   from Autospeed's perspective and universally independent of Google Play, but
   may take longer to acquire and degrade sharply with obstructed sky view.

This is a service-quality fallback order, not a claim that direct GNSS is less
private than Play services. Direct GNSS may be warmed concurrently while a
fused source is active so the final fallback is ready quickly. The location
orchestrator may maintain concurrent provider subscriptions internally, but
only one accepted fix stream feeds the speed policy and display.

The framework variant follows the same ladder with step 2 omitted. The
Play-compatible variant must expose an **Allow Play location fallback** setting.
It defaults to enabled in the personal profile and disabled in the public
profile. When disabled, behavior is identical to the framework ladder even
though the Play client is present in the artifact. Enabling it must plainly
state that GrapheneOS normally reroutes the API but other operating systems or
user configurations may allow Google Play services to process location.

Escalation and recovery must be automatic and hysteretic:

- do not escalate solely because a preferred source has not produced an
  immediate coarse position; require failure to meet the defined time,
  freshness, `hasSpeed()`, and speed-accuracy budgets;
- prefer a direct, trustworthy GNSS speed over a fast network-derived position
  that lacks speed or has unacceptable speed accuracy;
- return from Play fallback to acceptable system fused results after a stable
  recovery interval, without rapidly switching providers;
- never blend two provider speeds without a separately designed and tested
  fusion algorithm;
- show an unavailable or stale state rather than accepting a lower-quality fix
  merely to keep a number on screen.

## 3. Functional Requirements

### 3.1 Speed reporting

- Obtain updates through the location backend selected by the build variant.
- Keep provider-specific behavior behind a common location-source interface so
  freshness, accuracy, lifecycle, and display behavior remain identical.
- Apply the privacy-first provider ladder in section 2.2.1; never ask the user
  to install Google Play services.
- Show speed in both miles per hour (mph) and kilometers per hour (km/h).
- Let the user choose which unit is primary.
- Make the primary value substantially larger and more prominent.
- Show the secondary value by default, with a setting to hide it.
- Clearly distinguish these states:
  - waiting for a first location fix;
  - current speed available;
  - location fix is stale;
  - speed is unavailable or unreliable;
  - location permission or device location is disabled.
- Never imply that a stale or absent fix represents a current speed of zero.
- Stop location updates when Autospeed is no longer displaying either its main
  screen or its overlay.

Android `Location.getSpeed()` reports meters per second. Convert that single
source value for display:

```text
km/h = m/s * 3.6
mph  = m/s * 2.2369362921
```

The displayed values should be rounded to whole units by default. Conversion
must occur from the original meters-per-second value rather than converting one
display unit into the other.

Location fixes should be rejected for display when they are older than a
defined threshold or do not contain a speed. An accuracy policy is also needed
to limit obviously poor fixes. This policy must avoid aggressive smoothing that
causes a visibly delayed speed reading. The initial implementation should use a
small, documented freshness threshold and Android's reported speed accuracy
when available, then tune it using real driving tests.

Provider-reported `Location.getSpeed()` is the initial and authoritative speed
source. Autospeed must not derive speed by differencing coordinates when the
provider supplies speed. It must use `hasSpeedAccuracy()` and
`getSpeedAccuracyMetersPerSecond()` where available when deciding whether and
how to display a value.

Accelerometer or gyroscope integration is not part of the initial fused
location implementation. Inferring vehicle speed from inertial sensors requires
correcting for phone orientation, gravity, mount vibration, sensor bias, and
integration drift. A later experimental display filter may use inertial data
only if it:

- remains anchored to provider-reported speed rather than producing an
  independent speed history;
- is optional and can be disabled without changing location acquisition;
- exposes no falsely precise value during a stale or missing location fix;
- demonstrates lower display latency without materially increasing error in
  repeatable road tests.

### 3.2 Main display

The main screen contains:

- primary speed and unit;
- optional secondary speed and unit;
- location status when no trustworthy speed is available;
- previous track, play/pause, and next track controls;
- a button in a lower corner that opens the selected mapping application and
  starts overlay mode;
- a button that opens the selected media application with a speed-only overlay;
- access to settings.

The speed must remain legible in landscape and portrait layouts. Controls need
large touch targets and sufficient spacing for use in a mounted vehicle. The
screen should respect display cutouts and system bars and retain usable contrast
with every background option.

The application should minimize the number and duration of interactions needed
to reach any function. Settings and permission setup must be direct and
predictable rather than adding menus, repeated prompts, or motion-dependent
behavior. The public profile may present the one-time advisory defined in
section 2.1; the personal profile must not present it or any equivalent warning.

### 3.3 Orientation

The orientation setting has three values:

- **Landscape** (default): request a sensor-aware landscape orientation so the
  device can use either landscape direction.
- **Portrait**: request a sensor-aware portrait orientation so the device can
  use either portrait direction.
- **Auto-select**: do not impose an orientation; follow the device's current
  orientation and rotation behavior.

Changing the setting should take effect immediately and persist locally.

### 3.4 Media controls

Autospeed does not select, browse, stream, or store media. It controls an active
media session owned by another installed application.

The supported operations are:

- play or pause, with the button state following the active session where
  possible;
- skip to next track;
- skip to previous track.

The preferred implementation is Android's media-session APIs using a
notification-listener service to obtain authorized active media sessions and
their `MediaController` transport controls. Notification access is a special
system grant that the user must enable in Settings; it is not an ordinary
runtime permission.

If several controllable sessions are active, Autospeed needs a deterministic
selection rule. The initial rule should prefer a playing session, then the most
recent controllable session. The UI must disable unavailable actions rather
than pretending a command succeeded. Autospeed must not inspect, persist, or
display notification contents, track names, artwork, playlists, or listening
history.

`AudioManager.dispatchMediaKeyEvent()` may be evaluated as a compatibility
fallback, but it is not the primary design. Global key dispatch cannot provide
the same deterministic session selection, playback state, or supported-action
information. It must not replace the media-session path merely to avoid the
notification-access grant. If retained after device testing, it must be
explicitly labeled as best effort, send complete key down/up pairs, and never
report a command as successful without observable session state.

The selected media application is also available as a direct launch target from
the main screen. This is separate from media-session selection: choosing an
application to open must not cause Autospeed to send controls to an inactive
session belonging to that application.

### 3.5 Companion applications

Settings allow the user to choose compatible installed mapping and media
applications independently or leave either choice empty. Selection should use
Android intent resolution and display application labels rather than
maintaining hard-coded package lists. The design should avoid broad
installed-package visibility.

While Autospeed is active, its application-level navigation is limited to three
destinations: Autospeed itself, the selected mapping application, and the
selected media application. Autospeed must not provide an app drawer, arbitrary
application shortcuts, web links, messaging, calling, notification browsing,
voice-assistant entry points, or generic "open with" actions. System settings
screens required to grant permissions or special access are the only exception.

Every companion launch must use an explicit package-scoped intent for the
configured application. Autospeed must not fall back to an implicit intent,
browser, launcher, or chooser if that application cannot be opened. It must
instead keep or return Autospeed to the foreground and show a clear local error.

This boundary governs what Autospeed exposes and initiates; it is not a device
kiosk. Autospeed must not request usage access, accessibility privileges,
device-owner status, or another invasive capability to detect or prevent the
user from manually opening an unrelated application through Android itself.

When the map button is pressed:

1. Verify that a mapping application is selected and still installed.
2. Request overlay access if it has not already been granted.
3. Start the map-companion overlay.
4. Launch the selected application using an explicit package-scoped intent.
5. If launch fails, stop the overlay and present a clear error.

Autospeed sends no destination, route, location, query, or other user data to
the mapping application. It receives no data from that application.

When the media-application button is pressed:

1. Verify that a media application is selected and still installed.
2. Request overlay access if it has not already been granted.
3. Start the speed-only media-companion overlay.
4. Launch the selected application using an explicit package-scoped intent.
5. If launch fails, stop the overlay and present a clear error.

Autospeed sends no media command, search, track identifier, or other user data
as part of launching the media application. Media controls on Autospeed's main
screen continue to operate through the separately selected active media
session.

If no mapping application is selected, the map button should direct the user to
the relevant setting. The media-application button behaves the same way when no
media application is selected. Neither button should open an implicit chooser
at launch time.

### 3.6 Overlay mode

The requested behavior is a system overlay, not Android picture-in-picture.
Picture-in-picture contains an Autospeed activity and cannot remain as an
independent control surface in the requested way after Autospeed launches and
cedes the foreground to another application.

Overlay mode requires the user to grant Android's special "display over other
apps" access. It is implemented as a small window owned by a foreground
service, because location and a user-visible overlay must continue while the
companion application is in the foreground.

The map-companion overlay contains:

- primary speed and unit;
- previous track;
- play/pause;
- next track;
- an obvious close/return control.

The media-companion overlay contains only the primary speed and unit. The whole
speedometer surface is a return target; tapping anywhere on it brings
Autospeed's main activity to the foreground. The speed region of the
map-companion overlay must provide the same tap-to-return behavior without
interfering with its media buttons.

The overlay must:

- never cover the entire screen;
- stay within roughly one inch square, opening in the top-right corner, so it
  obscures as little of the companion application as possible. At that size the
  readout is a speed-limit-sign style panel showing only the unit and the
  primary value, and the map-companion transport controls are smaller than the
  48dp target used elsewhere. This is a deliberate trade: an overlay large
  enough for full-size targets covers a meaningful part of the map, and the
  controls remain reachable because the overlay is draggable;
- be movable between suitable screen edges or corners;
- remember only its local position;
- use large touch targets without consuming taps outside its bounds;
- remain readable over varying map colors;
- avoid system gesture areas and display cutouts;
- stop immediately when dismissed;
- show an ongoing notification while its foreground service is active;
- restore the device to a no-background-work state when stopped.

When the main Autospeed activity returns to the foreground, it must remove the
overlay and transfer location ownership back to the activity without creating a
second location listener.

Android and GrapheneOS may constrain background activity launches, foreground
services, and overlays. Overlay mode therefore needs device-level testing on
every supported Android release. If a release prevents the exact launch
sequence, Autospeed should ask the user to open the selected companion
application after starting the overlay rather than adding fragile automation or
accessibility privileges.

### 3.7 Background

The main display supports:

- system wallpaper;
- a solid color selected locally;
- an image selected from device storage.

Appearance has **Automatic**, **Day**, and **Night** modes. Automatic mode uses
Android's ambient-light sensor (`TYPE_LIGHT`) when present. It must use
separate enter-night and enter-day lux thresholds plus a dwell interval so
passing shadows and headlights do not make the interface flicker between
palettes. When the sensor is absent, unavailable, or has not produced a usable
sample, Automatic follows the system night-mode state.

The night palette must substantially reduce emitted light while retaining
high contrast and legibility; it must not merely invert colors. Day mode should
remain readable in direct daylight. Palette transitions must not animate in a
way that delays the speed display or attracts attention.

Use Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT`) for image
selection and retain only the returned URI permission. Do not request broad
photo or storage access. Persist the URI and render the image without copying it
into application-owned storage unless a later, explicitly approved requirement
calls for a private local copy.

Controls and speed text must have a fixed high-contrast surface, scrim, or
outline so wallpaper and image choices cannot make critical information
unreadable.

The resolved palette applies to every Autospeed surface, including dialogs. A
dialog is a separate window whose decor is inflated against the theme it is
constructed with, so it cannot be recoloured after the fact the way an
activity's view tree can; dialogs must therefore be built with a day or night
dialog theme chosen from the current resolution. Leaving a dialog on the device
theme would let a bright dialog appear over a night-palette screen at night.

### 3.8 Settings

Only settings may be persisted:

- orientation mode;
- primary speed unit;
- whether secondary speed is shown;
- selected mapping application identifier;
- selected media application identifier;
- whether the Play-compatible build may use Google Play location as a fallback;
- whether notification interruptions are suppressed while Autospeed is active;
- appearance mode;
- background mode;
- selected color;
- selected image document URI;
- overlay position and any approved overlay appearance settings;
- completion state for local onboarding or permission explanations, including
  whether the public-profile advisory has been shown.

Use Android DataStore for primitive preferences unless profiling demonstrates a
meaningful startup regression. No location fix, speed sample, trip, route,
media metadata, usage event, diagnostic event, or permission history may be
persisted.

The notification-suppression setting defaults to enabled in the personal
profile and disabled in the public profile. It remains a normal local setting
that the user can change in either profile. Enabling the preference must not
pretend to work until Android's Notification Policy Access has been granted;
denial leaves the rest of Autospeed fully functional.

The Play-location fallback setting exists only in the Play-compatible variant.
It defaults to enabled in the personal profile and disabled in the public
profile. Changing it takes effect immediately: disabling it stops the Play
request and reevaluates the available framework sources; enabling it permits
but does not force escalation under the quality policy in section 2.2.1.

There is no backup requirement. To prevent settings and selected identifiers
from leaving the device through Android backup, application backup should be
disabled unless a later decision explicitly permits encrypted operating-system
backup.

#### 3.8.1 About and license

Settings ends with an About section carrying two things:

- A short static note that Autospeed is released under the MIT License, with a
  pointer to the repository for the binding text. The license text is not
  reproduced or expanded in the application; `LICENSE` at the repository root
  is the single copy.
- An About action that opens a modal stating that Autospeed was written by Dax
  Pryce together with a large language model for his own personal use, that it
  carries no warranty, that anyone else who runs it assumes all liability and
  responsibility, and that the vehicle's own instrument cluster takes
  precedence over Autospeed's readout.

Neither is shown at startup. The instrument-cluster precedence statement is a
correctness statement about a satellite-derived estimate rather than a driving
advisory, so it does not go through the section 2.1 advisory flow, and it
appears in both build profiles.

## 4. Privacy and Security Requirements

### 4.1 Data handling

Autospeed performs speed conversion, fix-quality evaluation, state management,
and persistence locally. The Autospeed process makes no network requests, and
every Android manifest must omit `INTERNET`. Except for the explicitly selected
Google Play services location client in the Play-compatible variant,
third-party dependencies must not introduce network access, telemetry, remote
configuration, or dynamic code loading. The Play-compatible integration may
communicate with Google Play services only to request and receive location; it
must not add Google analytics, diagnostics, remote configuration, or unrelated
Play services APIs.

Location fixes may originate from an operating-system or Play-services process
whose implementation can use network assistance according to the device's
system configuration. Autospeed neither supplies that service with network
access nor controls its independent data handling. Release documentation must
identify the included location backend and explain this boundary without
claiming that all location-provider computation is offline.

The privacy guarantee is strongest for the framework build on GrapheneOS with
its OS-provided location services. In the Play-compatible build, allowing Play
fallback is an explicit tradeoff: Autospeed first tries the system provider,
but Google Play services may receive or process location if fallback activates
and the operating system does not reroute that API. Autospeed must not describe
Play fallback as private merely because sandboxed Play is possible on
GrapheneOS.

After Autospeed receives a location fix, it is processed in memory only. It is
never:

- written to disk or logs;
- transmitted by Autospeed to another process or application;
- attached to a map intent;
- placed on the clipboard;
- included in an error report;
- retained after the active display or overlay no longer needs it.

Log output in release builds must not contain location, speed history, selected
application identifiers, image URIs, notification data, or media-session data.

### 4.2 Permissions and special access

Autospeed is operated while driving, so a permission dialog that appears the
first time a feature is touched arrives at the worst possible moment. First
launch therefore opens a setup screen that offers every permission and special
access below at once, each with a plain explanation of what it is for, before
the vehicle is moving. The screen is reachable again from settings.

Granting anything there is optional and the screen can be dismissed with items
still ungranted. Only precise location is required for Autospeed to work as a
speedometer. Because access can also be skipped there or revoked later, each
feature must additionally keep a just-in-time request path and must degrade to
an explanation and a route to the correct system screen rather than failing
silently:

| Access | Purpose | Required when |
| --- | --- | --- |
| Precise foreground location | Calculate current speed | Showing live speed |
| Location foreground-service permission/type | Continue speed display in overlay mode | Overlay is active |
| Notifications | Show the required foreground-service notification on Android versions that request it | Starting overlay mode |
| Display over other apps | Render the Autospeed surface over a selected companion application | Starting overlay mode |
| Notification access | Control another application's active media session | Enabling media controls |
| Notification Policy Access | Suppress notification interruptions through an Autospeed-owned Zen rule | Enabling notification suppression |
| Persisted document URI grant | Read a user-selected background image | Selecting an image |

Background location permission should not be requested. Overlay mode is an
explicitly started, continuously visible foreground operation, not passive
background tracking.

The app must remain usable as a speedometer if media, overlay, or Notification
Policy Access is denied. Denial should disable only the related feature and
provide a route to the correct system settings.

### 4.3 Notification interruption policy

When notification suppression is enabled and Notification Policy Access is
granted, Autospeed must activate an app-owned `AutomaticZenRule` whenever its
main activity is visible or one of its overlays is active. The rule should
suppress notification sounds, vibration, heads-up/peek presentation, and
full-screen notification interruptions while leaving notifications posted in
the notification drawer. It should preserve media playback, navigation audio,
and alarms. User-configured system exceptions remain under Android's control.

The rule must remain active while Autospeed hands off to the selected map or
media application because the visible overlay keeps the Autospeed session
active. It must be deactivated when no Autospeed activity or overlay remains
visible. Disabling the preference during an active session must deactivate the
rule immediately.

Autospeed must modify only its own automatic rule. It must not overwrite the
global interruption filter, disable or edit another rule, infer prior DND
state, or use notification-listener access to read, cancel, delay, or repost
notifications. Revoking Notification Policy Access must remove Autospeed's
effective suppression without affecting the rest of the application.

### 4.4 Defensive behavior

- Treat intents, package identifiers, document URIs, and callbacks as
  untrusted input.
- Do not export components unless Android requires it for their purpose.
- Protect any exported service with the appropriate system binding permission.
- Use immutable pending intents unless mutability is required.
- Avoid accessibility-service privileges.
- Avoid reading complete notifications; use only the media-session token access
  required for transport controls.
- Pin screen-on behavior to active UI state. Do not hold wake locks after the
  main display or overlay closes.

## 5. Startup and Runtime Design

### 5.1 Startup target

The first frame should contain the complete display shell and a "waiting for
location" state. Loading settings and acquiring a fix must not block first
render. A numeric speed appears as soon as a trustworthy fix supplies one.

Concrete performance budgets must be measured on the oldest supported
GrapheneOS device:

- time from cold launch to first rendered frame;
- time from launch to registration for location updates;
- time from receiving a valid location callback to updated speed text;
- memory use of the main screen and overlay service.

### 5.2 Implementation shape

Use one Android application module with a small number of explicit components:

- a main activity for speed and controls;
- a settings screen;
- a common location-source interface;
- a framework source wrapping Android `LocationManager`;
- a framework fused source and direct GNSS source;
- an optional Play source wrapping `FusedLocationProviderClient`;
- a location orchestrator that applies the privacy preference, quality budgets,
  fallback order, and recovery hysteresis;
- a pure speed conversion and freshness policy;
- a media-session controller;
- a map application selector and launcher;
- an overlay foreground service and compact overlay view;
- a preferences repository.

Prefer Kotlin and Android SDK APIs. Jetpack libraries are acceptable when they
reduce lifecycle or correctness risk, but each dependency must be weighed
against APK size, initialization cost, reproducibility, and F-Droid
compatibility. The Google Play services location client is permitted only in
the Play-compatible variant and must not leak into the framework variant's
dependency graph or manifest. Avoid a dependency-injection framework, database,
HTTP client, web view, native library, and background job scheduler unless a
later requirement establishes a need.

UI toolkit selection is intentionally unresolved. Views offer a conservative
startup and dependency profile; Compose may simplify state-driven UI but must
meet the measured startup and package-size budgets. The overlay likely needs a
traditional Android `View` even if the activity uses Compose.

### 5.3 Runtime state

Maintain a single in-memory application state derived from:

- current settings;
- current location, its freshness, and the active provider tier;
- ambient-light samples and resolved day/night appearance;
- active media session and supported actions;
- permission/special-access status;
- notification-suppression preference and Autospeed Zen-rule status;
- main-display and overlay lifecycle.

Only one component should own active location requests at a time. Transitioning
from the main activity to overlay mode must transfer ownership without creating
duplicate listeners. Closing overlay mode stops location updates unless the
main display is again visible.

## 6. User Flows

### 6.1 First launch

1. Render the main display immediately.
2. Explain why precise location is needed.
3. Request foreground precise location.
4. Start location updates if granted.
5. On the first launch only, open the access setup screen so media, overlay,
   notification, and Do Not Disturb access can all be granted up front rather
   than interrupting a drive later. Record only that setup was shown, never
   which access was granted or denied.
6. Fall back to a just-in-time request the first time a control is used whose
   access was skipped during setup or revoked afterwards.
7. If notification suppression defaults to enabled but policy access is absent,
   show a non-blocking setup affordance without opening system settings
   automatically.

### 6.2 Enable media controls

1. Explain that notification access is used only to find and control active
   media sessions, not to read or store notifications.
2. Open Android's notification-listener access screen.
3. On return, detect the grant and update control availability.

### 6.3 Enable notification suppression

1. Let the user enable or disable notification suppression in Autospeed
   settings.
2. When enabling it without Notification Policy Access, explain that Android
   will keep notifications in the drawer while suppressing their interruptions.
3. Open Android's Notification Policy Access settings.
4. On return, detect the grant and activate Autospeed's rule only if an
   Autospeed surface is currently visible.
5. Do not repeat the access prompt when the preference is disabled or after the
   user declines.

### 6.4 Start companion application and overlay

For the selected map:

1. If no map is selected, open Autospeed's map setting.
2. Explain and request display-over-other-apps access if needed.
3. Start the map-companion foreground overlay.
4. Launch the selected map.
5. Keep location active only while the visible overlay exists.
6. Return to Autospeed by tapping the speed region, or stop through the overlay
   close action or foreground-service notification.

For the selected media application:

1. If no media application is selected, open Autospeed's media-app setting.
2. Explain and request display-over-other-apps access if needed.
3. Start the speed-only media-companion foreground overlay.
4. Launch the selected media application.
5. Keep location active only while the visible overlay exists.
6. Return to Autospeed by tapping anywhere on the speedometer overlay, or stop
   through the foreground-service notification.

In both cases the overlay is a companion surface, so it must never be visible at
the same time as Autospeed's own main screen. Whenever the main screen becomes
visible the overlay stops, no matter how the user got back: tapping the overlay,
its close action, the notification, the back gesture, recents, or the launcher.
Enumerating only the deliberate return routes is not sufficient, because the
notification's own content intent and every system navigation path also bring
the main screen forward.

### 6.5 Revoked access or removed application

Permissions and special access can be revoked outside Autospeed. The app checks
access before each dependent operation. If a selected map or media application
has been removed, clear that setting and ask the user to select another. If
location is revoked, remove the numeric speed immediately and show a permission
state.

### 6.6 External launch automation

Autospeed must remain launchable through its ordinary launcher activity so
external automation tools such as Tasker can start it in response to
user-defined conditions. Autospeed itself must not monitor wireless charging,
Wi-Fi identity, or departure from a location unless a later requirement
explicitly moves that automation into the app. No custom exported receiver or
background service is needed for the initial Tasker-driven flow.

## 7. Accessibility and Driving Safety

- Meet WCAG AA contrast for text and controls where applicable.
- Do not encode unit or status through color alone.
- Provide content descriptions and sensible focus order.
- Support system font scaling without clipping critical controls.
- Use touch targets of at least 48 dp.
- Avoid animation that delays information or distracts the driver.
- Avoid transient messages as the only presentation of an error.
- Keep setup, chooser, and settings actions short, consistently located, and
  free of avoidable confirmation steps.
- Do not use motion detection, location-derived speed, or an inferred driver
  state to hide or lock controls.
- Do not assume that warnings, interstitials, and lockouts improve safety;
  evaluate interaction cost and time diverted from the primary display.
- The display stays awake while any Autospeed surface is visible -- the speed
  display, settings, access setup, or the companion overlay. A window flag is
  used rather than a wake lock so the system releases it automatically when
  Autospeed stops being visible, and no setting is offered: a speedometer that
  blanks mid-drive is not useful, and the overlay is the visible surface while a
  companion application is in the foreground.

Autospeed is an aid, not a calibrated vehicle instrument. The README and
About screen must state that GPS-derived speed can be delayed, inaccurate, or
unavailable and does not replace the vehicle speedometer. This factual
limitation must not be an interrupting startup dialog.

## 8. Testing and Acceptance

### 8.1 Unit tests

- exact conversion from meters per second to mph and km/h;
- rounding behavior near unit boundaries;
- primary/secondary unit selection;
- missing speed and stale location handling;
- accuracy-policy edge cases;
- media-session selection rule;
- settings serialization and defaults;
- notification-suppression defaults for public and personal profiles;
- Play-fallback defaults for public and personal profiles;
- provider escalation and recovery hysteresis at every quality threshold;
- selection of a trustworthy GNSS speed over a network-derived fix without
  usable speed.

### 8.2 Android tests

- permission denied, denied permanently, granted, and revoked flows;
- first render before settings/location completion;
- orientation changes for all three modes;
- automatic appearance transitions at both lux thresholds and after the dwell
  interval, with no oscillation inside the hysteresis band;
- automatic appearance fallback to system night mode when no usable light
  sensor sample exists;
- overlay start, move, persisted position, dismissal, and service shutdown;
- map-companion overlay includes speed and media controls;
- media-companion overlay contains only the speedometer;
- tapping the speed region of either overlay returns to Autospeed, removes the
  overlay, and transfers location ownership without duplicate listeners;
- transition between main display and overlay without duplicate location
  requests;
- selected map or media application missing or unable to launch;
- companion launch uses only the configured package and never falls through to
  an implicit chooser, browser, launcher, or unrelated application;
- no Autospeed surface exposes arbitrary application launching, messaging,
  calling, notification browsing, or voice-assistant entry points;
- media controls with no session, one session, unsupported actions, and several
  sessions;
- any retained media-key fallback with applications that accept and ignore
  dispatched keys, confirming the UI does not invent success;
- background image selection, persisted URI access, and revoked document access;
- process recreation and device reboot with no unintended service restart;
- public-profile advisory is shown at most once and never blocks the speed
  display, settings, or controls;
- personal-profile UI contains no Autospeed-authored driving advisory or
  motion-based restriction;
- Autospeed's Zen rule activates only when notification suppression is enabled,
  policy access is granted, and a main or overlay surface is visible;
- notifications remain in the drawer while sound, vibration, heads-up, and
  full-screen interruptions are suppressed;
- disabling the preference, closing the last Autospeed surface, or revoking
  policy access deactivates Autospeed's rule;
- pre-existing user and third-party DND rules remain unchanged;
- framework variant obtains speed on a device with no Google Play services;
- Play-compatible variant does not contact Play location while system fused
  speed meets the quality budget;
- Play-compatible variant does not contact Play location when fallback is
  disabled;
- Play-compatible variant escalates to Play location only after system fused
  speed fails the quality budget and fallback is enabled;
- Play-compatible variant continues through direct GNSS when Play services is
  absent, disabled, outdated, unavailable, or fails the quality budget;
- the orchestrator returns from Play to recovered system fused location without
  provider flapping;
- concurrent provider warming still exposes only one accepted speed stream;
- both location variants apply the same freshness and accuracy policy and stop
  updates under the same lifecycle conditions.

### 8.3 Build configuration tests

- a public release builds without the personal-use acknowledgment;
- a personal release fails when `AUTOSPEED_PERSONAL_USE_ACK` is absent;
- a personal release fails when the acknowledgment value differs by any
  character from the text in section 2.1;
- a personal release succeeds with the exact acknowledgment;
- public and personal artifacts use distinct application IDs and visible build
  labels;
- neither artifact contains motion-detection or speed-based UI lockout logic;
- framework and Play-compatible variants have separate dependency graphs;
- the framework variant contains no Google Play services artifacts;
- every merged manifest omits `android.permission.INTERNET`;
- build metadata identifies both the advisory profile and location backend.

### 8.4 Device tests

Test on supported GrapheneOS releases with:

- location services enabled and disabled;
- GPS-only operation and poor satellite visibility;
- fast system-fused acquisition, delayed acquisition, missing speed, stale
  speed, and unacceptable reported speed accuracy;
- permission auto-reset and notification permission denial;
- battery saver and restricted background settings;
- portrait and both landscape rotations;
- gesture and three-button navigation;
- representative map and media applications installed from non-Play sources;
- airplane mode, confirming the GNSS-backed path remains usable without network
  assistance;
- a network monitor, confirming the Autospeed process makes no direct
  connections while separately recording any OS or Play provider activity;
- GrapheneOS Network Location disabled and enabled;
- sandboxed Google Play absent, present with location rerouting enabled, and
  present with rerouting disabled;
- a stock Google-enabled Android device, confirming that system-first selection
  and the Play-fallback preference behave as documented;
- real road tests against a vehicle speedometer at steady and changing speeds.

Road testing must be performed by a passenger or with instrumentation that does
not require driver interaction.

If an inertial display filter is prototyped, road tests must compare raw
provider speed and filtered speed against the same timestamped reference. The
filter is rejected unless it improves response time without increasing
steady-state error, stop/start artifacts, or stale-fix misrepresentation.

### 8.5 Release acceptance

A release is acceptable when:

- the manifest has no network permission and no background-location permission;
- speed or location information is absent from storage and release logs;
- every location variant works without Google Play services, with the
  Play-compatible variant falling back to the framework source;
- denying media and overlay access leaves the main speedometer functional;
- closing all visible Autospeed surfaces stops location and foreground work;
- the measured cold-start and update latency meet the budgets chosen below;
- the framework build is reproducible in the selected F-Droid-compatible
  toolchain;
- application-level navigation is limited to Autospeed and the configured map
  and media applications, apart from required Android permission/settings
  screens;
- each artifact identifies its location backend, and release documentation
  explains that a system location provider may independently use network
  assistance even though Autospeed lacks network permission;
- the artifact's build profile is identifiable and its advisory behavior
  matches section 2.1;
- a personal artifact cannot be produced without the exact build-time
  acknowledgment.

## 9. Required Decisions

The following decisions must be made before or during implementation:

- **UI toolkit:** Select Views, Compose, or a limited combination after measuring
  cold-start time and APK size on the oldest target device.
- **Startup budgets:** Set numeric limits for first frame, location registration,
  callback-to-display latency, APK size, and idle memory.
- **Framework provider policy:** Define how `LocationManager` selects the
  standard fused, GNSS, or other available providers without disrupting the
  displayed speed.
- **Play-compatible request policy:** Choose request priority, interval, minimum
  distance, and batching behavior.
- **Provider quality budgets:** Define the startup deadline, steady-state stale
  deadline, required `hasSpeed()` behavior, acceptable
  `speedAccuracyMetersPerSecond`, recovery interval, and anti-flapping
  hysteresis that govern escalation and return.
- **Fix-quality policy:** Define freshness, minimum accuracy, handling of
  `speedAccuracyMetersPerSecond`, rounding, and any deliberately minimal
  smoothing.
- **Experimental inertial filtering:** Keep disabled for the initial release.
  Define objective latency and error thresholds before adding accelerometer or
  gyroscope input to a later build.
- **Automatic appearance thresholds:** Tune enter-night lux, enter-day lux, and
  dwell time on both target phones in daylight, dusk, darkness, passing
  shadows, and headlight glare.
- **Notification exceptions:** Confirm the initial Zen policy preserves alarms,
  media, and navigation audio while leaving any emergency-contact exceptions
  configurable through Android's system settings.
- **Overlay appearance:** Choose size, opacity, supported positions, whether it
  can collapse, and behavior when the orientation changes.
- **Companion compatibility rules:** Define which resolved intents qualify map
  and media applications for selection and whether launching their main
  activities without additional data is sufficient.
- **Companion-launch fallback:** Confirm the user-assisted fallback when Android
  blocks launching another activity after starting the foreground overlay.
- **Multiple media sessions:** Approve the playing-then-most-recent selection
  rule or add an explicit preferred media application setting.
- **No-session play behavior:** Decide whether play should target the last known
  session or remain disabled; disabling is the privacy-preserving default.
- **Background image lifecycle:** Decide what to show if a provider revokes the
  persisted URI grant; the proposed fallback is the selected solid color.
- **Local backup:** Confirm that Android backup remains disabled to satisfy the
  local-only interpretation.
- **Dependency policy:** Establish an allowlist and review procedure covering
  privacy, licenses, F-Droid acceptance, update cadence, and startup impact.
- **Location variant distribution:** Decide which Obtainium release artifacts
  include the Play-compatible backend. F-Droid-compatible artifacts default to
  the framework backend unless the selected repository permits the dependency.
- **Public distribution policy:** Decide which public channels receive the
  public profile and whether any channel policy requires different wording.
  Do not add a warning or restriction based only on generalized litigation
  avoidance; cite the exact binding requirement or record it as an explicit
  distribution choice.
- **Application license:** Resolved. Autospeed is MIT-licensed; `LICENSE` at the
  repository root is the binding text, surfaced in the application per section
  3.8.1.
- **Repository contribution policy:** Define how narrowly bug and security fixes
  are accepted and document the required issue/reproduction information.

## 10. Delivery Plan

1. Create a minimal Android project and reproducible public/personal and
   framework/Play-compatible build dimensions with no network permission,
   identifiable artifacts, distinct application IDs, and the exact
   personal-profile acknowledgment gate.
2. Implement settings, immediate display shell, the common location-source
   interface, framework acquisition, conversion, and stale-fix behavior.
3. Add the location orchestrator, direct GNSS warming, and optional
   Play-compatible source with quality-driven escalation and recovery.
4. Implement orientation, full-screen layouts, secondary display, backgrounds,
   accessibility, and screen-awake policy.
5. Add notification-access onboarding, active media-session controls, and the
   optional Autospeed notification-suppression rule.
6. Add map and media application selection, overlay permission flow, foreground
   service, map-companion and media-companion layouts, and tap-to-return
   behavior.
7. Complete privacy review, device testing, startup profiling, road testing, and
   F-Droid/Obtainium release metadata.

## 11. Build and release engineering

- Kotlin and Gradle Kotlin DSL are required.
- JDK, Gradle, Android SDK tools, `typos`, and all other build tools run through
  the repository devcontainer; host installations are not part of the supported
  build.
- The devcontainer base image and downloaded tool archives are pinned by
  immutable digest or cryptographic hash.
- Every Gradle plugin and library uses an exact version. Dependency locking and
  strict checksum verification are required; dynamic and changing versions are
  forbidden.
- ktlint enforces Kotlin formatting, detekt performs semantic static analysis,
  Android lint treats warnings as errors, and `typos` checks source and
  documentation.
- The pure domain core must maintain 100 percent line and branch coverage.
  Android framework adapters require focused Android tests rather than
  meaningless coverage exclusions or tests written only to execute lines.
- Release APKs are built only by the tagged GitHub Actions workflow using the
  same devcontainer, signed with the protected release key, accompanied by
  SHA-256 checksums, and covered by GitHub artifact attestations.
- GitHub Releases is the update source. Stable artifact names must permit
  Obtainium users to follow either the framework or Play-compatible track.
- F-Droid publication is deferred, but the framework variant must retain the
  absence of proprietary runtime dependencies and other metadata needed to
  preserve future eligibility.

Each phase should remain independently usable. Location and the main display
must be stable before media and overlay privileges are introduced.
