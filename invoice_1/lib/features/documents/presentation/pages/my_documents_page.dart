import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../app/theme/app_colors.dart';
import '../providers/document_provider.dart';
import '../../../dashboard/presentation/widgets/recent_document_card.dart';

class MyDocumentsPage extends ConsumerWidget {
  const MyDocumentsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(documentListProvider);
    final notifier = ref.read(documentListProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Chứng từ của tôi'),
        actions: [
          IconButton(
            tooltip: 'Tải lên chứng từ',
            icon: const Icon(Icons.add),
            onPressed: () => context.push('/documents/upload'),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: TextField(
              textInputAction: TextInputAction.search,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Tìm tên chứng từ',
                border: OutlineInputBorder(),
              ),
              onSubmitted: (search) =>
                  notifier.loadDocuments(search: search, status: state.status),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: DropdownButtonFormField<String>(
              initialValue: state.status ?? '',
              decoration: const InputDecoration(
                labelText: 'Trạng thái xử lý',
                border: OutlineInputBorder(),
                isDense: true,
              ),
              items: const [
                DropdownMenuItem(value: '', child: Text('Tất cả')),
                DropdownMenuItem(value: 'UPLOADED', child: Text('Đã tải lên')),
                DropdownMenuItem(
                  value: 'PROCESSING',
                  child: Text('Đang xử lý'),
                ),
                DropdownMenuItem(value: 'PROCESSED', child: Text('Đã xử lý')),
                DropdownMenuItem(
                  value: 'NEED_REVIEW',
                  child: Text('Cần kiểm tra'),
                ),
                DropdownMenuItem(value: 'COMPLETED', child: Text('Hoàn tất')),
                DropdownMenuItem(value: 'FAILED', child: Text('Thất bại')),
              ],
              onChanged: (status) => notifier.loadDocuments(
                search: state.search,
                status: status ?? '',
              ),
            ),
          ),
          Expanded(
            child: state.isLoading && state.documents.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : state.error != null && state.documents.isEmpty
                ? _ErrorState(
                    message: state.error!,
                    onRetry: () => notifier.loadDocuments(),
                  )
                : state.documents.isEmpty
                ? RefreshIndicator(
                    onRefresh: () => notifier.loadDocuments(),
                    child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      children: const [
                        SizedBox(height: 140),
                        Center(child: Text('Không tìm thấy chứng từ.')),
                      ],
                    ),
                  )
                : RefreshIndicator(
                    onRefresh: () => notifier.loadDocuments(),
                    child: ListView.builder(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(16),
                      itemCount:
                          state.documents.length +
                          (state.hasMore || state.error != null ? 1 : 0),
                      itemBuilder: (context, index) {
                        if (index == state.documents.length) {
                          if (state.error != null) {
                            return _ErrorState(
                              message: state.error!,
                              onRetry: () => notifier.loadMore(),
                            );
                          }
                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            child: Center(
                              child: state.isLoadingMore
                                  ? const CircularProgressIndicator()
                                  : OutlinedButton(
                                      onPressed: () => notifier.loadMore(),
                                      child: const Text('Tải thêm'),
                                    ),
                            ),
                          );
                        }
                        final document = state.documents[index];
                        return RecentDocumentCard(
                          document: document,
                          onTap: () =>
                              context.push('/documents/${document.id}'),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.push('/documents/upload'),
        backgroundColor: AppColors.primary,
        child: const Icon(Icons.add_a_photo, color: Colors.white),
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;

  const _ErrorState({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.error),
            ),
            const SizedBox(height: 12),
            ElevatedButton(onPressed: onRetry, child: const Text('Thử lại')),
          ],
        ),
      ),
    );
  }
}
