/// A single chat turn. `role` is `'user'` or `'assistant'` (matching the API).
class ChatMessage {
  ChatMessage({required this.role, required this.content});

  final String role;
  String content;

  bool get isUser => role == 'user';

  Map<String, String> toJson() => {'role': role, 'content': content};
}
