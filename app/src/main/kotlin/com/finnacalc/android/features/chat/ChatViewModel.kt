//
// ChatViewModel.kt
//
// Port of the view-model half of iOS Features/Chat/FinnaBotView.swift.
// Streams /api/chat (plain UTF-8 text) via ApiClient.postTextStream. The
// instance lives at the app shell so a conversation survives closing and
// reopening the panel.
//

package com.finnacalc.android.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finnacalc.android.core.networking.ApiClient
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private const val WELCOME_ID = "welcome"

private const val WELCOME =
    "Hi! I'm FinnaBot. Ask me about budgeting, investing, taxes, or any of the calculators in the app."

enum class ChatRole { User, Assistant }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    /**
     * Set on replies to a question that reads like it's asking what to do with
     * money, so the answer carries a fine-print reminder that it came from a
     * model and isn't advice.
     */
    val needsAdviceDisclaimer: Boolean = false,
)

@Serializable
private data class ChatTurn(val role: String, val content: String)

@Serializable
private data class ChatRequest(val messages: List<ChatTurn>)

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow(
        listOf(ChatMessage(WELCOME_ID, ChatRole.Assistant, WELCOME))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Invisible context prepended to the first user turn of the payload (never
     * shown in the transcript): the analysis chat uses it to hand the model the
     * portfolio mix without the user pasting it.
     */
    var contextPrefix: String? = null

    private var streamJob: Job? = null

    fun setInput(text: String) {
        _input.value = text
    }

    fun send() {
        val trimmed = _input.value.trim()
        if (trimmed.isEmpty() || _isLoading.value) return

        _error.value = null
        _messages.value = _messages.value + ChatMessage(UUID.randomUUID().toString(), ChatRole.User, trimmed)
        _input.value = ""
        _isLoading.value = true

        // Drop the welcome message so the model conversation starts with a user turn.
        val payload = _messages.value
            .filter { it.id != WELCOME_ID }
            .map { ChatTurn(if (it.role == ChatRole.User) "user" else "assistant", it.content) }
            .toMutableList()
        val prefix = contextPrefix
        if (prefix != null) {
            val first = payload.indexOfFirst { it.role == "user" }
            if (first >= 0) {
                payload[first] = ChatTurn("user", prefix + "\n\n" + payload[first].content)
            }
        }

        val assistantId = UUID.randomUUID().toString()
        val disclaim = seeksAdvice(trimmed)
        val body = ApiClient.shared.json.encodeToString(ChatRequest(payload))

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var appended = false
            var failure: String? = null
            ApiClient.shared.postTextStream("/api/chat", body)
                .catch { e ->
                    failure = e.message ?: "Something went wrong."
                }
                .collect { text ->
                    if (!appended) {
                        _messages.value = _messages.value +
                            ChatMessage(assistantId, ChatRole.Assistant, "", disclaim)
                        appended = true
                    }
                    _messages.value = _messages.value.map {
                        if (it.id == assistantId) it.copy(content = text) else it
                    }
                }

            if (failure != null) {
                _messages.value = _messages.value.filterNot { it.id == assistantId }
                _error.value = failure
            } else {
                val streamed = _messages.value.firstOrNull { it.id == assistantId }?.content ?: ""
                if (streamed.isBlank()) {
                    _messages.value = _messages.value.filterNot { it.id == assistantId }
                    _error.value = "No response received. Please try again."
                }
            }
            _isLoading.value = false
        }
    }

    companion object {
        /**
         * Phrases that make a question a request for a recommendation rather
         * than an explanation. Deliberately broad — over-disclaiming is
         * harmless, and a missed one isn't.
         */
        private val ADVICE_CUES = listOf(
            "should i", "should we", "should my", "advice", "advise", "recommend",
            "what should", "which should", "worth it", "is it worth", "better to",
            "which is better", "is it smart", "good idea", "do you think i",
            "invest in", "buy", "sell", "pick", "portfolio", "allocate", "allocation",
            "how much should", "can i afford", "pay off", "payoff", "refinance",
            "roth", "401k", "ira", "retire", "best stock", "what stock", "which stock",
        )

        fun seeksAdvice(text: String): Boolean {
            val t = text.lowercase()
            return ADVICE_CUES.any { t.contains(it) }
        }

        /** The welcome turn's id, so embedded threads can filter it out. */
        const val WELCOME_MESSAGE_ID = WELCOME_ID
    }
}
