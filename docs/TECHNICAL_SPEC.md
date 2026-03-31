# zapret-ui Technical Spec

This document captures the v0.1-beta implementation contract for the Android MVP.

## Monorepo layout

- `apps/zapret_ui/` - Flutter UI, onboarding, settings, logs, diagnostics, updates.
- `core/zapret_core/` - Rust-owned configuration schema, rule engine, Host/SNI extraction, split planning, fallback downgrade, FFI.
- `platform/android/` - Kotlin `VpnService`, transport loops, safe mode, self-test, update manager, diagnostic bundle export.

## Data plane

- TCP forwarding is the only full transport path in v0.1-beta.
- UDP is direct passthrough only, except `APPLY + UDP/443`, which is blocked when QUIC blocking is enabled.
- Kotlin owns TUN, protected sockets, forwarding, and update lifecycle.
- Rust owns config validation and the decision engine for early bytes.

## Runtime limits

- Early buffer limit: `8192` bytes.
- Early buffer time budget: `500ms`.
- If no host/SNI or IP/port rule yields a final decision by the limit, Android finalizes the flow as `DIRECT` and emits a structured log entry.

