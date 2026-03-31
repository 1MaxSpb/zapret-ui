import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/models.dart';
import '../../l10n/app_strings.dart';

class DiagnosticsScreen extends StatelessWidget {
  const DiagnosticsScreen({
    super.key,
    required this.controller,
    required this.strings,
  });

  final AppController controller;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: <Widget>[
        Row(
          children: <Widget>[
            Expanded(child: Text(strings['diagnostics'], style: Theme.of(context).textTheme.headlineSmall)),
            FilledButton.icon(
              onPressed: controller.runSelfTest,
              icon: const Icon(Icons.play_arrow_outlined),
              label: Text(strings['runSelfTest']),
            ),
          ],
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(strings['exportConfig'], style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 12),
                SelectableText(controller.exportConfigJson()),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        ...controller.selfTests.map(
          (SelfTestResult result) => Card(
            child: ListTile(
              leading: Icon(result.status == 'OK' ? Icons.check_circle_outline : Icons.error_outline),
              title: Text(result.target),
              subtitle: Text(
                '${result.status} • ${result.latencyMs}ms • ${result.matchedRuleId} • ${result.effectivePreset}',
              ),
            ),
          ),
        ),
      ],
    );
  }
}

