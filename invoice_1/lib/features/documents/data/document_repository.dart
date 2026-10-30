import 'dart:io';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import '../../../../core/network/api_client.dart';
import 'models/document_model.dart';

class DocumentDetailData {
  final DocumentModel document;
  final Map<String, dynamic>? ocr;
  final Map<String, dynamic>? classification;
  final Map<String, dynamic>? invoice;
  final List<dynamic> extractedFields;

  const DocumentDetailData({
    required this.document,
    this.ocr,
    this.classification,
    this.invoice,
    this.extractedFields = const [],
  });
}

class DocumentRepository {
  final ApiClient _apiClient;

  DocumentRepository(this._apiClient);

  Future<Map<String, dynamic>?> getBusinessState(int id) async {
    return await _getOptionalJson('/documents/$id/business-state')
        as Map<String, dynamic>?;
  }

  Future<Map<String, dynamic>?> getAssessment(int id) async {
    return await _getOptionalJson('/documents/$id/assessment')
        as Map<String, dynamic>?;
  }

  Future<DocumentPage> getMyDocuments({
    int page = 0,
    int size = 20,
    String sort = 'createdAt,desc',
    String? search,
    String? processingStatus,
    String? reviewStatus,
  }) async {
    try {
      final queryParameters = <String, Object>{
        'page': page,
        'size': size,
        'sort': sort,
      };
      if (search != null && search.trim().isNotEmpty) {
        queryParameters['search'] = search.trim();
      }
      if (processingStatus != null) {
        queryParameters['processingStatus'] = processingStatus;
      }
      if (reviewStatus != null) {
        queryParameters['reviewStatus'] = reviewStatus;
      }
      final response = await _apiClient.dio.get<Map<String, dynamic>>(
        '/documents',
        queryParameters: queryParameters,
      );
      return DocumentPage.fromJson(response.data ?? const {});
    } catch (error) {
      throw ApiException.from(error);
    }
  }

  Future<DocumentDetailData> getDocumentDetail(int documentId) async {
    try {
      final documentResponse = await _apiClient.dio.get<Map<String, dynamic>>(
        '/documents/$documentId',
      );
      final document = DocumentModel.fromJson(
        documentResponse.data ?? const {},
      );
      final optional = await Future.wait<Object?>([
        _getOptionalJson('/documents/$documentId/ocr'),
        _getOptionalJson('/documents/$documentId/classification'),
        _getOptionalJson('/documents/$documentId/invoice'),
        _getOptionalJson('/documents/$documentId/extracted-fields'),
      ]);
      final fields = optional[3];
      return DocumentDetailData(
        document: document,
        ocr: optional[0] as Map<String, dynamic>?,
        classification: optional[1] as Map<String, dynamic>?,
        invoice: optional[2] as Map<String, dynamic>?,
        extractedFields: fields is List ? fields : const [],
      );
    } catch (error) {
      throw ApiException.from(error);
    }
  }

  Future<Object?> _getOptionalJson(String path) async {
    try {
      final response = await _apiClient.dio.get<Object?>(path);
      return response.data;
    } on DioException catch (error) {
      if (error.response?.statusCode == 404) return null;
      throw ApiException.from(error);
    }
  }

  Future<Uint8List> getDocumentFile(int documentId) async {
    try {
      final response = await _apiClient.dio.get<List<int>>(
        '/documents/$documentId/preview',
        options: Options(responseType: ResponseType.bytes),
      );
      final bytes = response.data;
      if (bytes == null || bytes.isEmpty) {
        throw const FormatException('Máy chủ trả về file trống.');
      }
      return Uint8List.fromList(bytes);
    } catch (error) {
      if (error is FormatException) rethrow;
      throw ApiException.from(error);
    }
  }

  Future<DocumentUploadResult> uploadDocument(
    File file, {
    ProgressCallback? onSendProgress,
  }) async {
    try {
      final formData = FormData.fromMap({
        'file': await MultipartFile.fromFile(
          file.path,
          filename: file.uri.pathSegments.last,
        ),
      });
      final response = await _apiClient.dio.post<Map<String, dynamic>>(
        '/documents/upload',
        data: formData,
        onSendProgress: onSendProgress,
      );
      final data = response.data;
      if (data == null) {
        throw const FormatException('Phản hồi tải chứng từ bị trống.');
      }
      return DocumentUploadResult.fromJson(data);
    } catch (error) {
      if (error is FormatException) rethrow;
      throw ApiException.from(error);
    }
  }

  Future<DocumentModel> uploadDocumentVersion(
    int documentId,
    File file, {
    ProgressCallback? onSendProgress,
  }) async {
    try {
      final formData = FormData.fromMap({
        'file': await MultipartFile.fromFile(
          file.path,
          filename: file.uri.pathSegments.last,
        ),
      });
      final response = await _apiClient.dio.post<Map<String, dynamic>>(
        '/documents/$documentId/versions',
        data: formData,
        onSendProgress: onSendProgress,
      );
      return DocumentModel.fromJson(response.data ?? const {});
    } catch (error) {
      throw ApiException.from(error);
    }
  }

  Future<DocumentModel> runWorkflowAction(
    int documentId,
    String action, {
    String? note,
  }) async {
    try {
      final response = await _apiClient.dio.post<Map<String, dynamic>>(
        '/documents/$documentId/workflow',
        data: {
          'action': action,
          if (note != null && note.trim().isNotEmpty) 'note': note.trim(),
        },
      );
      return DocumentModel.fromJson(response.data ?? const {});
    } catch (error) {
      throw ApiException.from(error);
    }
  }
}
