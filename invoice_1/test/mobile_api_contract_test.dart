import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:invoice_1/core/network/api_client.dart';
import 'package:invoice_1/core/storage/secure_storage.dart';
import 'package:invoice_1/features/auth/data/auth_repository.dart';
import 'package:invoice_1/features/documents/data/document_repository.dart';
import 'package:invoice_1/features/workspace/data/workspace_repository.dart';

class _Storage extends SecureStorage {
  @override
  Future<String?> getToken() async => 'test-token';
}

class _Adapter implements HttpClientAdapter {
  final calls = <RequestOptions>[];
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    calls.add(options);
    final path = options.path;
    final Object response = path == '/dashboard/financial'
        ? {'totalRevenue': 8500000, 'totalExpense': 3200000}
        : path == '/financial-transactions'
        ? {
            'content': [
              {
                'id': 12,
                'transactionType': 'EXPENSE',
                'amount': 350000,
                'paymentConfirmation': 'CONFIRMED',
                'documentId': 247,
              },
            ],
            'last': true,
          }
        : path == '/users'
        ? [
            {
              'id': 1,
              'username': 'an',
              'fullName': 'An',
              'roles': ['EMPLOYEE'],
              'status': 'ACTIVE',
            },
          ]
        : path.endsWith('/workflow')
        ? {
            'id': 247,
            'originalFileName': 'invoice.pdf',
            'processingStatus': 'NEED_REVIEW',
            'reviewStatus': 'CORRECTED',
          }
        : path.endsWith('/business-state')
        ? {
            'payment': null,
            'history': [
              {
                'action': 'REQUEST_INFO: Thiếu tổng tiền',
                'status': 'CORRECTED',
              },
            ],
          }
        : {};
    return ResponseBody.fromString(
      jsonEncode(response),
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late ApiClient client;
  late _Adapter adapter;
  setUp(() {
    client = ApiClient(_Storage());
    adapter = _Adapter();
    client.dio.httpClientAdapter = adapter;
  });
  tearDown(() => client.dio.close(force: true));
  test(
    'review note and payment evidence use actual backend contracts',
    () async {
      final documents = DocumentRepository(client);
      await documents.runWorkflowAction(
        247,
        'REQUEST_INFO',
        note: '  Thiếu tổng tiền  ',
      );
      expect(adapter.calls.last.data, {
        'action': 'REQUEST_INFO',
        'note': 'Thiếu tổng tiền',
      });
      final business = await documents.getBusinessState(247);
      expect(business!['payment'], isNull);
      expect(
        (business['history'] as List).first['action'],
        contains('Thiếu tổng tiền'),
      );
    },
  );
  test(
    'accountant transactions forward server pagination and type filter',
    () async {
      final page = await WorkspaceRepository(client).transactions(
        page: 2,
        type: 'EXPENSE',
        dateFrom: '2026-10-01',
        dateTo: '2026-10-31',
      );
      expect(adapter.calls.last.queryParameters['page'], 2);
      expect(adapter.calls.last.queryParameters['transactionType'], 'EXPENSE');
      expect(adapter.calls.last.queryParameters['dateFrom'], '2026-10-01');
      expect(adapter.calls.last.queryParameters['dateTo'], '2026-10-31');
      expect(page.entries.single.confirmation, 'CONFIRMED');
      expect(page.entries.single.documentId, 247);
    },
  );
  test(
    'monthly financial totals pass the selected range to the server',
    () async {
      final totals = await WorkspaceRepository(
        client,
      ).financialTotals('2026-10-01', '2026-10-31');
      expect(adapter.calls.last.path, '/dashboard/financial');
      expect(adapter.calls.last.queryParameters, {
        'dateFrom': '2026-10-01',
        'dateTo': '2026-10-31',
      });
      expect(totals['totalRevenue'], 8500000);
      expect(totals['totalExpense'], 3200000);
    },
  );
  test('admin users and change password use authenticated endpoints', () async {
    final users = await WorkspaceRepository(client).users();
    expect(users.single.effectiveRole, 'EMPLOYEE');
    await AuthRepository(
      client,
      _Storage(),
    ).changePassword('old-pass', 'new-pass123');
    expect(adapter.calls.last.method, 'PUT');
    expect(adapter.calls.last.path, '/users/me/password');
    expect(adapter.calls.last.data, {
      'currentPassword': 'old-pass',
      'newPassword': 'new-pass123',
    });
    expect(adapter.calls.last.headers['Authorization'], 'Bearer test-token');
  });
}
