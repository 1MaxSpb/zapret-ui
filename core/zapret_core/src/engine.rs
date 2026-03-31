use crate::config::{
    AppConfig, FallbackPolicy, FlowKey, PortRange, Preset, Profile, Protocol, QuicPolicy, Rule,
    RuleAction, RuleMatch,
};
use crate::parser::{extract_target, TrafficKind};
use ipnet::IpNet;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::net::IpAddr;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DecisionState {
    NEED_MORE_DATA,
    FINAL,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct SplitPlan {
    pub strategy: String,
    pub offsets: Vec<usize>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct EarlyDecision {
    pub state: DecisionState,
    pub action: RuleAction,
    pub effective_preset: Preset,
    pub split_plan: Option<SplitPlan>,
    pub quic_policy: QuicPolicy,
    pub matched_rule_id: Option<String>,
    pub hostname: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum FlowResultCode {
    Success,
    Timeout,
    Reset,
    HandshakeFail,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct FlowResultReport {
    pub rule_id: Option<String>,
    pub hostname: Option<String>,
    pub result_code: FlowResultCode,
    pub now_ms: u64,
}

#[derive(Debug, Clone)]
struct FailureWindow {
    timestamps_ms: Vec<u64>,
}

#[derive(Debug, Clone)]
pub struct Engine {
    config: AppConfig,
    failures: HashMap<String, FailureWindow>,
}

impl Engine {
    pub fn new(config: AppConfig) -> Self {
        Self {
            config,
            failures: HashMap::new(),
        }
    }

    pub fn config(&self) -> &AppConfig {
        &self.config
    }

    pub fn inspect_early_bytes(
        &self,
        flow: &FlowKey,
        buffered_bytes: &[u8],
        _now_ms: u64,
    ) -> EarlyDecision {
        let profile = self.config.active_profile();
        let extracted = extract_target(buffered_bytes, &self.config.feature_flags);
        let matched_rule = match_rule(profile, flow, extracted.hostname.as_deref());

        if flow.proto == Protocol::UDP {
            return inspect_udp(profile, flow, matched_rule);
        }

        let Some(rule) = matched_rule else {
            if extracted.needs_more_data {
                return need_more_data(profile, extracted.hostname);
            }
            return EarlyDecision {
                state: DecisionState::FINAL,
                action: RuleAction::DIRECT,
                effective_preset: profile.preset.clone(),
                split_plan: None,
                quic_policy: profile.quic_policy.clone(),
                matched_rule_id: None,
                hostname: extracted.hostname,
            };
        };

        match rule.action {
            RuleAction::BLOCK => EarlyDecision {
                state: DecisionState::FINAL,
                action: RuleAction::BLOCK,
                effective_preset: profile.preset.clone(),
                split_plan: None,
                quic_policy: profile.quic_policy.clone(),
                matched_rule_id: Some(rule.id.clone()),
                hostname: extracted.hostname,
            },
            RuleAction::DIRECT => EarlyDecision {
                state: DecisionState::FINAL,
                action: RuleAction::DIRECT,
                effective_preset: profile.preset.clone(),
                split_plan: None,
                quic_policy: profile.quic_policy.clone(),
                matched_rule_id: Some(rule.id.clone()),
                hostname: extracted.hostname,
            },
            RuleAction::APPLY => {
                if extracted.hostname.is_none() && extracted.needs_more_data {
                    return need_more_data(profile, None);
                }
                let effective_preset =
                    downgrade_preset(profile, &self.failures, &rule.id, &profile.fallback_policy);
                EarlyDecision {
                    state: DecisionState::FINAL,
                    action: RuleAction::APPLY,
                    effective_preset: effective_preset.clone(),
                    split_plan: build_split_plan(profile, &effective_preset, extracted.kind, buffered_bytes.len()),
                    quic_policy: profile.quic_policy.clone(),
                    matched_rule_id: Some(rule.id.clone()),
                    hostname: extracted.hostname,
                }
            }
        }
    }

    pub fn report_flow_result(&mut self, report: FlowResultReport) {
        let Some(rule_id) = report.rule_id else {
            return;
        };

        match report.result_code {
            FlowResultCode::Success => {
                self.failures.remove(&rule_id);
            }
            FlowResultCode::Timeout | FlowResultCode::Reset | FlowResultCode::HandshakeFail => {
                let policy = &self.config.active_profile().fallback_policy;
                let entry = self
                    .failures
                    .entry(rule_id)
                    .or_insert_with(|| FailureWindow { timestamps_ms: Vec::new() });
                entry.timestamps_ms.push(report.now_ms);
                trim_failures(entry, policy, report.now_ms);
            }
        }
    }
}

fn need_more_data(profile: &Profile, hostname: Option<String>) -> EarlyDecision {
    EarlyDecision {
        state: DecisionState::NEED_MORE_DATA,
        action: RuleAction::DIRECT,
        effective_preset: profile.preset.clone(),
        split_plan: None,
        quic_policy: profile.quic_policy.clone(),
        matched_rule_id: None,
        hostname,
    }
}

fn inspect_udp(profile: &Profile, flow: &FlowKey, matched_rule: Option<&Rule>) -> EarlyDecision {
    let Some(rule) = matched_rule else {
        return EarlyDecision {
            state: DecisionState::FINAL,
            action: RuleAction::DIRECT,
            effective_preset: profile.preset.clone(),
            split_plan: None,
            quic_policy: profile.quic_policy.clone(),
            matched_rule_id: None,
            hostname: None,
        };
    };

    let action = match rule.action {
        RuleAction::BLOCK => RuleAction::BLOCK,
        RuleAction::DIRECT => RuleAction::DIRECT,
        RuleAction::APPLY
            if flow.dst_port == 443 && profile.quic_policy == QuicPolicy::BlockUdp443ForApply =>
            RuleAction::BLOCK,
        RuleAction::APPLY => RuleAction::DIRECT,
    };

    EarlyDecision {
        state: DecisionState::FINAL,
        action,
        effective_preset: profile.preset.clone(),
        split_plan: None,
        quic_policy: profile.quic_policy.clone(),
        matched_rule_id: Some(rule.id.clone()),
        hostname: None,
    }
}

fn match_rule<'a>(profile: &'a Profile, flow: &FlowKey, hostname: Option<&str>) -> Option<&'a Rule> {
    let mut rules: Vec<&Rule> = profile.rules.iter().collect();
    rules.sort_by_key(|rule| rule.priority);
    rules.into_iter().find(|rule| matches_rule(rule, flow, hostname))
}

fn matches_rule(rule: &Rule, flow: &FlowKey, hostname: Option<&str>) -> bool {
    protocol_matches(&rule.matcher.protocol, &flow.proto)
        && domain_matches(&rule.matcher, hostname)
        && cidr_matches(&rule.matcher, &flow.dst_ip)
        && port_matches(&rule.matcher, flow.dst_port)
}

fn protocol_matches(rule_protocol: &Protocol, flow_protocol: &Protocol) -> bool {
    matches!(rule_protocol, Protocol::ANY) || rule_protocol == flow_protocol
}

fn domain_matches(rule_match: &RuleMatch, hostname: Option<&str>) -> bool {
    let Some(hostname) = hostname else {
        return rule_match.domain_exact.is_none() && rule_match.domain_wildcard.is_none();
    };
    if let Some(exact) = &rule_match.domain_exact {
        if hostname.eq_ignore_ascii_case(exact) {
            return true;
        }
    }
    if let Some(wildcard) = &rule_match.domain_wildcard {
        if let Some(suffix) = wildcard.strip_prefix("*.") {
            if hostname == suffix || hostname.ends_with(&format!(".{suffix}")) {
                return true;
            }
        }
    }
    rule_match.domain_exact.is_none() && rule_match.domain_wildcard.is_none()
}

fn cidr_matches(rule_match: &RuleMatch, dst_ip: &str) -> bool {
    let Some(cidr) = &rule_match.cidr else {
        return true;
    };
    let Ok(net) = cidr.parse::<IpNet>() else {
        return false;
    };
    let Ok(ip) = dst_ip.parse::<IpAddr>() else {
        return false;
    };
    net.contains(&ip)
}

fn port_matches(rule_match: &RuleMatch, dst_port: u16) -> bool {
    match &rule_match.port_range {
        Some(range) => range.start <= dst_port && dst_port <= range.end,
        None => true,
    }
}

fn build_split_plan(
    profile: &Profile,
    preset: &Preset,
    kind: TrafficKind,
    buffered_len: usize,
) -> Option<SplitPlan> {
    if buffered_len == 0 {
        return None;
    }

    let mut offsets = match preset {
        Preset::Compatible => match kind {
            TrafficKind::Http => vec![8, 32],
            TrafficKind::Tls => vec![5, 43],
            TrafficKind::Unknown => vec![16],
        },
        Preset::Balanced => match kind {
            TrafficKind::Http => vec![8, 24, 64],
            TrafficKind::Tls => vec![5, 43, 128],
            TrafficKind::Unknown => vec![16, 48],
        },
        Preset::Aggressive => match kind {
            TrafficKind::Http => vec![4, 12, 32, 96],
            TrafficKind::Tls => vec![5, 24, 64, 160],
            TrafficKind::Unknown => vec![8, 24, 64],
        },
        Preset::Custom => {
            if let Some(custom) = &profile.custom_techniques {
                match kind {
                    TrafficKind::Http => custom.http_split_offsets.clone(),
                    TrafficKind::Tls => custom.tls_split_offsets.clone(),
                    TrafficKind::Unknown => custom.http_split_offsets.clone(),
                }
            } else {
                Vec::new()
            }
        }
    };

    offsets.retain(|offset| *offset > 0 && *offset < buffered_len);
    offsets.sort_unstable();
    offsets.dedup();
    if offsets.is_empty() {
        None
    } else {
        Some(SplitPlan {
            strategy: format!("{preset:?}-{kind:?}").to_lowercase(),
            offsets,
        })
    }
}

fn downgrade_preset(
    profile: &Profile,
    failures: &HashMap<String, FailureWindow>,
    rule_id: &str,
    fallback_policy: &FallbackPolicy,
) -> Preset {
    let failure_count = failures
        .get(rule_id)
        .map(|window| window.timestamps_ms.len() as u32)
        .unwrap_or_default();
    match profile.preset {
        Preset::Aggressive if failure_count >= fallback_policy.aggressive_threshold => {
            if failure_count >= fallback_policy.aggressive_threshold + fallback_policy.balanced_threshold {
                Preset::Compatible
            } else {
                Preset::Balanced
            }
        }
        Preset::Balanced if failure_count >= fallback_policy.balanced_threshold => Preset::Compatible,
        _ => profile.preset.clone(),
    }
}

fn trim_failures(window: &mut FailureWindow, policy: &FallbackPolicy, now_ms: u64) {
    let max_age_ms = policy.time_window_seconds.saturating_mul(1000);
    window
        .timestamps_ms
        .retain(|timestamp| now_ms.saturating_sub(*timestamp) <= max_age_ms);
}
