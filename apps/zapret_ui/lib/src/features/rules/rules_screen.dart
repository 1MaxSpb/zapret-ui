import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/models.dart';

class RulesScreen extends StatelessWidget {
  const RulesScreen({
    super.key,
    required this.controller,
  });

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final List<RuleModel> rules = controller.config.activeProfile.rules;
    return ReorderableListView.builder(
      padding: const EdgeInsets.all(20),
      itemCount: rules.length,
      onReorder: (int oldIndex, int newIndex) {
        controller.reorderRules(oldIndex, newIndex);
      },
      itemBuilder: (BuildContext context, int index) {
        final RuleModel rule = rules[index];
        return Card(
          key: ValueKey<String>(rule.id),
          child: ListTile(
            title: Text(rule.id),
            subtitle: Text(
              '${rule.action.name.toUpperCase()} • ${rule.match.protocol.name.toUpperCase()} • '
              '${rule.match.domainWildcard ?? rule.match.domainExact ?? rule.match.cidr ?? 'any'}',
            ),
            trailing: Text('#${rule.priority}'),
          ),
        );
      },
    );
  }
}
