import 'package:flutter_test/flutter_test.dart';
import 'package:zapret_ui/src/core/app_controller.dart';
import 'package:zapret_ui/src/core/models.dart';

void main() {
  test('controller initializes sample config', () async {
    final AppController controller = AppController();

    await controller.initialize();

    expect(controller.initialized, isTrue);
    expect(controller.config.configSchemaVersion, 1);
    expect(controller.config.activeProfileId, 'default');
  });

  test('controller reorders active rules', () async {
    final AppController controller = AppController();
    await controller.initialize();

    await controller.cloneActiveProfile();
    await controller.reorderRules(0, 1);

    expect(controller.config.activeProfile.rules.first.priority, 10);
    expect(controller.config.activeProfile.rules.last.priority, 20);
  });

  test('onboarding applies language and theme', () async {
    final AppController controller = AppController();
    await controller.initialize();

    await controller.completeOnboarding(
      language: AppLanguage.en,
      theme: AppThemePreference.dark,
    );

    expect(controller.onboardingComplete, isTrue);
    expect(controller.config.settings.language, AppLanguage.en);
    expect(controller.config.settings.themeMode, AppThemePreference.dark);
  });
}
