import 'models.dart';

abstract class ConfigRepository {
  Future<AppConfigModel> load();
  Future<void> save(AppConfigModel config);
}

class MemoryConfigRepository implements ConfigRepository {
  AppConfigModel _config = AppConfigModel.sample();

  @override
  Future<AppConfigModel> load() async => _config;

  @override
  Future<void> save(AppConfigModel config) async {
    _config = config;
  }
}
