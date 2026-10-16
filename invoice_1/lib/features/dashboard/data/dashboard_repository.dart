import '../../../../core/network/api_client.dart';
import '../../documents/data/document_repository.dart';
import '../../documents/data/models/document_model.dart';
import 'models/dashboard_summary_model.dart';

class DashboardRepository {
  final ApiClient _apiClient;

  DashboardRepository(this._apiClient);

  Future<DashboardSummaryModel> getSummary() async {
    try {
      final response =
          await _apiClient.dio.get<Map<String, dynamic>>('/dashboard/statistics');
      return DashboardSummaryModel.fromJson(response.data ?? const {});
    } catch (error) {
      throw ApiException.from(error);
    }
  }

  Future<List<DocumentModel>> getRecentDocuments() async {
    final page = await DocumentRepository(_apiClient).getMyDocuments(
      page: 0,
      size: 5,
      sort: 'createdAt,desc',
    );
    return page.documents;
  }
}
