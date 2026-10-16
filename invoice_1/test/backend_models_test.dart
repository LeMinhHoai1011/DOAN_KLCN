import 'package:flutter_test/flutter_test.dart';
import 'package:invoice_1/features/auth/data/models/user_model.dart';
import 'package:invoice_1/features/dashboard/data/models/dashboard_summary_model.dart';
import 'package:invoice_1/features/documents/data/models/document_model.dart';

void main() {
  group('Backend response models', () {
    test('maps the actual camelCase user response', () {
      final user = UserModel.fromJson({
        'id': 41,
        'username': 'employee.one',
        'fullName': 'Employee One',
        'companyId': 9,
        'roles': ['EMPLOYEE'],
        'email': 'employee@example.test',
        'status': 'ACTIVE',
      });

      expect(user.userId, 41);
      expect(user.companyId, 9);
      expect(user.fullName, 'Employee One');
      expect(user.roles, ['EMPLOYEE']);
    });

    test('maps a backend DocumentResponse and Spring page', () {
      final page = DocumentPage.fromJson({
        'content': [
          {
            'id': 72,
            'originalFileName': 'invoice.pdf',
            'fileType': 'application/pdf',
            'fileSize': 4096,
            'status': 'PROCESSING',
            'processingStatus': 'PROCESSING',
            'reviewStatus': 'PENDING',
            'companyId': 9,
            'typeId': 3,
            'documentTypeName': 'Invoice',
            'uploadedById': 41,
            'createdAt': '2026-10-06T10:00:00',
            'updatedAt': '2026-10-06T10:01:00',
          },
        ],
        'number': 0,
        'totalPages': 2,
        'last': false,
      });

      expect(page.documents.single.id, 72);
      expect(page.documents.single.originalFileName, 'invoice.pdf');
      expect(page.documents.single.processingStatus, 'PROCESSING');
      expect(page.documents.single.uploadedById, 41);
      expect(page.last, isFalse);
    });

    test('maps only dashboard metrics provided by backend', () {
      final summary = DashboardSummaryModel.fromJson({
        'totalDocuments': 12,
        'totalInvoices': 7,
        'totalClassified': 5,
        'totalReviewRequired': 2,
        'totalFailed': 1,
        'documentsByStatus': [
          {'status': 'PROCESSING', 'count': 3},
        ],
      });

      expect(summary.totalDocuments, 12);
      expect(summary.totalInvoices, 7);
      expect(summary.totalReviewRequired, 2);
      expect(summary.totalFailed, 1);
      expect(summary.documentsByStatus.single.status, 'PROCESSING');
    });

    test('requires backend document IDs to be numeric', () {
      expect(
        () => DocumentModel.fromJson({
          'id': 'DOC_1',
          'originalFileName': 'invoice.pdf',
          'processingStatus': 'UPLOADED',
        }),
        throwsFormatException,
      );
    });
  });
}
