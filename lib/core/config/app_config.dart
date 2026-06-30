import 'package:flutter_dotenv/flutter_dotenv.dart';

/// Runtime configuration, read from the bundled `assets/.env`.
///
/// Only PUBLIC client values live here (Supabase URL + anon key, the Google
/// *web* OAuth client id, and the backend base URL). Server secrets stay on
/// the Next.js backend that serves the `/api/*` routes.
class AppConfig {
  const AppConfig._();

  /// Base URL of the existing Next.js backend. The web app used same-origin
  /// relative paths; the mobile client needs an absolute base.
  static String get apiBaseUrl =>
      _get('API_BASE_URL', fallback: 'https://finnacalc.com');

  /// Supabase project URL (`NEXT_PUBLIC_SUPABASE_URL` on the web).
  static String get supabaseUrl => _get('SUPABASE_URL');

  /// Supabase anon/publishable key (`NEXT_PUBLIC_SUPABASE_ANON_KEY`).
  static String get supabaseAnonKey => _get('SUPABASE_ANON_KEY');

  /// Google OAuth *web* client id — passed as `serverClientId` for native
  /// Google sign-in so Supabase accepts the resulting ID token.
  static String get googleWebClientId => _get('GOOGLE_WEB_CLIENT_ID');

  /// Mirrors the web's `isSupabaseConfigured`: both values must be present.
  /// When false the app behaves as signed-out instead of crashing.
  static bool get isSupabaseConfigured =>
      supabaseUrl.isNotEmpty && supabaseAnonKey.isNotEmpty;

  /// Whether native Google sign-in can be offered.
  static bool get isGoogleConfigured => googleWebClientId.isNotEmpty;

  static String _get(String key, {String fallback = ''}) {
    try {
      final value = dotenv.maybeGet(key)?.trim();
      return (value == null || value.isEmpty) ? fallback : value;
    } catch (_) {
      // dotenv not initialized (e.g. assets/.env missing or in tests).
      return fallback;
    }
  }
}
