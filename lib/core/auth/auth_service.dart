import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../config/app_config.dart';
import 'app_user.dart';

/// A user-presentable auth error.
class AuthFailure implements Exception {
  const AuthFailure(this.message);
  final String message;
  @override
  String toString() => message;
}

/// Result of [AuthService.signUp]; mirrors the web's `{ needsConfirmation }`.
class SignUpResult {
  const SignUpResult({required this.needsConfirmation});
  final bool needsConfirmation;
}

/// Wraps `supabase_flutter`, exposing the same auth surface as the web's
/// `lib/auth.tsx` (`useAuth`): email/password sign-in & sign-up, native Google
/// sign-in (via `signInWithIdToken`), sign-out, and live session state.
class AuthService extends ChangeNotifier {
  AuthService({bool? configured})
      : configured = configured ?? AppConfig.isSupabaseConfigured;

  /// Whether Supabase credentials are present. When false the app behaves as a
  /// signed-out app instead of crashing (matching `isSupabaseConfigured`).
  final bool configured;

  AppUser? _user;
  bool _loading = true;
  bool _googleInitialized = false;
  StreamSubscription<AuthState>? _authSub;

  AppUser? get user => _user;
  bool get loading => _loading;
  bool get isSignedIn => _user != null;

  SupabaseClient get _client => Supabase.instance.client;

  /// Restores any persisted session and starts listening for changes.
  void start() {
    if (!configured) {
      _loading = false;
      notifyListeners();
      return;
    }
    _user = AppUser.fromSession(_client.auth.currentSession);
    _loading = false;
    notifyListeners();

    _authSub = _client.auth.onAuthStateChange.listen((state) {
      _user = AppUser.fromSession(state.session);
      notifyListeners();
    });
  }

  Future<void> signIn(String email, String password) async {
    _ensureConfigured();
    try {
      await _client.auth.signInWithPassword(
        email: email.trim().toLowerCase(),
        password: password,
      );
    } on AuthException catch (e) {
      throw AuthFailure(e.message);
    }
  }

  Future<SignUpResult> signUp(
      String email, String password, String name) async {
    _ensureConfigured();
    try {
      final res = await _client.auth.signUp(
        email: email.trim().toLowerCase(),
        password: password,
        data: {'name': name.trim()},
      );
      // When email confirmation is required, Supabase returns a user but no
      // session.
      return SignUpResult(needsConfirmation: res.session == null);
    } on AuthException catch (e) {
      throw AuthFailure(e.message);
    }
  }

  /// Native Google sign-in: obtain a Google ID token, then exchange it with
  /// Supabase via `signInWithIdToken`. Returns false if the user cancelled.
  Future<bool> signInWithGoogle() async {
    _ensureConfigured();
    if (!AppConfig.isGoogleConfigured) {
      throw const AuthFailure(
          'Google sign-in is not configured (missing GOOGLE_WEB_CLIENT_ID).');
    }

    final google = GoogleSignIn.instance;
    try {
      if (!_googleInitialized) {
        await google.initialize(serverClientId: AppConfig.googleWebClientId);
        _googleInitialized = true;
      }
      final account = await google.authenticate();
      final idToken = account.authentication.idToken;
      if (idToken == null) {
        throw const AuthFailure('Google did not return an ID token.');
      }
      await _client.auth.signInWithIdToken(
        provider: OAuthProvider.google,
        idToken: idToken,
      );
      return true;
    } on GoogleSignInException catch (e) {
      if (e.code == GoogleSignInExceptionCode.canceled) return false;
      throw AuthFailure('Google sign-in failed: ${e.description ?? e.code.name}');
    } on AuthException catch (e) {
      throw AuthFailure(e.message);
    }
  }

  Future<void> signOut() async {
    if (!configured) return;
    await _client.auth.signOut();
    _user = null;
    notifyListeners();
  }

  void _ensureConfigured() {
    if (!configured) {
      throw const AuthFailure(
          'Authentication is not configured. Add SUPABASE_URL and '
          'SUPABASE_ANON_KEY to assets/.env.');
    }
  }

  @override
  void dispose() {
    _authSub?.cancel();
    super.dispose();
  }
}
