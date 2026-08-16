//
// ApiClient.kt
//
// Port of iOS Core/Networking/APIClient.swift — thin client for the existing
// FinnaCalc Next.js API (the same backend the website uses). All feature
// networking goes through here. The web routes do not yet verify the
// Authorization header, but we send the Supabase access token as a Bearer when
// available so it's forward-compatible.
//

package com.finnacalc.android.core.networking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ApiConfig {
    /** Base URL of the FinnaCalc API. The production site is www.finnacalc.com. */
    const val BASE_URL = "https://www.finnacalc.com"
}

sealed class ApiException(message: String) : Exception(message) {
    /** A normal failure with a server-provided (or synthesized) message. */
    class Message(message: String) : ApiException(message)

    /** The feature's backend isn't configured (HTTP 503 from the route). */
    class NotConfigured(message: String) : ApiException(message)

    class Decoding(cause: Throwable) : ApiException("Couldn't read the server's response.") {
        init { initCause(cause) }
    }

    companion object {
        /**
         * Build an error from a non-2xx body. Plaid routes return
         * `{ "error": "…" }`; the budget-advisor/chat routes return plain text.
         */
        fun from(body: String, status: Int): ApiException {
            var message = "Request failed ($status)."
            val jsonError = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull()
            if (!jsonError.isNullOrEmpty()) {
                message = jsonError
            } else if (body.isNotBlank()) {
                message = body
            }
            return if (status == 503) NotConfigured(message) else Message(message)
        }
    }
}

class ApiClient(
    var baseUrl: String = ApiConfig.BASE_URL,
) {
    /**
     * Supplies the current Supabase access token (set at app launch).
     * Optional — the API works without it today.
     */
    var tokenProvider: (suspend () -> String?)? = null

    val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // Streaming chat responses stay open well past a normal read window.
        .readTimeout(120, TimeUnit.SECONDS)
        // The SnapTrade session lives in an httpOnly cookie set on the connect
        // response; this jar carries it to the accounts call automatically —
        // the OkHttp analogue of URLSession's shared cookie storage on iOS.
        .cookieJar(InMemoryCookieJar())
        .build()

    // MARK: Requests

    /** POST a JSON body and decode the JSON response (2xx only). */
    suspend inline fun <reified Response> postJson(path: String, body: String): Response {
        val data = postData(path, body)
        return try {
            json.decodeFromString(data)
        } catch (e: Exception) {
            throw ApiException.Decoding(e)
        }
    }

    /** GET a path with query items and decode the JSON response (2xx only). */
    suspend inline fun <reified Response> getJson(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): Response {
        val data = getData(path, query)
        return try {
            json.decodeFromString(data)
        } catch (e: Exception) {
            throw ApiException.Decoding(e)
        }
    }

    /**
     * POST a JSON body and return the raw response text (2xx only; throws an
     * [ApiException] with the server message otherwise).
     */
    suspend fun postData(path: String, body: String): String {
        val response = execute(makeRequest(path, body))
        return response.use { checkAndRead(it) }
    }

    /**
     * POST a JSON body and return (text, statusCode) WITHOUT throwing on a
     * non-2xx status, so the caller can decode a structured error body (e.g.
     * /api/efile returns its `{status,message,...}` result with HTTP 501).
     */
    suspend fun postAllowingErrorStatus(path: String, body: String): Pair<String, Int> {
        val response = execute(makeRequest(path, body))
        return response.use { (it.body?.string() ?: "") to it.code }
    }

    suspend fun getData(path: String, query: Map<String, String> = emptyMap()): String {
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val builder = Request.Builder().url(url).get()
        tokenProvider?.invoke()?.let { builder.header("Authorization", "Bearer $it") }
        val response = execute(builder.build())
        return response.use { checkAndRead(it) }
    }

    /**
     * POST a JSON body and stream the plain-text response. Each emitted value
     * is the cumulative text received so far (matching the web's `acc`
     * pattern), so a consumer just assigns it to the message being rendered.
     * UTF-8 boundary safety comes from decoding the accumulated bytes each
     * emission.
     */
    fun postTextStream(path: String, body: String): Flow<String> = callbackFlow {
        val request = makeRequest(path, body)
        val call = client.newCall(request)
        val thread = Thread {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val text = response.body?.string() ?: ""
                        throw ApiException.from(text, response.code)
                    }
                    val source = response.body?.source() ?: throw ApiException.Message("No response from the server.")
                    val accumulated = okio.Buffer()
                    val chunk = ByteArray(4096)
                    while (true) {
                        val read = source.inputStream().read(chunk)
                        if (read == -1) break
                        accumulated.write(chunk, 0, read)
                        // Only emit on a valid UTF-8 boundary so multibyte
                        // characters never render half-decoded.
                        val text = accumulated.snapshot().utf8()
                        if (!text.contains('�')) trySend(text)
                    }
                    trySend(accumulated.snapshot().utf8())
                    close()
                }
            } catch (e: Exception) {
                close(e)
            }
        }
        thread.start()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    // MARK: Helpers

    private suspend fun makeRequest(path: String, body: String): Request {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .post(body.toRequestBody("application/json".toMediaType()))
        tokenProvider?.invoke()?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }

    private suspend fun execute(request: Request): Response =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(ApiException.Message(e.message ?: "No response from the server."))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                })
            }
        }

    private fun checkAndRead(response: Response): String {
        val text = response.body?.string() ?: ""
        if (response.code !in 200..299) {
            throw ApiException.from(text, response.code)
        }
        return text
    }

    companion object {
        val shared = ApiClient()
    }
}

/**
 * Session-lifetime cookie storage. Cookies here are session cookies (the
 * SnapTrade httpOnly session); nothing needs to survive a restart, and the
 * backend re-registers on the next connect call if it's gone.
 */
private class InMemoryCookieJar : okhttp3.CookieJar {
    private val store = mutableMapOf<String, List<okhttp3.Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        val existing = store[url.host] ?: emptyList()
        val merged = (cookies + existing).distinctBy { it.name }
        store[url.host] = merged
    }

    @Synchronized
    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
        (store[url.host] ?: emptyList()).filter { it.expiresAt > System.currentTimeMillis() }
}
