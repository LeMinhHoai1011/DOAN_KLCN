import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../../app/constants/api_constants.dart';
import '../storage/secure_storage.dart';

class ApiClient {
  late final Dio _dio;
  final SecureStorage _secureStorage;
  final VoidCallback? _onUnauthorized;

  ApiClient(this._secureStorage, {this._onUnauthorized}) {
    _dio = Dio(
      BaseOptions(
        baseUrl: ApiConstants.baseUrl,
        connectTimeout: const Duration(
          milliseconds: ApiConstants.connectTimeout,
        ),
        receiveTimeout: const Duration(
          milliseconds: ApiConstants.receiveTimeout,
        ),
        contentType: 'application/json',
      ),
    );

    if (kDebugMode) {
      _dio.interceptors.add(
        LogInterceptor(
          requestHeader: false,
          requestBody: false,
          responseHeader: false,
          responseBody: false,
          logPrint: (message) => debugPrint(message.toString()),
        ),
      );
    }

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await _secureStorage.getToken();
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          return handler.next(options);
        },
        onError: (error, handler) async {
          final isLoginRequest =
              error.requestOptions.path.endsWith('/auth/login');
          if (error.response?.statusCode == 401 && !isLoginRequest) {
            await _secureStorage.clearSession();
            _onUnauthorized?.call();
          }
          return handler.next(error);
        },
      ),
    );
  }

  Dio get dio => _dio;
}

class ApiException implements Exception {
  final int? statusCode;
  final String message;

  const ApiException(this.message, {this.statusCode});

  factory ApiException.from(Object error) {
    if (error is ApiException) return error;
    if (error is! DioException) {
      return ApiException(error.toString());
    }

    final statusCode = error.response?.statusCode;
    if (statusCode == 401 &&
        error.requestOptions.path.endsWith('/auth/login')) {
      return const ApiException(
        'Tên đăng nhập hoặc mật khẩu không đúng.',
        statusCode: 401,
      );
    }
    final backendMessage = _messageFrom(error.response?.data);
    if (backendMessage != null && backendMessage.isNotEmpty) {
      return ApiException(backendMessage, statusCode: statusCode);
    }

    if (statusCode != null && statusCode >= 500) {
      return ApiException(
        'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.',
        statusCode: statusCode,
      );
    }
    final message = switch (statusCode) {
      400 => 'Dữ liệu gửi lên không hợp lệ.',
      401 => 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
      403 => 'Tài khoản không có quyền thực hiện thao tác này.',
      404 => 'Không tìm thấy dữ liệu yêu cầu.',
      409 => 'Dữ liệu đang xung đột với trạng thái hiện tại.',
      413 => 'File vượt quá giới hạn dung lượng mà máy chủ cho phép.',
      _ => switch (error.type) {
        DioExceptionType.connectionTimeout =>
          'Không thể mở kết nối tới ${_endpoint(error.requestOptions.uri)}. '
          'Hãy kiểm tra địa chỉ API, cùng mạng Wi-Fi/Windows Firewall hoặc USB reverse.',
        DioExceptionType.sendTimeout =>
          'Tải dữ liệu lên máy chủ quá thời gian chờ.',
        DioExceptionType.receiveTimeout =>
          'Máy chủ xử lý quá thời gian chờ. Chứng từ có thể đã được tiếp nhận; hãy làm mới danh sách trước khi gửi lại.',
        DioExceptionType.connectionError =>
          'Không thể kết nối tới ${_endpoint(error.requestOptions.uri)}. '
          'Hãy kiểm tra địa chỉ API, mạng và trạng thái backend.',
        DioExceptionType.badCertificate =>
          'Không thể xác thực chứng chỉ bảo mật của máy chủ.',
        _ => 'Không thể hoàn tất yêu cầu tới máy chủ.',
      },
    };
    return ApiException(message, statusCode: statusCode);
  }

  static String? _messageFrom(Object? data) {
    if (data is! Map) return null;
    for (final key in ['message', 'error', 'details']) {
      final value = data[key];
      if (value is String && value.trim().isNotEmpty) return value;
    }
    return null;
  }

  static String _endpoint(Uri uri) {
    final port = uri.hasPort ? ':${uri.port}' : '';
    return '${uri.host}$port';
  }

  @override
  String toString() => message;
}
