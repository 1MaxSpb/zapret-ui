import 'dart:convert';

enum AppLanguage { ru, en }

enum AppThemePreference { system, light, dark }

enum VpnStatus { off, starting, on, error, safeMode }

enum ProfilePreset { compatible, balanced, aggressive, custom }

enum RuleAction { apply, direct, block }

enum RuleProtocol { tcp, udp, any }

enum QuicPolicy { allow, blockUdp443ForApply }

class FeatureFlags {
  const FeatureFlags({
    required this.enableTlsSni,
    required this.enableHttpHost,
    required this.enableAggressiveSplit,
    required this.enableSelfTestAb,
  });

  final bool enableTlsSni;
  final bool enableHttpHost;
  final bool enableAggressiveSplit;
  final bool enableSelfTestAb;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'enable_tls_sni': enableTlsSni,
        'enable_http_host': enableHttpHost,
        'enable_aggressive_split': enableAggressiveSplit,
        'enable_self_test_ab': enableSelfTestAb,
      };
}

class UpdateSettings {
  const UpdateSettings({
    required this.repoReference,
    required this.apiBaseUrl,
    required this.autoUpdate,
    required this.wifiOnly,
    required this.applyOnNextVpnStart,
  });

  final String repoReference;
  final String apiBaseUrl;
  final bool autoUpdate;
  final bool wifiOnly;
  final bool applyOnNextVpnStart;

  UpdateSettings copyWith({
    String? repoReference,
    String? apiBaseUrl,
    bool? autoUpdate,
    bool? wifiOnly,
    bool? applyOnNextVpnStart,
  }) {
    return UpdateSettings(
      repoReference: repoReference ?? this.repoReference,
      apiBaseUrl: apiBaseUrl ?? this.apiBaseUrl,
      autoUpdate: autoUpdate ?? this.autoUpdate,
      wifiOnly: wifiOnly ?? this.wifiOnly,
      applyOnNextVpnStart: applyOnNextVpnStart ?? this.applyOnNextVpnStart,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'repo_reference': repoReference,
        'api_base_url': apiBaseUrl,
        'auto_update': autoUpdate,
        'wifi_only': wifiOnly,
        'apply_on_next_vpn_start': applyOnNextVpnStart,
      };
}

class AppSettings {
  const AppSettings({
    required this.language,
    required this.themeMode,
    required this.debugLogs,
    required this.safeModeEnabled,
    required this.updates,
  });

  final AppLanguage language;
  final AppThemePreference themeMode;
  final bool debugLogs;
  final bool safeModeEnabled;
  final UpdateSettings updates;

  AppSettings copyWith({
    AppLanguage? language,
    AppThemePreference? themeMode,
    bool? debugLogs,
    bool? safeModeEnabled,
    UpdateSettings? updates,
  }) {
    return AppSettings(
      language: language ?? this.language,
      themeMode: themeMode ?? this.themeMode,
      debugLogs: debugLogs ?? this.debugLogs,
      safeModeEnabled: safeModeEnabled ?? this.safeModeEnabled,
      updates: updates ?? this.updates,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'language': language.name.toUpperCase(),
        'theme_mode': themeMode.name.toUpperCase(),
        'debug_logs': debugLogs,
        'safe_mode_enabled': safeModeEnabled,
        'updates': updates.toJson(),
      };
}

class PortRange {
  const PortRange({required this.start, required this.end});

  final int start;
  final int end;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'start': start,
        'end': end,
      };
}

class RuleMatch {
  const RuleMatch({
    required this.protocol,
    this.domainExact,
    this.domainWildcard,
    this.cidr,
    this.portRange,
  });

  final RuleProtocol protocol;
  final String? domainExact;
  final String? domainWildcard;
  final String? cidr;
  final PortRange? portRange;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'protocol': protocol.name.toUpperCase(),
        'domain_exact': domainExact,
        'domain_wildcard': domainWildcard,
        'cidr': cidr,
        'port_range': portRange?.toJson(),
      };
}

class RuleModel {
  const RuleModel({
    required this.id,
    required this.priority,
    required this.action,
    required this.match,
  });

  final String id;
  final int priority;
  final RuleAction action;
  final RuleMatch match;

  RuleModel copyWith({int? priority}) {
    return RuleModel(
      id: id,
      priority: priority ?? this.priority,
      action: action,
      match: match,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'priority': priority,
        'action': action.name.toUpperCase(),
        'match': match.toJson(),
      };
}

class FallbackPolicy {
  const FallbackPolicy({
    required this.aggressiveThreshold,
    required this.balancedThreshold,
    required this.timeWindowSeconds,
  });

  final int aggressiveThreshold;
  final int balancedThreshold;
  final int timeWindowSeconds;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'aggressive_threshold': aggressiveThreshold,
        'balanced_threshold': balancedThreshold,
        'time_window_seconds': timeWindowSeconds,
      };
}

class ProfileModel {
  const ProfileModel({
    required this.id,
    required this.name,
    required this.preset,
    required this.rules,
    required this.quicPolicy,
    required this.fallbackPolicy,
  });

