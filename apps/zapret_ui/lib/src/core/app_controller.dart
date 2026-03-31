import 'package:flutter/foundation.dart';

import 'config_repository.dart';
import 'models.dart';

class AppController extends ChangeNotifier {
  AppController({ConfigRepository? repository})
      : _repository = repository ?? MemoryConfigRepository();

  final ConfigRepository _repository;

  AppConfigModel? _config;
  bool _initialized = false;
  bool _onboardingComplete = false;
  int _selectedTabIndex = 0;
  VpnStatus _vpnStatus = VpnStatus.off;
  bool _safeModeBannerVisible = false;
  bool _updateBannerVisible = true;
  String _tokenTestMessage = 'Not tested';

  final List<LogEntry> _logs = <LogEntry>[
    LogEntry(
      level: 'INFO',
      message: 'Core schema v1 loaded',
      tag: 'core',
      timestamp: DateTime.now().subtract(const Duration(minutes: 8)),
    ),
    LogEntry(
      level: 'WARN',
      message: 'QUIC blocked for APPLY rule *.youtube.com',
      tag: 'udp',
      timestamp: DateTime.now().subtract(const Duration(minutes: 3)),
    ),
    LogEntry(
      level: 'INFO',
      message: 'Fallback downgrade Aggressive -> Balanced',
      tag: 'fallback',
      timestamp: DateTime.now().subtract(const Duration(minutes: 1)),
    ),
  ];

  List<SelfTestResult> _selfTests = const <SelfTestResult>[
    SelfTestResult(
      target: 'https://example.com',
      status: 'OK',
      latencyMs: 126,
      matchedRuleId: 'apply-youtube',
      effectivePreset: 'Balanced',
    ),
  ];

  final List<String> _updateHistory = <String>[
    '0.1.0 baseline embedded',
    '0.1.1 staged for next VPN start',
  ];

  bool get initialized => _initialized;
  bool get onboardingComplete => _onboardingComplete;
  int get selectedTabIndex => _selectedTabIndex;
  VpnStatus get vpnStatus => _vpnStatus;
  bool get safeModeBannerVisible => _safeModeBannerVisible;
  bool get updateBannerVisible => _updateBannerVisible;
  String get tokenTestMessage => _tokenTestMessage;
  AppConfigModel get config => _config ?? AppConfigModel.sample();
  List<LogEntry> get logs => List<LogEntry>.unmodifiable(_logs);
  List<SelfTestResult> get selfTests => List<SelfTestResult>.unmodifiable(_selfTests);
  List<String> get updateHistory => List<String>.unmodifiable(_updateHistory);

  Future<void> initialize() async {
    _config = await _repository.load();
    _initialized = true;
    notifyListeners();
  }

  Future<void> completeOnboarding({
    required AppLanguage language,
    required AppThemePreference theme,
  }) async {
    _config = config.copyWith(
      settings: config.settings.copyWith(
        language: language,
        themeMode: theme,
      ),
    );
    _onboardingComplete = true;
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> updateLanguage(AppLanguage language) async {
    _config = config.copyWith(settings: config.settings.copyWith(language: language));
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> updateTheme(AppThemePreference theme) async {
    _config = config.copyWith(settings: config.settings.copyWith(themeMode: theme));
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> updateDebugLogs(bool value) async {
    _config = config.copyWith(settings: config.settings.copyWith(debugLogs: value));
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> updateSafeMode(bool value) async {
    _config = config.copyWith(settings: config.settings.copyWith(safeModeEnabled: value));
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> updateRepoReference(String value) async {
    _config = config.copyWith(
      settings: config.settings.copyWith(
        updates: config.settings.updates.copyWith(repoReference: value),
      ),
    );
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> updateApiBaseUrl(String value) async {
    _config = config.copyWith(
      settings: config.settings.copyWith(
        updates: config.settings.updates.copyWith(apiBaseUrl: value),
      ),
    );
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> toggleUpdateSetting({
    bool? autoUpdate,
    bool? wifiOnly,
    bool? applyOnNextVpnStart,
  }) async {
    _config = config.copyWith(
      settings: config.settings.copyWith(
        updates: config.settings.updates.copyWith(
          autoUpdate: autoUpdate,
          wifiOnly: wifiOnly,
          applyOnNextVpnStart: applyOnNextVpnStart,
        ),
      ),
    );
    await _repository.save(config);
    notifyListeners();
  }

  void setSelectedTab(int index) {
    _selectedTabIndex = index;
    notifyListeners();
  }

  void toggleVpn() {
    _vpnStatus = _vpnStatus == VpnStatus.on ? VpnStatus.off : VpnStatus.on;
    notifyListeners();
  }

  void activateSafeModeBanner() {
    _vpnStatus = VpnStatus.safeMode;
    _safeModeBannerVisible = true;
    notifyListeners();
  }

  void dismissBanners() {
    _safeModeBannerVisible = false;
    _updateBannerVisible = false;
    notifyListeners();
  }

  Future<void> selectProfile(String profileId) async {
    _config = config.copyWith(activeProfileId: profileId);
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> cloneActiveProfile() async {
    final ProfileModel active = config.activeProfile;
    final String cloneId = '${active.id}-copy-${config.profiles.length + 1}';
    final List<ProfileModel> profiles = <ProfileModel>[
      ...config.profiles,
      ProfileModel(
        id: cloneId,
        name: '${active.name} Copy',
        preset: active.preset,
        rules: active.rules,
        quicPolicy: active.quicPolicy,
        fallbackPolicy: active.fallbackPolicy,
      ),
    ];
    _config = config.copyWith(profiles: profiles, activeProfileId: cloneId);
    await _repository.save(config);
    notifyListeners();
  }

  Future<void> reorderRules(int oldIndex, int newIndex) async {
    final List<RuleModel> rules = <RuleModel>[...config.activeProfile.rules];
    if (oldIndex < newIndex) {
      newIndex -= 1;
    }
    final RuleModel rule = rules.removeAt(oldIndex);
    rules.insert(newIndex, rule);
    final List<RuleModel> prioritized = rules
        .asMap()
        .entries
        .map((entry) => entry.value.copyWith(priority: (entry.key + 1) * 10))
        .toList();
    final List<ProfileModel> profiles = config.profiles
        .map(
          (profile) => profile.id == config.activeProfileId
              ? profile.copyWith(rules: prioritized)
              : profile,
        )
        .toList();
    _config = config.copyWith(profiles: profiles);
    await _repository.save(config);
    notifyListeners();
  }

  void runSelfTest() {
    _selfTests = <SelfTestResult>[
      const SelfTestResult(
        target: 'https://youtube.com',
        status: 'OK',
        latencyMs: 188,
        matchedRuleId: 'apply-youtube',
        effectivePreset: 'Balanced',
      ),
      const SelfTestResult(
        target: 'https://bank.example',
        status: 'DIRECT',
        latencyMs: 74,
        matchedRuleId: 'direct-banking',
        effectivePreset: 'Balanced',
      ),
    ];
    notifyListeners();
  }

  void markTokenTested(bool ok) {
    _tokenTestMessage = ok ? 'Token accepted' : 'Authorization failed';
    notifyListeners();
  }

  String exportConfigJson() => config.toPrettyJson();
}

