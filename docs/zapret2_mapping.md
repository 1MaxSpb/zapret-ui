# zapret2 Mapping Document

This document is the first artifact in the repository and the single place where preset tables are expected to evolve.

## Goals

- Keep the config schema stable while the preset internals change.
- Map upstream `zapret2` techniques into mobile-safe equivalents.
- Separate transport ownership (Android/iOS adapters) from evasion heuristics (`zapret_core`).

## Initial preset matrix

| Preset | HTTP split | TLS split | Retry threshold | Notes |
| --- | --- | --- | --- | --- |
| Compatible | Header-safe split after request line | Single SNI-safe split | 1 failure before downgrade | Default safe mode preset |
| Balanced | Header split plus early header segmentation | Two-stage ClientHello split | 2 failures before downgrade | Default user preset |
| Aggressive | Multi-point request/header segmentation | Multi-point ClientHello segmentation | 3 failures before downgrade | Disabled automatically in safe mode |

## Open mapping work

- Validate mobile-safe offsets against target networks.
- Compare TLS split positions with real ClientHello examples.
- Refine fallback thresholds per rule or domain family if empirical data justifies it.
