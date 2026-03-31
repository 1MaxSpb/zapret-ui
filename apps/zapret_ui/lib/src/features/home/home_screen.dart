import 'package:flutter/material.dart';

import '../../core/app_controller.dart';
import '../../core/models.dart';
import '../../l10n/app_strings.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({
    super.key,
    required this.controller,
    required this.strings,
  });

  final AppController controller;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final ProfileModel activeProfile = controller.config.activeProfile;
    return ListView(
      padding: const EdgeInsets.all(20),
      children: <Widget>[
        Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(28),
            gradient: const LinearGradient(
              colors: <Color>[Color(0xFF0B5D7A), Color(0xFF62B6CB)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                strings['appTitle'],
                style: Theme.of(context).textTheme.headlineMedium?.copyWith(color: Colors.white),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: <Widget>[
                  _StatusPill(label: 'VPN: ${controller.vpnStatus.name.toUpperCase()}'),
                  _StatusPill(label: 'Profile: ${activeProfile.name}'),
                  _StatusPill(label: 'QUIC: ${activeProfile.quicPolicy.name}'),
                ],
              ),
              const SizedBox(height: 24),
              FilledButton.tonal(
                onPressed: controller.toggleVpn,
                style: FilledButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: const Color(0xFF0B5D7A),
                ),
                child: Text(
                  controller.vpnStatus == VpnStatus.on ? strings['stop'] : strings['start'],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (controller.safeModeBannerVisible)
          _BannerCard(
            icon: Icons.shield_outlined,
            text: strings['safeMode'],
            onDismiss: controller.dismissBanners,
          ),
        if (controller.updateBannerVisible)
          _BannerCard(
            icon: Icons.system_update_alt_outlined,
            text: strings['updateReady'],
            onDismiss: controller.dismissBanners,
          ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text('Config schema v${controller.config.configSchemaVersion}', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                Text('App ${controller.config.appVersion} / Core ${controller.config.coreVersion}'),
                const SizedBox(height: 16),
                Text('Active rules: ${activeProfile.rules.length}'),
                Text('Fallback window: ${activeProfile.fallbackPolicy.timeWindowSeconds}s'),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.18),
        borderRadius: BorderRadius.circular(100),
      ),
      child: Text(label, style: const TextStyle(color: Colors.white)),
    );
  }
}

class _BannerCard extends StatelessWidget {
  const _BannerCard({
    required this.icon,
    required this.text,
    required this.onDismiss,
  });

  final IconData icon;
  final String text;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: Icon(icon),
        title: Text(text),
        trailing: IconButton(
          onPressed: onDismiss,
          icon: const Icon(Icons.close),
        ),
      ),
    );
  }
}
