import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/app_controller.dart';
import 'core/models.dart';
import 'features/diagnostics/diagnostics_screen.dart';
import 'features/home/home_screen.dart';
import 'features/logs/logs_screen.dart';
import 'features/onboarding/onboarding_screen.dart';
import 'features/profiles/profiles_screen.dart';
import 'features/rules/rules_screen.dart';
import 'features/settings/settings_screen.dart';
import 'features/updates/updates_screen.dart';
import 'l10n/app_strings.dart';

class ZapretApp extends StatefulWidget {
  const ZapretApp({super.key});

  @override
  State<ZapretApp> createState() => _ZapretAppState();
}

class _ZapretAppState extends State<ZapretApp> {
  final AppController controller = AppController();

  @override
  void initState() {
    super.initState();
    controller.initialize();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (BuildContext context, Widget? child) {
        final AppLanguage language = controller.config.settings.language;
        final AppStrings strings = AppStrings(language);
        return MaterialApp(
          debugShowCheckedModeBanner: false,
          title: strings['appTitle'],
          locale: Locale(language.name),
          supportedLocales: const <Locale>[Locale('ru'), Locale('en')],
          localizationsDelegates: GlobalMaterialLocalizations.delegates,
          themeMode: _mapThemeMode(controller.config.settings.themeMode),
          theme: _buildTheme(Brightness.light),
          darkTheme: _buildTheme(Brightness.dark),
          home: !controller.initialized
              ? const Scaffold(body: Center(child: CircularProgressIndicator()))
              : controller.onboardingComplete
                  ? AppShell(controller: controller, strings: strings)
                  : OnboardingScreen(controller: controller, strings: strings),
        );
      },
    );
  }

  ThemeMode _mapThemeMode(AppThemePreference preference) {
    switch (preference) {
      case AppThemePreference.light:
        return ThemeMode.light;
      case AppThemePreference.dark:
        return ThemeMode.dark;
      case AppThemePreference.system:
        return ThemeMode.system;
    }
  }

  ThemeData _buildTheme(Brightness brightness) {
    const Color seed = Color(0xFF0B5D7A);
    final ColorScheme scheme = ColorScheme.fromSeed(
      seedColor: seed,
      brightness: brightness,
    );
    return ThemeData(
      colorScheme: scheme,
      useMaterial3: true,
      scaffoldBackgroundColor:
          brightness == Brightness.dark ? const Color(0xFF09121A) : const Color(0xFFF4F7F8),
      cardTheme: CardThemeData(
        elevation: 0,
        color: brightness == Brightness.dark ? const Color(0xFF11212C) : Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      ),
    );
  }
}

class AppShell extends StatelessWidget {
  const AppShell({
    super.key,
    required this.controller,
    required this.strings,
  });

  final AppController controller;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final List<Widget> screens = <Widget>[
      HomeScreen(controller: controller, strings: strings),
      ProfilesScreen(controller: controller, strings: strings),
      RulesScreen(controller: controller),
      LogsScreen(controller: controller, strings: strings),
      DiagnosticsScreen(controller: controller, strings: strings),
      UpdatesScreen(controller: controller, strings: strings),
      SettingsScreen(controller: controller, strings: strings),
    ];

    return Scaffold(
      body: SafeArea(
        child: IndexedStack(
          index: controller.selectedTabIndex,
          children: screens,
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: controller.selectedTabIndex,
        onDestinationSelected: controller.setSelectedTab,
        destinations: <NavigationDestination>[
          NavigationDestination(icon: const Icon(Icons.home_outlined), label: strings['home']),
          NavigationDestination(icon: const Icon(Icons.layers_outlined), label: strings['profiles']),
          NavigationDestination(icon: const Icon(Icons.rule_folder_outlined), label: strings['rules']),
          NavigationDestination(icon: const Icon(Icons.terminal_outlined), label: strings['logs']),
          NavigationDestination(icon: const Icon(Icons.health_and_safety_outlined), label: strings['diagnostics']),
          NavigationDestination(icon: const Icon(Icons.system_update_alt_outlined), label: strings['updates']),
          NavigationDestination(icon: const Icon(Icons.settings_outlined), label: strings['settings']),
        ],
      ),
    );
  }
}
