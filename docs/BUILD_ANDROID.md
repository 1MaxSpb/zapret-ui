# Android Build Notes

The Android MVP is composed from two pieces:

- Flutter UI package in `apps/zapret_ui`
- Kotlin VPN adapter library in `platform/android`

The adapter expects the Rust library to be built for:

- `arm64-v8a`
- `armeabi-v7a`

Recommended build pipeline:

1. Build `zapret_core` shared libraries for both ABIs.
2. Place baseline binaries under the Android packaging step.
3. Build the Flutter APK embedding the Android adapter.
4. Sign the APK and keep the Ed25519 public key embedded for update verification.

Local toolchains are intentionally decoupled so the repo can evolve before the full mobile build environment is installed.

