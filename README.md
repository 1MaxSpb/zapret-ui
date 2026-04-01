# zapret-ui

`zapret-ui` is a monorepo for the Android-first `zapret` mobile client:

- `apps/android/` - launchable Android host app for local debugging and Android Studio Run
- `apps/zapret_ui/` - Flutter UI shell
- `core/zapret_core/` - Rust config, rules, split planning, and FFI
- `platform/android/` - Kotlin `VpnService` adapter, diagnostics, and core updates
- `docs/` - product and implementation documentation

The repository is intentionally structured so preset tables can evolve independently from the configuration schema and FFI surface.

## Build & Run Android

Open the repository root, the one that contains `settings.gradle.kts`. The launchable Android host app is:

- Path: `apps/android`
- Gradle module: `:apps:android`
- Type: `com.android.application`

The Android adapter in `:platform:android` is a library module. It is required by the host app, but it is not runnable by itself and should not be selected as the Run target.

### Android Studio (Windows)

1. Clone the repository and open the monorepo root in Android Studio.
2. Wait for Gradle Sync to finish.
3. Select the Android application module at `apps/android` with Gradle path `:apps:android`.
4. Run on an emulator or device.

If Android Studio shows `Module not specified`, create or edit the Run configuration and point it to the application module from `apps/android`, not to `:platform:android`.

### Gradle commands

The repository currently documents plain `gradle` commands because no Gradle wrapper is committed in this repo.

- Build the host app debug APK: `gradle :apps:android:assembleDebug`
- Install the host app on a connected device or emulator: `gradle :apps:android:installDebug`
- Build the Android library directly: `gradle :platform:android:assemble`
- Build both in one pass: `gradle :platform:android:assemble :apps:android:assembleDebug`

There is no `:zapapp` module in this repository. If you see `project 'zapapp' not found`, use `:apps:android` instead.
