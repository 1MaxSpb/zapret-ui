# zapret-ui

`zapret-ui` is a monorepo for the Android-first `zapret` mobile client:

- `apps/android/` - launchable Android host app for local debugging and Android Studio Run
- `apps/zapret_ui/` - Flutter UI shell
- `core/zapret_core/` - Rust config, rules, split planning, and FFI
- `platform/android/` - Kotlin `VpnService` adapter, diagnostics, and core updates
- `docs/` - product and implementation documentation

The repository is intentionally structured so preset tables can evolve independently from the configuration schema and FFI surface.

## Android Studio (Windows)

1. Clone the repository and open the monorepo root in Android Studio.
2. Wait for Gradle sync to complete.
3. Select the `apps:android` run target.
4. Run on an emulator or device.

The host app is intentionally minimal. It exists to launch a debuggable Android application module and wire it to `:platform:android`.

## Gradle commands

- Build the Android library: `gradle :platform:android:assemble`
- Build the launchable host app: `gradle :apps:android:assembleDebug`
- Build both in one pass: `gradle :platform:android:assemble :apps:android:assembleDebug`