  final String id;
  final String name;
  final ProfilePreset preset;
  final List<RuleModel> rules;
  final QuicPolicy quicPolicy;
  final FallbackPolicy fallbackPolicy;

  ProfileModel copyWith({
    String? name,
    ProfilePreset? preset,
    List<RuleModel>? rules,
    QuicPolicy? quicPolicy,
  }) {
    return ProfileModel(
      id: id,
      name: name ?? this.name,
      preset: preset ?? this.preset,
      rules: rules ?? this.rules,
      quicPolicy: quicPolicy ?? this.quicPolicy,
      fallbackPolicy: fallbackPolicy,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'name': name,
        'preset': preset.name[0].toUpperCase() + preset.name.substring(1),
        'custom_techniques': null,
        'rules': rules.map((rule) => rule.toJson()).toList(),
        'quic_policy': quicPolicy.name == 'allow' ? 'Allow' : 'BlockUdp443ForApply',
        'fallback_policy': fallbackPolicy.toJson(),
      };
}

class AppConfigModel {
  const AppConfigModel({
    required this.appVersion,
    required this.coreVersion,
    required this.configSchemaVersion,
    required this.featureFlags,
    required this.activeProfileId,
    required this.profiles,
    required this.settings,
  });

  final String appVersion;
  final String coreVersion;
  final int configSchemaVersion;
  final FeatureFlags featureFlags;
  final String activeProfileId;
  final List<ProfileModel> profiles;
  final AppSettings settings;

  ProfileModel get activeProfile =>
      profiles.firstWhere((profile) => profile.id == activeProfileId);

  AppConfigModel copyWith({
    String? activeProfileId,
    List<ProfileModel>? profiles,
    AppSettings? settings,
  }) {
    return AppConfigModel(
      appVersion: appVersion,
      coreVersion: coreVersion,
      configSchemaVersion: configSchemaVersion,
      featureFlags: featureFlags,
      activeProfileId: activeProfileId ?? this.activeProfileId,
      profiles: profiles ?? this.profiles,
      settings: settings ?? this.settings,
    );
  }

  String toPrettyJson() => const JsonEncoder.withIndent('  ').convert(toJson());

  Map<String, dynamic> toJson() => <String, dynamic>{
        'app_version': appVersion,
        'core_version': coreVersion,
        'config_schema_version': configSchemaVersion,
        'feature_flags': featureFlags.toJson(),
        'active_profile_id': activeProfileId,
        'profiles': profiles.map((profile) => profile.toJson()).toList(),
        'settings': settings.toJson(),
        'last_valid_config_snapshot': null,
      };

  factory AppConfigModel.sample() {
    return AppConfigModel(
      appVersion: '0.1.0',
      coreVersion: '0.1.0',
      configSchemaVersion: 1,
      featureFlags: const FeatureFlags(
        enableTlsSni: true,
        enableHttpHost: true,
        enableAggressiveSplit: true,
        enableSelfTestAb: true,
      ),
      activeProfileId: 'default',
      profiles: <ProfileModel>[
        ProfileModel(
          id: 'default',
          name: 'Balanced',
          preset: ProfilePreset.balanced,
          rules: const <RuleModel>[
            RuleModel(
              id: 'apply-youtube',
              priority: 10,
              action: RuleAction.apply,
              match: RuleMatch(
                protocol: RuleProtocol.tcp,
                domainWildcard: '*.youtube.com',
                portRange: PortRange(start: 443, end: 443),
              ),
            ),
            RuleModel(
              id: 'direct-banking',
              priority: 20,
              action: RuleAction.direct,
              match: RuleMatch(
                protocol: RuleProtocol.any,
                domainWildcard: '*.bank.example',
              ),
            ),
          ],
          quicPolicy: QuicPolicy.blockUdp443ForApply,
          fallbackPolicy: const FallbackPolicy(
            aggressiveThreshold: 3,
            balancedThreshold: 2,
            timeWindowSeconds: 300,
          ),
        ),
      ],
      settings: AppSettings(
        language: AppLanguage.ru,
        themeMode: AppThemePreference.system,
        debugLogs: false,
        safeModeEnabled: true,
        updates: const UpdateSettings(
          repoReference: 'owner/repo',
          apiBaseUrl: 'https://api.github.com',
          autoUpdate: true,
          wifiOnly: true,
          applyOnNextVpnStart: true,
        ),
      ),
    );
  }
}

class LogEntry {
  const LogEntry({
    required this.level,
    required this.message,
    required this.tag,
    required this.timestamp,
  });

  final String level;
  final String message;
  final String tag;
  final DateTime timestamp;
}

class SelfTestResult {
  const SelfTestResult({
    required this.target,
    required this.status,
    required this.latencyMs,
    required this.matchedRuleId,
    required this.effectivePreset,
  });

  final String target;
  final String status;
  final int latencyMs;
  final String matchedRuleId;
  final String effectivePreset;
}

