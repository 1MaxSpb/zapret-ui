import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/models.dart';
import '../../l10n/app_strings.dart';

class ProfilesScreen extends StatelessWidget {
  const ProfilesScreen({
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
            Expanded(child: Text(strings['profiles'], style: Theme.of(context).textTheme.headlineSmall)),
            FilledButton.icon(
              onPressed: controller.cloneActiveProfile,
              icon: const Icon(Icons.copy_outlined),
              label: Text(strings['cloneProfile']),
            ),
          ],
        ),
        const SizedBox(height: 16),
        ...controller.config.profiles.map(
          (ProfileModel profile) => Card(
            child: ListTile(
              title: Text(profile.name),
              subtitle: Text('${profile.preset.name} • ${profile.rules.length} rules'),
              trailing: controller.config.activeProfileId == profile.id
                  ? const Icon(Icons.check_circle_outline)
                  : null,
              onTap: () {
                controller.selectProfile(profile.id);
              },
            ),
          ),
        ),
      ],
    );
  }
}
