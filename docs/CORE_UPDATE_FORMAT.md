# Core Update Format

Android uses staged `zapret_core` updates delivered as `core-<core_version>.zip`.

## Archive layout

- `core_manifest.json`
- `arm64-v8a/libzapret_core.so`
- `armeabi-v7a/libzapret_core.so`
- `presets.json` (optional)

## Manifest fields

- `core_version`: semantic version string
- `min_app_version`: minimum compatible app version
- `config_schema_version`: required config schema
- `abis`: supported ABI list
- `files`: array of `{ "path": "...", "sha256": "...", "size": 123 }`
- `signature`: Ed25519 signature over the canonical manifest payload

## Install flow

1. Download asset from GitHub Releases using public or PAT-authenticated API.
2. Verify Ed25519 signature with the embedded public key.
3. Verify file sizes and SHA-256 digests.
4. Verify app version, schema version, and ABI compatibility.
5. Run `healthcheck` against the candidate core.
6. Stage activation for the next VPN start.
7. Roll back automatically if load or runtime health checks fail.

