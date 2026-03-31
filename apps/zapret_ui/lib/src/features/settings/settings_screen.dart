import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/models.dart';
import '../../l10n/app_strings.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({
    super.key,
    required this.controller,
    required this.strings,
  });

  final AppController controller;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final AppSettings settings = controller.config.settings;
    return ListView(
      padding: const EdgeInsets.all(20),
      children: <Widget>[
        Text(strings['settings'], style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(strings['language']),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: AppLanguage.values
                      .map(
                        (AppLanguage value) => ChoiceChip(
                          label: Text(value.name.toUpperCase()),
                          selected: settings.language == value,
                          onSelected: (_) {
                            controller.updateLanguage(value);
                          },
                        ),
                      )
                      .toList(),
                ),
                const SizedBox(height: 24),
                Text(strings['theme']),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: AppThemePreference.values
                      .map(
                        (AppThemePreference value) => ChoiceChip(
                          label: Text(value.name.toUpperCase()),
                          selected: settings.themeMode == value,
                          onSelected: (_) {
                            controller.updateTheme(value);
                          },
                        ),
                      )
                      .toList(),
                ),
                const SizedBox(height: 24),
                SwitchListTile(
                  value: settings.debugLogs,
                  onChanged: (bool value) {
                    controller.updateDebugLogs(value);
                  },
                  title: const Text('Debug logs'),
                ),
                SwitchListTile(
                  value: settings.safeModeEnabled,
                  onChanged: (bool value) {
                    controller.updateSafeMode(value);
                  },
                  title: const Text('Safe mode behavior'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
