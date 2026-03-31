# Config Schema v1

Top-level JSON shape:

```json
{
  "app_version": "0.1.0",
  "core_version": "0.1.0",
  "config_schema_version": 1,
  "feature_flags": {
    "enable_tls_sni": true,
    "enable_http_host": true,
    "enable_aggressive_split": true,
    "enable_self_test_ab": true
  },
  "active_profile_id": "default",
  "profiles": [],
  "settings": {},
  "last_valid_config_snapshot": null
}
```

## Profile

- `id`: string
- `name`: localized user-visible string
- `preset`: `Compatible | Balanced | Aggressive | Custom`
- `custom_techniques`: optional split overrides used only when `preset = Custom`
- `rules`: ordered list, first match wins
- `quic_policy`: `Allow | BlockUdp443ForApply`
- `fallback_policy`: downgrade thresholds and time window

## Rule

- `id`: stable string identifier
- `priority`: numeric ordering, lower means earlier
- `action`: `APPLY | DIRECT | BLOCK`
- `match.protocol`: `TCP | UDP | ANY`
- `match.domain_exact`: optional string
- `match.domain_wildcard`: optional `*.example.com`
- `match.cidr`: optional CIDR
- `match.port_range`: optional `[start, end]`

## Persistence

- Rust validates all imported JSON before UI persistence.
- UI stores only the last valid config and mirrors it into `last_valid_config_snapshot` on successful apply.
- Future migrations are keyed by `config_schema_version`.

