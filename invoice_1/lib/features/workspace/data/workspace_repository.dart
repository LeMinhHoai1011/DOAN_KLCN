import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../../auth/presentation/providers/auth_provider.dart';
import '../../auth/data/models/user_model.dart';

final workspaceRepositoryProvider = Provider((ref) {
  ref.watch(authProvider.select((s) => s.user?.userId));
  return WorkspaceRepository(ref.watch(apiClientProvider));
});

class FinancialEntry {
  final int id;
  final String type, confirmation;
  final num amount;
  final String? description, date, category;
  final int? documentId;
  FinancialEntry({
    required this.id,
    required this.type,
    required this.confirmation,
    required this.amount,
    this.description,
    this.date,
    this.category,
    this.documentId,
  });
  factory FinancialEntry.fromJson(Map<String, dynamic> json) => FinancialEntry(
    id: (json['id'] as num).toInt(),
    type: json['transactionType']?.toString() ?? '',
    confirmation: json['paymentConfirmation']?.toString() ?? 'UNVERIFIED',
    amount: json['amount'] as num? ?? 0,
    description: json['description']?.toString(),
    date: json['transactionDate']?.toString(),
    category: json['categoryName']?.toString(),
    documentId: (json['documentId'] as num?)?.toInt(),
  );
}

class FinancialPage {
  final List<FinancialEntry> entries;
  final bool last;
  FinancialPage(this.entries, this.last);
}

class WorkspaceRepository {
  final ApiClient api;
  WorkspaceRepository(this.api);
  Future<FinancialPage> transactions({
    int page = 0,
    String? type,
    String? dateFrom,
    String? dateTo,
  }) async {
    try {
      final response = await api.dio.get<Map<String, dynamic>>(
        '/financial-transactions',
        queryParameters: {
          'page': page,
          'size': 20,
          'sort': 'transactionDate,desc',
          'transactionType': ?type,
          'dateFrom': ?dateFrom,
          'dateTo': ?dateTo,
        },
      );
      final data = response.data ?? {};
      return FinancialPage(
        (data['content'] as List? ?? [])
            .map(
              (e) =>
                  FinancialEntry.fromJson(Map<String, dynamic>.from(e as Map)),
            )
            .toList(),
        data['last'] as bool? ?? true,
      );
    } catch (e) {
      throw ApiException.from(e);
    }
  }

  Future<List<UserModel>> users() async {
    try {
      final response = await api.dio.get<List<dynamic>>('/users');
      return (response.data ?? [])
          .map((e) => UserModel.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (e) {
      throw ApiException.from(e);
    }
  }

  Future<Map<String, dynamic>> financialTotals(String from, String to) async {
    try {
      final response = await api.dio.get<Map<String, dynamic>>(
        '/dashboard/financial',
        queryParameters: {'dateFrom': from, 'dateTo': to},
      );
      return response.data ?? {};
    } catch (error) {
      throw ApiException.from(error);
    }
  }
}

final workspaceUsersProvider = FutureProvider.autoDispose(
  (ref) => ref.watch(workspaceRepositoryProvider).users(),
);
final financialTotalsProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, ({String from, String to})>(
      (ref, range) => ref
          .watch(workspaceRepositoryProvider)
          .financialTotals(range.from, range.to),
    );
