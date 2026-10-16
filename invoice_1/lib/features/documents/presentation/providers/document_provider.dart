import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../features/auth/presentation/providers/auth_provider.dart';
import '../../data/document_repository.dart';
import '../../data/models/document_model.dart';
import 'dart:typed_data';

final documentRepositoryProvider = Provider((ref) {
  ref.watch(authProvider.select((state) => state.user?.userId));
  return DocumentRepository(ref.watch(apiClientProvider));
});

class DocumentListState {
  final bool isLoading;
  final bool isLoadingMore;
  final String? error;
  final List<DocumentModel> documents;
  final int page;
  final bool hasMore;
  final String search;
  final String? status;

  const DocumentListState({
    this.isLoading = true,
    this.isLoadingMore = false,
    this.error,
    this.documents = const [],
    this.page = 0,
    this.hasMore = true,
    this.search = '',
    this.status,
  });
}

class DocumentListNotifier extends StateNotifier<DocumentListState> {
  final DocumentRepository _repository;
  static const _pageSize = 20;

  DocumentListNotifier(this._repository) : super(const DocumentListState()) {
    loadDocuments();
  }

  Future<void> loadDocuments({String? search, String? status}) async {
    final nextSearch = search ?? state.search;
    final nextStatus = status == null
        ? state.status
        : status.isEmpty || status == '*'
        ? null
        : status;
    state = DocumentListState(
      isLoading: true,
      search: nextSearch,
      status: nextStatus,
    );
    try {
      final page = await _repository.getMyDocuments(
        page: 0,
        size: _pageSize,
        sort: 'createdAt,desc',
        search: nextSearch,
        processingStatus: nextStatus,
      );
      state = DocumentListState(
        isLoading: false,
        documents: page.documents,
        page: page.page,
        hasMore: !page.last,
        search: nextSearch,
        status: nextStatus,
      );
    } catch (error) {
      state = DocumentListState(
        isLoading: false,
        error: error.toString(),
        search: nextSearch,
        status: nextStatus,
      );
    }
  }

  Future<void> loadMore() async {
    if (state.isLoading || state.isLoadingMore || !state.hasMore) return;
    final nextPage = state.page + 1;
    state = DocumentListState(
      isLoadingMore: true,
      documents: state.documents,
      page: state.page,
      hasMore: state.hasMore,
      search: state.search,
      status: state.status,
    );
    try {
      final page = await _repository.getMyDocuments(
        page: nextPage,
        size: _pageSize,
        sort: 'createdAt,desc',
        search: state.search,
        processingStatus: state.status,
      );
      state = DocumentListState(
        documents: [...state.documents, ...page.documents],
        page: page.page,
        hasMore: !page.last,
        search: state.search,
        status: state.status,
      );
    } catch (error) {
      state = DocumentListState(
        isLoading: false,
        isLoadingMore: false,
        error: error.toString(),
        documents: state.documents,
        page: state.page,
        hasMore: state.hasMore,
        search: state.search,
        status: state.status,
      );
    }
  }
}

final documentListProvider =
    StateNotifierProvider<DocumentListNotifier, DocumentListState>((ref) {
      return DocumentListNotifier(ref.watch(documentRepositoryProvider));
    });

final documentDetailProvider = FutureProvider.autoDispose
    .family<DocumentDetailData, int>((ref, id) {
      return ref.watch(documentRepositoryProvider).getDocumentDetail(id);
    });

final documentFileProvider = FutureProvider.autoDispose.family<Uint8List, int>((
  ref,
  id,
) {
  return ref.watch(documentRepositoryProvider).getDocumentFile(id);
});
