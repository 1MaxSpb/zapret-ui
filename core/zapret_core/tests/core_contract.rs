use zapret_core::{
    AppConfig, DecisionState, Direction, Engine, FlowKey, FlowResultCode, FlowResultReport,
    PortRange, Preset, Profile, Protocol, QuicPolicy, Rule, RuleAction, RuleMatch,
};

#[test]
fn validates_sample_config() {
    let config = AppConfig::sample();
    assert!(config.validate().is_ok());
}

#[test]
fn matches_http_host_rule_and_returns_apply() {
    let config = AppConfig::sample();
    let engine = Engine::new(config);
    let flow = FlowKey {
        proto: Protocol::TCP,
        src_ip: "10.0.0.2".into(),
        src_port: 54000,
        dst_ip: "93.184.216.34".into(),
        dst_port: 443,
        direction: Direction::OUTBOUND,
    };
    let http = b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n";
    let decision = engine.inspect_early_bytes(&flow, http, 0);
    assert_eq!(decision.action, RuleAction::APPLY);
    assert_eq!(decision.matched_rule_id.as_deref(), Some("apply-example"));
}

#[test]
fn returns_need_more_data_for_partial_http_headers() {
    let config = AppConfig::sample();
    let engine = Engine::new(config);
    let flow = FlowKey {
        proto: Protocol::TCP,
        src_ip: "10.0.0.2".into(),
        src_port: 54000,
        dst_ip: "93.184.216.34".into(),
        dst_port: 443,
        direction: Direction::OUTBOUND,
    };
    let partial = b"GET / HTTP/1.1\r\nHost: examp";
    let decision = engine.inspect_early_bytes(&flow, partial, 0);
    assert_eq!(decision.state, DecisionState::NEED_MORE_DATA);
}

#[test]
fn downgrades_preset_after_failures() {
    let mut config = AppConfig::sample();
    config.profiles[0].preset = Preset::Aggressive;
    let mut engine = Engine::new(config);
    let flow = FlowKey {
        proto: Protocol::TCP,
        src_ip: "10.0.0.2".into(),
        src_port: 54000,
        dst_ip: "93.184.216.34".into(),
        dst_port: 443,
        direction: Direction::OUTBOUND,
    };
    for tick in 0..3 {
        engine.report_flow_result(FlowResultReport {
            rule_id: Some("apply-example".into()),
            hostname: Some("example.com".into()),
            result_code: FlowResultCode::Timeout,
            now_ms: tick * 10,
        });
    }
    let http = b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n";
    let decision = engine.inspect_early_bytes(&flow, http, 100);
    assert_eq!(decision.effective_preset, Preset::Balanced);
}

#[test]
fn blocks_udp_443_when_apply_rule_hits() {
    let mut config = AppConfig::sample();
    let fallback_policy = config.profiles[0].fallback_policy.clone();
    config.profiles[0] = Profile {
        id: "default".into(),
        name: "Default".into(),
        preset: Preset::Balanced,
        custom_techniques: None,
        rules: vec![Rule {
            id: "udp-apply".into(),
            priority: 1,
            action: RuleAction::APPLY,
            matcher: RuleMatch {
                protocol: Protocol::UDP,
                domain_exact: None,
                domain_wildcard: None,
                cidr: None,
                port_range: Some(PortRange { start: 443, end: 443 }),
            },
        }],
        quic_policy: QuicPolicy::BlockUdp443ForApply,
        fallback_policy,
    };
    let engine = Engine::new(config);
    let flow = FlowKey {
        proto: Protocol::UDP,
        src_ip: "10.0.0.2".into(),
        src_port: 54000,
        dst_ip: "1.1.1.1".into(),
        dst_port: 443,
        direction: Direction::OUTBOUND,
    };
    let decision = engine.inspect_early_bytes(&flow, &[], 0);
    assert_eq!(decision.action, RuleAction::BLOCK);
}
