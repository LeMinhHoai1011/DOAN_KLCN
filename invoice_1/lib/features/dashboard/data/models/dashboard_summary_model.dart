class DashboardSummaryModel {
  final int totalDocuments;
  final int totalInvoices;
  final int totalClassified;
  final int totalReviewRequired;
  final int totalFailed;
  final int? approvedCount;
  final int? correctionCount;
  final int? rejectedCount;
  int countStatus(String status) => documentsByStatus
      .where((entry) => entry.status == status)
      .fold(0, (sum, entry) => sum + entry.count);
  final List<DocumentStatusCount> documentsByStatus;

  const DashboardSummaryModel({
    required this.totalDocuments,
    required this.totalInvoices,
    required this.totalClassified,
    required this.totalReviewRequired,
    required this.totalFailed,
    this.approvedCount,
    this.correctionCount,
    this.rejectedCount,
    required this.documentsByStatus,
  });

  factory DashboardSummaryModel.fromJson(Map<String, dynamic> json) {
    final statuses = json['documentsByStatus'];
    return DashboardSummaryModel(
      totalDocuments: _asInt(json['totalDocuments']),
      totalInvoices: _asInt(json['totalInvoices']),
      totalClassified: _asInt(json['totalClassified']),
      totalReviewRequired: _asInt(json['totalReviewRequired']),
      totalFailed: _asInt(json['totalFailed']),
      documentsByStatus: statuses is List
          ? statuses
                .map(
                  (item) => DocumentStatusCount.fromJson(
                    item as Map<String, dynamic>,
                  ),
                )
                .toList()
          : const [],
    );
  }

  static int _asInt(Object? value) => (value as num?)?.toInt() ?? 0;
}

class DocumentStatusCount {
  final String status;
  final int count;

  const DocumentStatusCount({required this.status, required this.count});

  factory DocumentStatusCount.fromJson(Map<String, dynamic> json) {
    return DocumentStatusCount(
      status: json['status']?.toString() ?? 'UNKNOWN',
      count: (json['count'] as num?)?.toInt() ?? 0,
    );
  }
}
