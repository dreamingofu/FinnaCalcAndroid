import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../core/auth/auth_service.dart';
import '../core/config/app_config.dart';
import '../core/networking/api_client.dart';

/// The long-lived services created at startup.
typedef AppServices = ({AuthService auth, ApiClient api});

/// Loads runtime config, initialises Supabase (when configured), and returns
/// the started services. Safe to run even with no `assets/.env` — the app then
/// behaves as signed-out.
Future<AppServices> bootstrap() async {
  await dotenv.load(fileName: 'assets/.env', isOptional: true);

  if (AppConfig.isSupabaseConfigured) {
    await Supabase.initialize(
      url: AppConfig.supabaseUrl,
      publishableKey: AppConfig.supabaseAnonKey,
    );
  }

  final api = await ApiClient.createPersistent();
  final auth = AuthService();
  auth.start();
  return (auth: auth, api: api);
}
