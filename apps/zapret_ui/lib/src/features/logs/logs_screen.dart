import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/models.dart';
import '../../l10n/app_strings.dart';

class LogsScreen extends StatelessWidget {
  const LogsScreen({
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
        Text(strings['logs'], style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 16),
        Wrap(
          spacing: 8,
          children: const <Widget>[
            Chip(label: Text('INFO')),
            Chip(label: Text('WARN')),
            Chip(label: Text('ERROR')),
            Chip(label: Text('DEBUG')),
          ],
        ),
        const SizedBox(height: 16),
        ...controller.logs.map((LogEntry entry) {
          return Card(
            child: ListTile(
              leading: CircleAvatar(child: Text(entry.level.substring(0, 1))),
              title: Text(entry.message),
              subtitle: Text('${entry.tag} • ${entry.timestamp.toIso8601String()}'),
            ),
          );
        }),
      ],
    );
  }
}

