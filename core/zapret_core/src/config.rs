use semver::Version;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use thiserror::Error;

pub const CONFIG_SCHEMA_VERSION: u32 = 1;
pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");
pub const DEFAULT_EARLY_BUFFER_BYTES: usize = 8 * 1024;
pub const DEFAULT_EARLY_BUFFER_TIME_MS: u64 = 500;

#[derive(Debug, Error)]
pub enum ConfigError {
    #[error("invalid json: {0}")]
    InvalidJson(String),
    #[error("unsupported config schema version: {0}")]
    UnsupportedSchemaVersion(u32),
    #[error("invalid version string in {0}: {1}")]
    InvalidVersion(&'static str, String),
    #[error("active profile does not exist: {0}")]
    MissingActiveProfile(String),
    #[error("duplicate profile id: {0}")]
    DuplicateProfile(String),
    #[error("duplicate rule id in profile {profile_id}: {rule_id}")]
    DuplicateRule { profile_id: String, rule_id: String },
    #[error("invalid wildcard: {0}")]
    InvalidWildcard(String),
    #[error("invalid port range: {0}-{1}")]
    InvalidPortRange(u16, u16),
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct AppConfig {
    pub app_version: String,
    pub core_version: String,
    pub config_schema_version: u32,
    pub feature_flags: FeatureFlags,
    pub active_profile_id: String,
    pub profiles: Vec<Profile>,
    pub settings: Settings,
    pub last_valid_config_snapshot: Option<Box<LastValidConfigSnapshot>>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct LastValidConfigSnapshot {
    pub active_profile_id: String,
    pub profiles: Vec<Profile>,
    pub settings: Settings,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct FeatureFlags {
    pub enable_tls_sni: bool,
    pub enable_http_host: bool,
    pub enable_aggressive_split: bool,
    pub enable_self_test_ab: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct Settings {
    pub language: Language,
    pub theme_mode: ThemeMode,
    pub debug_logs: bool,
    pub safe_mode_enabled: bool,
    pub updates: UpdateSettings,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Language {
    RU,
    EN,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct UpdateSettings {
    pub repo_reference: String,
    pub api_base_url: String,
    pub auto_update: bool,
    pub wifi_only: bool,
    pub apply_on_next_vpn_start: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct Profile {
    pub id: String,
    pub name: String,
    pub preset: Preset,
    pub custom_techniques: Option<CustomTechniques>,
    pub rules: Vec<Rule>,
    pub quic_policy: QuicPolicy,
    pub fallback_policy: FallbackPolicy,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum Preset {
    Compatible,
    Balanced,
    Aggressive,
    Custom,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct CustomTechniques {
    pub http_split_offsets: Vec<usize>,
    pub tls_split_offsets: Vec<usize>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum QuicPolicy {
    Allow,
    BlockUdp443ForApply,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct FallbackPolicy {
    pub aggressive_threshold: u32,
    pub balanced_threshold: u32,
    pub time_window_seconds: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct Rule {
    pub id: String,
    pub priority: u32,
    pub action: RuleAction,
    #[serde(rename = "match")]
    pub matcher: RuleMatch,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum RuleAction {
    APPLY,
    DIRECT,
    BLOCK,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct RuleMatch {
    pub protocol: Protocol,
    pub domain_exact: Option<String>,
    pub domain_wildcard: Option<String>,
    pub cidr: Option<String>,
    pub port_range: Option<PortRange>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Protocol {
    TCP,
    UDP,
    ANY,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct PortRange {
    pub start: u16,
    pub end: u16,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct FlowKey {
    pub proto: Protocol,
    pub src_ip: String,
    pub src_port: u16,
    pub dst_ip: String,
    pub dst_port: u16,
    pub direction: Direction,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Direction {
    OUTBOUND,
    INBOUND,
}

impl AppConfig {
    pub fn from_json(json: &str) -> Result<Self, ConfigError> {
        let config: AppConfig =
            serde_json::from_str(json).map_err(|error| ConfigError::InvalidJson(error.to_string()))?;
        config.validate()?;
        Ok(config)
    }

    pub fn validate(&self) -> Result<(), ConfigError> {
        if self.config_schema_version != CONFIG_SCHEMA_VERSION {
            return Err(ConfigError::UnsupportedSchemaVersion(
                self.config_schema_version,
            ));
        }

        Version::parse(&self.app_version)
            .map_err(|_| ConfigError::InvalidVersion("app_version", self.app_version.clone()))?;
        Version::parse(&self.core_version)
            .map_err(|_| ConfigError::InvalidVersion("core_version", self.core_version.clone()))?;

        let mut profile_ids = HashSet::new();
        let mut has_active_profile = false;
        for profile in &self.profiles {
            if !profile_ids.insert(profile.id.clone()) {
                return Err(ConfigError::DuplicateProfile(profile.id.clone()));
            }
            if profile.id == self.active_profile_id {
                has_active_profile = true;
            }
            let mut rule_ids = HashSet::new();
            for rule in &profile.rules {
                if !rule_ids.insert(rule.id.clone()) {
                    return Err(ConfigError::DuplicateRule {
                        profile_id: profile.id.clone(),
                        rule_id: rule.id.clone(),
                    });
                }
                if let Some(wildcard) = &rule.matcher.domain_wildcard {
                    if !wildcard.starts_with("*.") || wildcard.len() < 4 {
                        return Err(ConfigError::InvalidWildcard(wildcard.clone()));
                    }
                }
                if let Some(range) = &rule.matcher.port_range {
                    if range.start > range.end {
                        return Err(ConfigError::InvalidPortRange(range.start, range.end));
                    }
                }
            }
        }

        if !has_active_profile {
            return Err(ConfigError::MissingActiveProfile(
                self.active_profile_id.clone(),
            ));
        }

        Ok(())
    }

    pub fn active_profile(&self) -> &Profile {
        self.profiles
            .iter()
            .find(|profile| profile.id == self.active_profile_id)
            .expect("validated active profile must exist")
    }

    pub fn sample() -> Self {
        Self {
            app_version: "0.1.0".to_string(),
            core_version: CORE_VERSION.to_string(),
            config_schema_version: CONFIG_SCHEMA_VERSION,
            feature_flags: FeatureFlags {
                enable_tls_sni: true,
                enable_http_host: true,
                enable_aggressive_split: true,
                enable_self_test_ab: true,
            },
            active_profile_id: "default".to_string(),
            profiles: vec![Profile {
                id: "default".to_string(),
                name: "Default".to_string(),
                preset: Preset::Balanced,
                custom_techniques: None,
                rules: vec![Rule {
                    id: "apply-example".to_string(),
                    priority: 10,
                    action: RuleAction::APPLY,
                    matcher: RuleMatch {
                        protocol: Protocol::TCP,
                        domain_exact: Some("example.com".to_string()),
                        domain_wildcard: None,
                        cidr: None,
                        port_range: Some(PortRange { start: 443, end: 443 }),
                    },
                }],
                quic_policy: QuicPolicy::BlockUdp443ForApply,
                fallback_policy: FallbackPolicy {
                    aggressive_threshold: 3,
                    balanced_threshold: 2,
                    time_window_seconds: 300,
                },
            }],
            settings: Settings {
                language: Language::EN,
                theme_mode: ThemeMode::SYSTEM,
                debug_logs: false,
                safe_mode_enabled: true,
                updates: UpdateSettings {
                    repo_reference: "owner/repo".to_string(),
                    api_base_url: "https://api.github.com".to_string(),
                    auto_update: true,
                    wifi_only: true,
                    apply_on_next_vpn_start: true,
                },
            },
            last_valid_config_snapshot: None,
        }
    }
}
