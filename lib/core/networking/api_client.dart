import 'dart:convert';
import 'dart:typed_data';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:path_provider/path_provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart' hide Headers;

import '../config/app_config.dart';

/// Returns the current bearer token to attach to API requests, or null.
typedef AccessTokenProvider = String? Function();

/// A failed API call.
class ApiException implements Exception {
  ApiException(this.message, {this.statusCode});
  final String message;
  final int? statusCode;
  @override
  String toString() => message;
}

String? _supabaseAccessToken() {
  if (!AppConfig.isSupabaseConfigured) return null;
  try {
    return Supabase.instance.client.auth.currentSession?.accessToken;
  } catch (_) {
    return null;
  }
}

/// Thin Dio wrapper around the Next.js backend. Adds the Supabase access token
/// as a `Bearer` header when available (the web used same-origin cookies; the
/// mobile client forwards the JWT, per the plan's Phase 0 backend work).
class ApiClient {
  ApiClient({Dio? dio, AccessTokenProvider? accessToken, CookieJar? cookieJar})
      : _accessToken = accessToken ?? _supabaseAccessToken,
        _dio = dio ??
            Dio(BaseOptions(
              baseUrl: AppConfig.apiBaseUrl,
              connectTimeout: const Duration(seconds: 20),
              receiveTimeout: const Duration(seconds: 60),
              headers: const {'Accept': 'application/json'},
            )) {
    // Cookie jar so SnapTrade's `snaptrade_session` cookie set by /connect is
    // replayed on /accounts (the web relied on same-origin cookies).
    _dio.interceptors.add(CookieManager(cookieJar ?? CookieJar()));
    _dio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) {
      final token = _accessToken();
      if (token != null && token.isNotEmpty) {
        options.headers['Authorization'] = 'Bearer $token';
      }
      handler.next(options);
    }));
  }

  /// Builds an [ApiClient] whose cookies persist across app restarts (used so
  /// the SnapTrade session survives). Falls back to an in-memory jar on error.
  static Future<ApiClient> createPersistent() async {
    CookieJar jar;
    try {
      final dir = await getApplicationDocumentsDirectory();
      jar = PersistCookieJar(storage: FileStorage('${dir.path}/.cookies'));
    } catch (_) {
      jar = CookieJar();
    }
    return ApiClient(cookieJar: jar);
  }

  final Dio _dio;
  final AccessTokenProvider _accessToken;

  Future<dynamic> getJson(String path, {Map<String, dynamic>? query}) async {
    try {
      final res = await _dio.get<dynamic>(path, queryParameters: query);
      return res.data;
    } on DioException catch (e) {
      throw _toApiException(e);
    }
  }

  Future<dynamic> postJson(String path, {Object? body}) async {
    try {
      final res = await _dio.post<dynamic>(
        path,
        data: body,
        options: Options(contentType: Headers.jsonContentType),
      );
      return res.data;
    } on DioException catch (e) {
      throw _toApiException(e);
    }
  }

  /// Streams a plain UTF-8 text response (used by `/api/budget-advisor` and
  /// `/api/chat`), yielding decoded chunks as they arrive.
  Future<Stream<String>> streamText(
    String path, {
    Object? body,
    String method = 'POST',
  }) async {
    try {
      final res = await _dio.request<ResponseBody>(
        path,
        data: body,
        options: Options(
          method: method,
          responseType: ResponseType.stream,
          contentType: Headers.jsonContentType,
          headers: const {'Accept': 'text/plain'},
          receiveTimeout: const Duration(minutes: 5),
        ),
      );
      final body0 = res.data;
      if (body0 == null) {
        throw ApiException('Empty response', statusCode: res.statusCode);
      }
      return body0.stream
          .cast<List<int>>()
          .transform(const Utf8Decoder(allowMalformed: true));
    } on DioException catch (e) {
      throw _toApiException(e);
    }
  }

  ApiException _toApiException(DioException e) {
    final status = e.response?.statusCode;
    String message;
    final data = e.response?.data;
    if (data is Map && data['error'] is String) {
      message = data['error'] as String;
    } else if (data is String && data.isNotEmpty) {
      message = data;
    } else if (data is Uint8List) {
      message = utf8.decode(data, allowMalformed: true);
    } else {
      message = switch (e.type) {
        DioExceptionType.connectionTimeout ||
        DioExceptionType.receiveTimeout ||
        DioExceptionType.sendTimeout =>
          'The request timed out. Check your connection and try again.',
        DioExceptionType.connectionError =>
          'Could not reach the server. Check your connection.',
        _ => 'Request failed${status != null ? ' ($status)' : ''}.',
      };
    }
    return ApiException(message, statusCode: status);
  }
}
