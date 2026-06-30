import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/design_system/design_system.dart';
import '../../core/networking/api_client.dart';
import 'chat_message.dart';
import 'chat_service.dart';

/// FinnaBot chat — streams `/api/chat` responses, appending chunks live.
class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();
  final List<ChatMessage> _messages = [
    ChatMessage(
      role: 'assistant',
      content:
          "Hi! I'm FinnaBot. Ask me about budgeting, investing, taxes, or "
          'which calculator to use.',
    ),
  ];
  StreamSubscription<String>? _sub;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _sub?.cancel();
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final text = _input.text.trim();
    if (text.isEmpty || _busy) return;

    final user = ChatMessage(role: 'user', content: text);
    final assistant = ChatMessage(role: 'assistant', content: '');
    setState(() {
      _messages.add(user);
      _error = null;
      _input.clear();
      _busy = true;
    });
    _scrollToBottom();

    // History to send (everything so far, excluding the empty placeholder).
    final history = List<ChatMessage>.from(_messages);
    setState(() => _messages.add(assistant));

    try {
      final stream = await context.read<ChatService>().send(history);
      _sub = stream.listen(
        (chunk) {
          setState(() => assistant.content += chunk);
          _scrollToBottom();
        },
        onError: (Object e) {
          setState(() {
            _busy = false;
            if (assistant.content.isEmpty) _messages.remove(assistant);
            _error = e is ApiException ? e.message : 'Something went wrong.';
          });
        },
        onDone: () {
          setState(() {
            _busy = false;
            if (assistant.content.trim().isEmpty) {
              _messages.remove(assistant);
              _error = 'No response received. Please try again.';
            }
          });
        },
        cancelOnError: true,
      );
    } on ApiException catch (e) {
      setState(() {
        _busy = false;
        _messages.remove(assistant);
        _error = e.message;
      });
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(
          _scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 150),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      backgroundColor: c.background,
      appBar: AppBar(
        backgroundColor: c.background,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        foregroundColor: c.foreground,
        shape: Border(bottom: BorderSide(color: c.border)),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.smart_toy_outlined, color: FCPalette.blue600, size: 20),
            const SizedBox(width: 8),
            const Text('FinnaBot',
                style: TextStyle(fontWeight: FCFontWeights.bold)),
          ],
        ),
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: ListView.builder(
                controller: _scroll,
                padding: const EdgeInsets.all(16),
                itemCount: _messages.length,
                itemBuilder: (context, i) => _Bubble(message: _messages[i]),
              ),
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                child: Text(_error!,
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: c.destructive)),
              ),
            _InputBar(controller: _input, busy: _busy, onSend: _send),
          ],
        ),
      ),
    );
  }
}

class _Bubble extends StatelessWidget {
  const _Bubble({required this.message});

  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final isUser = message.isUser;
    final showTyping = !isUser && message.content.isEmpty;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.82,
        ),
        decoration: BoxDecoration(
          color: isUser ? FCPalette.blue600 : c.muted,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(14),
            topRight: const Radius.circular(14),
            bottomLeft: Radius.circular(isUser ? 14 : 4),
            bottomRight: Radius.circular(isUser ? 4 : 14),
          ),
        ),
        child: showTyping
            ? SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor: AlwaysStoppedAnimation<Color>(c.mutedForeground),
                ),
              )
            : SelectableText(
                message.content,
                style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  height: 1.45,
                  color: isUser ? Colors.white : c.foreground,
                ),
              ),
      ),
    );
  }
}

class _InputBar extends StatelessWidget {
  const _InputBar({
    required this.controller,
    required this.busy,
    required this.onSend,
  });

  final TextEditingController controller;
  final bool busy;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
      decoration: BoxDecoration(
        color: c.background,
        border: Border(top: BorderSide(color: c.border)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: FCTextField(
              controller: controller,
              hintText: 'Ask FinnaBot…',
              maxLines: 4,
              minLines: 1,
              textInputAction: TextInputAction.send,
              onSubmitted: (_) => onSend(),
            ),
          ),
          const SizedBox(width: 8),
          FCButton(
            size: FCButtonSize.icon,
            icon: const Icon(Icons.send),
            loading: busy,
            onPressed: busy ? null : onSend,
          ),
        ],
      ),
    );
  }
}
