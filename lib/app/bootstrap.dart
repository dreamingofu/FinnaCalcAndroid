import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../core/auth/auth_service.dart';
import '../core/config/app_config.dart';

/// Loads runtime config, initialises Supabase (when configured), and returns a
/// started [AuthService]. Safe to run even with no `assets/.env` — the app then
/// behaves as signed-out.
Future<AuthService> bootstrap() async {
  await dotenv.load(fileName: 'assets/.env', isOptional: true);

  if (AppConfig.isSupabaseConfigured) {
    await Supabase.initialize(
      url: AppConfig.supabaseUrl,
      publishableKey: AppConfig.supabaseAnonKey,
    );
  }

  final auth = AuthService();
  auth.start();
  return auth;
}
