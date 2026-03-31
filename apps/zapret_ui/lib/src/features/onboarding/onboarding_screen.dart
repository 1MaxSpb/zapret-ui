import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/models.dart';
import '../../l10n/app_strings.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({
    super.key,
    required this.controller,
    required this.strings,
  });

  final AppController controller;
  final AppStrings strings;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  AppLanguage language = AppLanguage.ru;
  AppThemePreference theme = AppThemePreference.system;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: <Color>[Color(0xFF0B5D7A), Color(0xFF9ED8DB)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 560),
            child: Card(
              margin: const EdgeInsets.all(24),
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(widget.strings['onboardingTitle'], style: Theme.of(context).textTheme.headlineSmall),
                    const SizedBox(height: 24),
                    Text(widget.strings['language']),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      children: <Widget>[
                        ChoiceChip(
                          label: const Text('RU'),
                          selected: language == AppLanguage.ru,
                          onSelected: (_) => setState(() => language = AppLanguage.ru),
                        ),
                        ChoiceChip(
                          label: const Text('EN'),
                          selected: language == AppLanguage.en,
                          onSelected: (_) => setState(() => language = AppLanguage.en),
                        ),
                      ],
                    ),
                    const SizedBox(height: 24),
                    Text(widget.strings['theme']),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      children: AppThemePreference.values
                          .map(
                            (AppThemePreference entry) => ChoiceChip(
                              label: Text(entry.name.toUpperCase()),
                              selected: theme == entry,
                              onSelected: (_) => setState(() => theme = entry),
                            ),
                          )
                          .toList(),
                    ),
                    const SizedBox(height: 32),
                    FilledButton(
                      onPressed: () => widget.controller.completeOnboarding(
                        language: language,
                        theme: theme,
                      ),
                      child: Text(widget.strings['continue']),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

