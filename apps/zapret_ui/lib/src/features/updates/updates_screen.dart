import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../l10n/app_strings.dart';

class UpdatesScreen extends StatelessWidget {
  const UpdatesScreen({
    super.key,
    required this.controller,
    required this.strings,
  });

  final AppController controller;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final updates = controller.config.settings.updates;
    return ListView(
      padding: const EdgeInsets.all(20),
      children: <Widget>[
        Text(strings['updates'], style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: <Widget>[
                TextFormField(
                  initialValue: updates.repoReference,
                  decoration: const InputDecoration(labelText: 'Repo reference'),
                  onChanged: (String value) {
                    controller.updateRepoReference(value);
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  initialValue: updates.apiBaseUrl,
                  decoration: const InputDecoration(labelText: 'GitHub API base URL'),
                  onChanged: (String value) {
                    controller.updateApiBaseUrl(value);
                  },
                ),
                const SizedBox(height: 12),
                Row(
                  children: <Widget>[
                    FilledButton(
                      onPressed: () => controller.markTokenTested(true),
                      child: const Text('Test token'),
                    ),
                    const SizedBox(width: 12),
                    Expanded(child: Text('${strings['tokenState']}: ${controller.tokenTestMessage}')),
                  ],
                ),
                SwitchListTile(
                  value: updates.autoUpdate,
                  onChanged: (bool value) {
                    controller.toggleUpdateSetting(autoUpdate: value);
                  },
                  title: const Text('Auto-update'),
                ),
                SwitchListTile(
                  value: updates.wifiOnly,
                  onChanged: (bool value) {
                    controller.toggleUpdateSetting(wifiOnly: value);
                  },
                  title: const Text('Wi-Fi only'),
                ),
                SwitchListTile(
                  value: updates.applyOnNextVpnStart,
                  onChanged: (bool value) {
                    controller.toggleUpdateSetting(applyOnNextVpnStart: value);
                  },
                  title: const Text('Apply on next VPN start'),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        ...controller.updateHistory.map(
          (String item) => Card(
            child: ListTile(
              leading: const Icon(Icons.history_outlined),
              title: Text(item),
            ),
          ),
        ),
      ],
    );
  }
}
