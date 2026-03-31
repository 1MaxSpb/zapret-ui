# zapret-ui

`zapret-ui` is a monorepo for the Android-first `zapret` mobile client:

- `apps/zapret_ui/` - Flutter UI shell
- `core/zapret_core/` - Rust config, rules, split planning, and FFI
- `platform/android/` - Kotlin `VpnService` adapter, diagnostics, and core updates
- `docs/` - product and implementation documentation

The repository is intentionally structured so preset tables can evolve independently from the configuration schema and FFI surface.

