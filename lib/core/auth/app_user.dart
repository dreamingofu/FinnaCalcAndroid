import 'package:supabase_flutter/supabase_flutter.dart';

/// The app-facing user, mirroring the web's `type User = { id; email; name }`.
///
/// `name` resolves to `user_metadata.name` (trimmed); falls back to the email
/// local-part, then the empty string — exactly as `lib/auth.tsx`'s `toUser`.
class AppUser {
  const AppUser({required this.id, required this.email, required this.name});

  final String id;
  final String email;
  final String name;

  static AppUser? fromSession(Session? session) => fromSupabaseUser(session?.user);

  static AppUser? fromSupabaseUser(User? user) {
    if (user == null) return null;
    final email = user.email ?? '';
    final metaName = (user.userMetadata?['name'] as String?)?.trim();
    final name = (metaName != null && metaName.isNotEmpty)
        ? metaName
        : (email.isNotEmpty ? email.split('@').first : '');
    return AppUser(id: user.id, email: email, name: name);
  }

  @override
  bool operator ==(Object other) =>
      other is AppUser &&
      other.id == id &&
      other.email == email &&
      other.name == name;

  @override
  int get hashCode => Object.hash(id, email, name);
}
