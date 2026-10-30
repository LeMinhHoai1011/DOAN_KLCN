import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../data/dashboard_repository.dart';
import '../../data/models/dashboard_summary_model.dart';
import '../../../documents/data/models/document_model.dart';

final dashboardRepositoryProvider = Provider((ref) {
  ref.watch(authProvider.select((state) => state.user?.userId));
  return DashboardRepository(
    ref.watch(apiClientProvider),
    reviewQueue: ref.watch(authProvider).user?.effectiveRole == 'ACCOUNTANT',
  );
});

class DashboardState {
  final bool isLoading;
  final String? error;
  final DashboardSummaryModel? summary;
  final List<DocumentModel> recentDocuments;

  const DashboardState({
    this.isLoading = true,
    this.error,
    this.summary,
    this.recentDocuments = const [],
  });

  DashboardState copyWith({
    bool? isLoading,
    String? error,
    DashboardSummaryModel? summary,
    List<DocumentModel>? recentDocuments,
  }) {
    return DashboardState(
      isLoading: isLoading ?? this.isLoading,
      error: error,
      summary: summary ?? this.summary,
      recentDocuments: recentDocuments ?? this.recentDocuments,
    );
  }
}

class DashboardNotifier extends StateNotifier<DashboardState> {
  final DashboardRepository _repository;

  DashboardNotifier(this._repository) : super(const DashboardState()) {
    loadData();
  }

  Future<void> loadData() async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      final results = await Future.wait<Object>([
        _repository.getSummary(),
        _repository.getRecentDocuments(),
      ]);
      state = state.copyWith(
        isLoading: false,
        summary: results[0] as DashboardSummaryModel,
        recentDocuments: results[1] as List<DocumentModel>,
      );
    } catch (error) {
      state = state.copyWith(isLoading: false, error: error.toString());
    }
  }
}

final dashboardProvider =
    StateNotifierProvider<DashboardNotifier, DashboardState>((ref) {
      return DashboardNotifier(ref.watch(dashboardRepositoryProvider));
    });
