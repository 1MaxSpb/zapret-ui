import '../core/models.dart';

class AppStrings {
  const AppStrings(this.language);

  final AppLanguage language;

  static const Map<AppLanguage, Map<String, String>> _values = <AppLanguage, Map<String, String>>{
    AppLanguage.en: <String, String>{
      'appTitle': 'zapret-ui',
      'home': 'Home',
      'profiles': 'Profiles',
      'rules': 'Rules',
      'logs': 'Logs',
      'diagnostics': 'Diagnostics',
      'updates': 'Core Updates',
      'settings': 'Settings',
      'start': 'Start VPN',
      'stop': 'Stop VPN',
      'onboardingTitle': 'Choose language and theme',
      'continue': 'Continue',
      'safeMode': 'Safe mode is active',
      'updateReady': 'Core update is staged for next start',
      'theme': 'Theme',
      'language': 'Language',
      'exportConfig': 'Export JSON Preview',
      'runSelfTest': 'Run self-test',
      'cloneProfile': 'Clone active profile',
      'tokenState': 'Token test',
    },
    AppLanguage.ru: <String, String>{
      'appTitle': 'zapret-ui',
      'home': 'Главная',
      'profiles': 'Профили',
      'rules': 'Правила',
      'logs': 'Логи',
      'diagnostics': 'Диагностика',
      'updates': 'Обновления Core',
      'settings': 'Настройки',
      'start': 'Запустить VPN',
      'stop': 'Остановить VPN',
      'onboardingTitle': 'Выберите язык и тему',
      'continue': 'Продолжить',
      'safeMode': 'Включён safe mode',
      'updateReady': 'Обновление Core применится при следующем старте',
      'theme': 'Тема',
      'language': 'Язык',
      'exportConfig': 'Показать JSON конфиг',
      'runSelfTest': 'Запустить self-test',
      'cloneProfile': 'Клонировать активный профиль',
      'tokenState': 'Проверка токена',
    },
  };

  String operator [](String key) => _values[language]?[key] ?? key;
}
