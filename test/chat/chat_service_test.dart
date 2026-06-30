import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/networking/api_client.dart';
import 'package:finnacalc/features/chat/chat_message.dart';
import 'package:finnacalc/features/chat/chat_service.dart';

class _FakeApi extends ApiClient {
  Object? lastBody;
  String? lastPath;

  @override
  Future<Stream<String>> streamText(String path,
      {Object? body, String method = 'POST'}) async {
    lastPath = path;
    lastBody = body;
    return Stream.fromIterable(['Hello', ' there', '!']);
  }
}

void main() {
  test('ChatMessage serializes role/content', () {
    final m = ChatMessage(role: 'user', content: 'hi');
    expect(m.isUser, isTrue);
    expect(m.toJson(), {'role': 'user', 'content': 'hi'});
  });

  test('ChatService posts messages and streams the reply', () async {
    final api = _FakeApi();
    final service = ChatService(api);
    final stream = await service.send([
      ChatMessage(role: 'user', content: 'What is a Roth IRA?'),
    ]);
    final reply = (await stream.toList()).join();
    expect(reply, 'Hello there!');
    expect(api.lastPath, '/api/chat');
    final body = api.lastBody as Map;
    final messages = body['messages'] as List;
    expect(messages.first, {'role': 'user', 'content': 'What is a Roth IRA?'});
  });

  test('ChatService drops non user/assistant roles', () async {
    final api = _FakeApi();
    await ChatService(api).send([
      ChatMessage(role: 'system', content: 'ignore me'),
      ChatMessage(role: 'assistant', content: 'prior'),
    ]);
    final messages = (api.lastBody as Map)['messages'] as List;
    expect(messages, hasLength(1));
    expect(messages.first, {'role': 'assistant', 'content': 'prior'});
  });
}
