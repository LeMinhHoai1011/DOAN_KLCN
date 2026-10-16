import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:invoice_1/core/network/api_client.dart';
import 'package:invoice_1/core/storage/secure_storage.dart';

void main() {
  group('ApiException', () {
    test('prefers a backend message and preserves HTTP status', () {
      final error = DioException(
        requestOptions: RequestOptions(path: '/documents'),
        response: Response(
          requestOptions: RequestOptions(path: '/documents'),
          statusCode: 400,
          data: {
            'code': 'INVALID_FILE',
            'message': 'File type is not supported',
          },
        ),
        type: DioExceptionType.badResponse,
      );

      final mapped = ApiException.from(error);

      expect(mapped.statusCode, 400);
      expect(mapped.message, 'File type is not supported');
    });

    test('maps timeout errors to a useful message', () {
      final error = DioException(
        requestOptions: RequestOptions(path: '/documents/upload'),
        type: DioExceptionType.receiveTimeout,
      );

      expect(
        ApiException.from(error).message,
        'Máy chủ xử lý quá thời gian chờ. Chứng từ có thể đã được tiếp nhận; hãy làm mới danh sách trước khi gửi lại.',
      );
    });

    test('distinguishes TCP connection timeout and includes the endpoint', () {
      final error = DioException(
        requestOptions: RequestOptions(
          path: 'http://127.0.0.1:8081/api/v1/auth/login',
        ),
        type: DioExceptionType.connectionTimeout,
      );

      final message = ApiException.from(error).message;

      expect(message, contains('127.0.0.1:8081'));
      expect(message, contains('USB reverse'));
      expect(message, isNot(contains('xử lý quá thời gian')));
    });

    test('sends the stored JWT as a Bearer authorization header', () async {
      final client = ApiClient(_MemoryStorage('test-jwt'));
      final adapter = _CapturingAdapter();
      client.dio.httpClientAdapter = adapter;

      await client.dio.get('/users/me');

      expect(
        adapter.requestOptions?.headers['Authorization'],
        'Bearer test-jwt',
      );
      client.dio.close(force: true);
    });
  });
}

class _MemoryStorage extends SecureStorage {
  final String _token;

  _MemoryStorage(this._token);

  @override
  Future<String?> getToken() async => _token;

  @override
  Future<void> clearSession() async {}
}

class _CapturingAdapter implements HttpClientAdapter {
  RequestOptions? requestOptions;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requestOptions = options;
    return ResponseBody.fromString(
      '{"id": 1}',
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
