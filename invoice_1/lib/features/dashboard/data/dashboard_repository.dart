import '../../../../core/network/api_client.dart';
import '../../documents/data/document_repository.dart';
import '../../documents/data/models/document_model.dart';
import 'models/dashboard_summary_model.dart';

class DashboardRepository {
  final ApiClient _apiClient;
  final bool reviewQueue;

  DashboardRepository(this._apiClient, {this.reviewQueue = false});

  Future<DashboardSummaryModel> getSummary() async {
    try {
      final response = await _apiClient.dio.get<Map<String, dynamic>>(
        '/dashboard/statistics',
      );
      final summary = DashboardSummaryModel.fromJson(response.data ?? const {});
      // Counts are server-side totals, never inferred from the five recent files.
      final documents = DocumentRepository(_apiClient);
      Future<int?> count(String review) async {
        try {
          return (await documents.getMyDocuments(
            size: 1,
            reviewStatus: review,
          )).totalElements;
        } catch (_) {
          return null;
        }
      }

      final counts = await Future.wait([
        count('APPROVED'),
        count('CORRECTED'),
        count('REJECTED'),
      ]);
      return DashboardSummaryModel(
        totalDocuments: summary.totalDocuments,
        totalInvoices: summary.totalInvoices,
        totalClassified: summary.totalClassified,
        totalReviewRequired: summary.totalReviewRequired,
        totalFailed: summary.totalFailed,
        documentsByStatus: summary.documentsByStatus,
        approvedCount: counts[0],
        correctionCount: counts[1],
        rejectedCount: counts[2],
      );
    } catch (error) {
      throw ApiException.from(error);
    }
  }

  Future<List<DocumentModel>> getRecentDocuments() async {
    final page = await DocumentRepository(_apiClient).getMyDocuments(
      page: 0,
      size: 5,
      sort: 'createdAt,desc',
      reviewStatus: reviewQueue ? 'PENDING' : null,
    );
    return page.documents;
  }
}
