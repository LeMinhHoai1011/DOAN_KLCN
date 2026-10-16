class DocumentModel {
  final int id;
  final String originalFileName;
  final String? fileType;
  final int? fileSize;
  final String processingStatus;
  final String? reviewStatus;
  final int? companyId;
  final int? typeId;
  final String? documentType;
  final String? documentTypeCode;
  final String? documentTypeName;
  final int? uploadedById;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const DocumentModel({
    required this.id,
    required this.originalFileName,
    this.fileType,
    this.fileSize,
    required this.processingStatus,
    this.reviewStatus,
    this.companyId,
    this.typeId,
    this.documentType,
    this.documentTypeCode,
    this.documentTypeName,
    this.uploadedById,
    this.createdAt,
    this.updatedAt,
  });

  factory DocumentModel.fromJson(Map<String, dynamic> json) {
    return DocumentModel(
      id: _asInt(json['id']),
      originalFileName: json['originalFileName'] as String? ?? '',
      fileType: json['fileType'] as String?,
      fileSize: _asNullableInt(json['fileSize']),
      processingStatus: _status(json['processingStatus'] ?? json['status']),
      reviewStatus: json['reviewStatus']?.toString(),
      companyId: _asNullableInt(json['companyId']),
      typeId: _asNullableInt(json['typeId']),
      documentType: json['documentType'] as String?,
      documentTypeCode: json['documentTypeCode'] as String?,
      documentTypeName: json['documentTypeName'] as String?,
      uploadedById: _asNullableInt(json['uploadedById']),
      createdAt: _date(json['createdAt']),
      updatedAt: _date(json['updatedAt']),
    );
  }

  static int _asInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.parse(value);
    throw const FormatException('Backend document response did not contain a numeric id.');
  }

  static int? _asNullableInt(Object? value) {
    if (value == null) return null;
    return _asInt(value);
  }

  static DateTime? _date(Object? value) {
    if (value == null) return null;
    return DateTime.parse(value.toString());
  }

  static String _status(Object? value) {
    return value?.toString() ?? 'UNKNOWN';
  }
}

class DocumentPage {
  final List<DocumentModel> documents;
  final int page;
  final int totalPages;
  final bool last;

  const DocumentPage({
    required this.documents,
    required this.page,
    required this.totalPages,
    required this.last,
  });

  factory DocumentPage.fromJson(Map<String, dynamic> json) {
    final content = json['content'];
    if (content is! List) {
      throw const FormatException('Backend document page did not contain content.');
    }
    return DocumentPage(
      documents: content
          .map((item) => DocumentModel.fromJson(item as Map<String, dynamic>))
          .toList(),
      page: (json['number'] as num?)?.toInt() ?? 0,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
      last: json['last'] as bool? ?? true,
    );
  }
}

class DocumentUploadResult {
  final bool success;
  final int documentId;
  final String processingStatus;
  final String reviewStatus;
  final String message;
  final String? errorCode;
  final String? errorMessage;

  const DocumentUploadResult({
    required this.success,
    required this.documentId,
    required this.processingStatus,
    required this.reviewStatus,
    required this.message,
    this.errorCode,
    this.errorMessage,
  });

  factory DocumentUploadResult.fromJson(Map<String, dynamic> json) {
    final error = json['error'];
    final errorMap = error is Map<String, dynamic> ? error : null;
    return DocumentUploadResult(
      success: json['success'] as bool? ?? false,
      documentId: DocumentModel._asInt(json['documentId']),
      processingStatus: DocumentModel._status(json['processingStatus']),
      reviewStatus: DocumentModel._status(json['reviewStatus']),
      message: json['message'] as String? ?? '',
      errorCode: errorMap?['code'] as String?,
      errorMessage: errorMap?['message'] as String?,
    );
  }
}
