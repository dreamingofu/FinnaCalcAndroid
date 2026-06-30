import '../../core/networking/api_client.dart';
import '../../core/networking/api_endpoints.dart';
import 'chat_message.dart';

/// Talks to `/api/chat` (FinnaBot). Sends the conversation and returns the
/// assistant's reply as a plain UTF-8 text stream (no SSE framing), matching
/// the web.
class ChatService {
  ChatService(this._api);

  final ApiClient _api;

  Future<Stream<String>> send(List<ChatMessage> messages) {
    return _api.streamText(
      ApiEndpoints.chat,
      body: {
        'messages': messages
            .where((m) => m.role == 'user' || m.role == 'assistant')
            .map((m) => m.toJson())
            .toList(),
      },
    );
  }
}
