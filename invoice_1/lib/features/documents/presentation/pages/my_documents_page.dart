import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../shared/widgets/flow_widgets.dart';
import '../../../../shared/widgets/document_status_pill.dart';
import '../../../../shared/widgets/role_document_card.dart';
import '../providers/document_provider.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../dashboard/presentation/widgets/recent_document_card.dart';

class MyDocumentsPage extends ConsumerStatefulWidget {
  const MyDocumentsPage({super.key});
  @override
  ConsumerState<MyDocumentsPage> createState() => _MyDocumentsPageState();
}

class _MyDocumentsPageState extends ConsumerState<MyDocumentsPage> {
  final _search = TextEditingController();
  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Widget _option(String label, bool selected, VoidCallback onTap) => Material(
    color: selected ? AppColors.primarySurface : AppColors.background,
    borderRadius: BorderRadius.circular(10),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
            color: selected ? AppColors.primary : AppColors.textPrimary,
          ),
        ),
      ),
    ),
  );
  Future<void> _filter() async {
    final state = ref.read(documentListProvider);
    var processing = state.status ?? '', review = state.review ?? '';
    final applied = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (context) => StatefulBuilder(
        builder: (context, setSheetState) => SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                'Bộ lọc chứng từ',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 28),
              const Text(
                'Trạng thái xử lý',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 16),
              LayoutBuilder(
                builder: (context, constraints) => Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children:
                      [
                            '',
                            'PROCESSING',
                            'NEED_REVIEW',
                            'FAILED',
                            'UPLOADED',
                            'PROCESSED',
                            'COMPLETED',
                          ]
                          .map(
                            (value) => SizedBox(
                              width: (constraints.maxWidth - 10) / 2,
                              child: _option(
                                value.isEmpty
                                    ? 'Tất cả'
                                    : value == 'COMPLETED'
                                    ? 'Hoàn tất xử lý'
                                    : processingLabel(value),
                                processing == value,
                                () => setSheetState(() => processing = value),
                              ),
                            ),
                          )
                          .toList(),
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                'Trạng thái duyệt',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 16),
              ...['', 'PENDING', 'APPROVED', 'CORRECTED', 'REJECTED'].map(
                (value) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: _option(
                    value.isEmpty ? 'Tất cả' : reviewLabel(value),
                    review == value,
                    () => setSheetState(() => review = value),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Áp dụng bộ lọc'),
              ),
            ],
          ),
        ),
      ),
    );
    if (applied == true && mounted) {
      await ref
          .read(documentListProvider.notifier)
          .loadDocuments(
            search: _search.text,
            status: processing,
            review: review,
          );
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(documentListProvider);
    final notifier = ref.read(documentListProvider.notifier);
    final role = ref.watch(authProvider).user?.effectiveRole;
    final staff = role == 'ACCOUNTANT' || role == 'ADMIN';
    if (state.error != null && state.documents.isEmpty) {
      return SafeArea(
        child: FlowError(
          error: state.error!,
          onRetry: () => notifier.loadDocuments(),
        ),
      );
    }
    return SafeArea(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                FlowHeader(
                  title: role == 'ACCOUNTANT'
                      ? 'Kế toán • Hàng đợi'
                      : role == 'ADMIN'
                      ? 'Admin • Chứng từ'
                      : 'Chứng từ',
                  subtitle: staff
                      ? 'Tìm kiếm và theo dõi chứng từ'
                      : 'Theo dõi tiến độ từng chứng từ',
                ),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _search,
                        textInputAction: TextInputAction.search,
                        decoration: InputDecoration(
                          prefixIcon: const Icon(Icons.search, size: 18),
                          hintText: 'Tìm tên tệp chứng từ',
                        ),
                        onSubmitted: (value) =>
                            notifier.loadDocuments(search: value),
                      ),
                    ),
                    const SizedBox(width: 10),
                    SizedBox(
                      width: 70,
                      height: 48,
                      child: FilledButton(
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primarySurface,
                          foregroundColor: AppColors.primary,
                          padding: EdgeInsets.zero,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        onPressed: _filter,
                        child: Text(
                          state.status != null || state.review != null
                              ? 'Lọc •'
                              : 'Lọc',
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
              ],
            ),
          ),
          Expanded(
            child: state.isLoading
                ? const Center(child: CircularProgressIndicator())
                : RefreshIndicator(
                    onRefresh: () => notifier.loadDocuments(),
                    child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                      children: [
                        if (state.documents.isEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: 80),
                            child: Card(
                              child: Padding(
                                padding: const EdgeInsets.all(24),
                                child: Column(
                                  children: [
                                    const SizedBox(height: 20),
                                    const Icon(
                                      Icons.search,
                                      size: 48,
                                      color: AppColors.primary,
                                    ),
                                    const SizedBox(height: 24),
                                    const Text(
                                      'Không tìm thấy chứng từ',
                                      textAlign: TextAlign.center,
                                      style: TextStyle(
                                        fontSize: 18,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                    const SizedBox(height: 16),
                                    const Text(
                                      'Thử thay đổi bộ lọc hoặc từ khóa',
                                      textAlign: TextAlign.center,
                                      style: TextStyle(
                                        fontSize: 13,
                                        color: AppColors.textSecondary,
                                      ),
                                    ),
                                    const SizedBox(height: 32),
                                    SizedBox(
                                      width: double.infinity,
                                      child: ElevatedButton(
                                        onPressed: () {
                                          _search.clear();
                                          notifier.loadDocuments(
                                            search: '',
                                            status: '',
                                            review: '',
                                          );
                                        },
                                        child: const Text('Xóa bộ lọc'),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ...state.documents.map(
                          (doc) => staff
                              ? RoleDocumentCard(
                                  document: doc,
                                  onTap: () =>
                                      context.push('/documents/${doc.id}'),
                                )
                              : RecentDocumentCard(
                                  document: doc,
                                  onTap: () =>
                                      context.push('/documents/${doc.id}'),
                                ),
                        ),
                        if (state.error != null)
                          TextButton(
                            onPressed: () => notifier.loadMore(),
                            child: const Text(
                              'Chưa tải được trang tiếp theo. Thử lại',
                            ),
                          ),
                        if (state.hasMore && state.error == null)
                          Center(
                            child: state.isLoadingMore
                                ? const CircularProgressIndicator()
                                : OutlinedButton(
                                    onPressed: () => notifier.loadMore(),
                                    child: const Text('Tải thêm'),
                                  ),
                          ),
                        if (role == 'ADMIN')
                          const Padding(
                            padding: EdgeInsets.symmetric(vertical: 28),
                            child: Text(
                              'Chỉ xem, không sửa OCR/AI tại đây.',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}
