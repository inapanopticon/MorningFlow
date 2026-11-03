# MorningFlow

MorningFlow is a calm, voice-guided morning routine for Android. It turns the first half
hour after waking into a gentle routine: it notices when you get up and offers to start,
a warm recorded voice keeps you company through each step, the phone's sensors count
squats and steps instead of asking for taps, and before you leave it reads out today's
weather, plans and packing list.

The app is deliberately minimal: little text, large type, and mostly silence between
short spoken prompts, so it can be followed from across the room with sleepy eyes.

Built with Kotlin and Jetpack Compose.

## Features

- **Guided morning session** — eight stages from Leave Bed to Leaving Reminder, in a
  fixed order, with voice guidance and automatic progression. The screen stays on, Back
  asks before ending, and the overall progress is always visible.
- **Short, Medium or Long, per step** — every timed step has three lengths. Longer
  versions say a little more and leave more quiet time; Medium matches the default
  30-minute routine. All lengths share the same voice and the same opening and closing
  lines.
- **Recorded companion voice** — every fixed line is a studio-quality recording in one
  consistent voice, paced so each step finishes speaking before its countdown ends.
- **Sensor-verified activity** — steps come from the hardware step detector, squats from
  a gravity-projected motion state machine, and room brightness from the light sensor.
  Squats and walking speak only as they start, stay quiet while you move, and say
  "nice work" when the target is reached.
- **Wake-up detection** — inside the configured wake window, a bounded foreground
  service watches the accelerometer and gyroscope; the first confirmed pick-up posts a
  notification offering to start the morning.
- **Leaving reminder** — live weather (Open-Meteo), today's next events and your own
  packing list. Fixed lines are recorded; only the day's details use text-to-speech.
  Umbrella, jacket and sunscreen nudges follow thresholds you choose.
- **History** — a monthly calendar marking every finished morning, plus your most
  recent sessions.
- **Routine templates** — editable routines selected automatically by day, time and
  priority. A fresh install starts with a single Recommended Morning template.
- **Personal settings** — editable packing list, weather for your location or a city you
  pick, background music and voice volume, and System / Light / Dark appearance.
- **Graceful degradation** — a denied permission, missing sensor or dead network
  removes exactly the feature that needed it and never blocks the session.

## Requirements

- Android Studio 2026.1 or newer with JDK 17
- Android SDK 36 (compile SDK 36.1)
- Internet access for the first Gradle sync, live weather and city search
- A physical device for the sensor features (min SDK 26, target SDK 36)

No API keys or local configuration files are required: the weather and city search
services (Open-Meteo) are keyless and there is no Firebase dependency.

## Build and run

```bash
./gradlew assembleDebug          # build (APK at app/build/outputs/apk/debug/)
./gradlew installDebug           # install on the connected device
./gradlew testDebugUnitTest      # full JVM unit-test suite
./gradlew connectedDebugAndroidTest  # instrumented tests - needs a device/emulator
```

Or open the project in Android Studio, let Gradle sync, and Run. First launch walks
through a short onboarding: wake window, ready-by time, days and permissions.

### Permissions

| Permission | Used for |
| --- | --- |
| `ACCESS_COARSE_LOCATION` | choosing the forecast location (approximate only; no background location) — not needed if you pick a city |
| `READ_CALENDAR` | today's remaining events in the leaving reminder (time and title only) |
| `POST_NOTIFICATIONS` (Android 13+) | the morning wake-up prompt |
| Alarms & reminders special access | starting detection exactly at the wake window |
| `ACTIVITY_RECOGNITION` (Android 10+) | the hardware step detector |
| `WAKE_LOCK`, foreground service (install-time) | keeping detection alive while the screen is off, bounded to the wake window |

Every runtime permission is optional. Denying one degrades that feature only:

| Without | The session still runs; only |
| --- | --- |
| network | the weather line is absent |
| calendar permission | the reminder says the calendar isn't available |
| location permission | weather is absent, unless a city is chosen in Settings |
| notification permission | there is no wake-up prompt |
| a step counter / gyroscope | the affected step falls back to its timer |

### Running on an emulator

An emulator has no usable motion sensors, light sensor or location. Where
`AppContainer` is constructed (in `MorningFlowApplication`), switch to the emulator
configuration:

```kotlin
AppContainer(context, ContainerConfig.EMULATOR)
```

This substitutes fakes for the sensors, wake-up detection, weather and calendar.
Persistence is deliberately left real — the database and settings are what an
emulator run is good for verifying. Restore `AppContainer(context)` before
committing.

### Vendor battery managers

Aggressive OEM battery managers can restrict background work. On devices with such
controls (e.g. Huawei's App launch management), allow MorningFlow to run in the
background if wake-up detection should work with the screen off.

## Architecture

Three layers, joined by interfaces in `core/data/Contracts.kt`. Feature code depends
on repository contracts, never on implementations, and every repository has a fake
and a real implementation kept in lockstep by a shared contract test suite.

```text
app/src/main/java/com/morningflow/app/
├── app/                  entry point, top-level ViewModel, navigation
├── core/
│   ├── audio/            narration (generated clip lists + selection logic), playback
│   ├── data/             repository contracts, fakes, Room/DataStore wiring, AppContainer
│   ├── database/         Room entities, DAOs, migrations
│   ├── datastore/        settings persistence
│   ├── designsystem/     theme (light and dark), components, icons, illustrations
│   ├── domain/           session engine and runtime, leaving-reminder narration,
│   │                     weather/location coordination
│   ├── model/            shared domain models
│   ├── permissions/      permission and special-access helpers
│   ├── sensor/           step, squat, brightness and wake-up detectors
│   └── wakeup/           alarm scheduling, detection service, notifications
└── feature/              one package per screen: onboarding, home, session,
                          completion, templates, history, settings
```

### Narration

The recorded lines live in `app/src/main/res/raw` as mono Opus `.ogg` clips.
`core/audio/NarrationClips.kt` lists, for every step and length, which clips play and
the pause after each; it is generated from the measured recordings, so edit the
script and regenerate rather than changing it by hand. `NarrationScripts` picks the
right version at runtime, and `NarrationPacingTest` re-measures the shipped clips on a
device to prove every length still finishes inside its countdown.

## Testing

- **JVM unit tests** (`app/src/test`) cover repositories, DAOs, settings migrations,
  the session engine, narration selection and assembly, history, schedulers and
  ViewModels. The repository contract suite runs every assertion against both the
  fake and the real implementation so they cannot drift apart.
- **Instrumented tests** (`app/src/androidTest`) validate the Room schema migration
  against real SQLite, including foreign keys and indices, and measure narration pacing
  for every length from the shipped recordings.

## Design

Design tokens (colors for light and dark, spacing, radii, elevation, text styles),
components, icons and illustrations are centralized in `core/designsystem`.
