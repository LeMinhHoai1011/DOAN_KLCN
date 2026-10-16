import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../providers/dashboard_provider.dart';
import '../widgets/summary_card.dart';
import '../widgets/recent_document_card.dart';

class DashboardPage extends ConsumerWidget {
  const DashboardPage({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(dashboardProvider);
    final authState = ref.watch(authProvider);
    final userName = authState.user?.fullName ?? 'Nhân viên';

    if (state.isLoading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    if (state.error != null) {
      return Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: AppColors.error),
              const SizedBox(height: 16),
              Text(state.error!, style: const TextStyle(color: AppColors.error)),
              const SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: () => ref.read(dashboardProvider.notifier).loadData(),
                icon: const Icon(Icons.refresh),
                label: const Text('Thử lại'),
              ),
            ],
          ),
        ),
      );
    }

    final summary = state.summary;
    final recentDocs = state.recentDocuments;
    if (summary == null) {
      return const Scaffold(
        body: Center(child: Text('Chưa có dữ liệu tổng quan từ máy chủ.')),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Tổng quan'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.read(dashboardProvider.notifier).loadData(),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(dashboardProvider.notifier).loadData(),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Xin chào, $userName',
              style: const TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 4),
            const Text(
              'Đây là tổng quan chứng từ của bạn',
              style: TextStyle(fontSize: 14, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 24),
            GridView.count(
              crossAxisCount: 2,
              crossAxisSpacing: 16,
              mainAxisSpacing: 16,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              childAspectRatio: 1.5,
              children: [
                SummaryCard(title: 'Tổng chứng từ', count: summary.totalDocuments, color: AppColors.primary, icon: Icons.folder),
                SummaryCard(title: 'Hóa đơn', count: summary.totalInvoices, color: AppColors.primary, icon: Icons.receipt_long),
                SummaryCard(title: 'Đã phân loại', count: summary.totalClassified, color: AppColors.success, icon: Icons.category_outlined),
                SummaryCard(title: 'Cần kiểm tra', count: summary.totalReviewRequired, color: AppColors.warning, icon: Icons.pending_actions),
                SummaryCard(title: 'Thất bại', count: summary.totalFailed, color: AppColors.error, icon: Icons.error_outline),
              ],
            ),
            if (summary.documentsByStatus.isNotEmpty) ...[
              const SizedBox(height: 20),
              const Text(
                'Theo trạng thái xử lý',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: summary.documentsByStatus
                    .map((item) => Chip(
                          label: Text('${item.status}: ${item.count}'),
                        ))
                    .toList(),
              ),
            ],
            const SizedBox(height: 32),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Chứng từ gần đây',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary,
                  ),
                ),
                TextButton(
                  onPressed: () {
                    // Chuyển sang tab Chứng từ
                    context.go('/documents');
                  },
                  child: const Text('Xem tất cả'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (recentDocs.isEmpty)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(32.0),
                  child: Column(
                    children: [
                      Icon(Icons.description_outlined, size: 48, color: AppColors.textSecondary),
                      SizedBox(height: 8),
                      Text(
                        'Bạn chưa tải lên chứng từ nào.',
                        style: TextStyle(color: AppColors.textSecondary),
                      ),
                    ],
                  ),
                ),
              )
            else
              ...recentDocs.map((doc) => RecentDocumentCard(
                    document: doc,
                    onTap: () {
                      context.push('/documents/${doc.id}');
                    },
                  )),
          ],
        ),
      ),
    );
  }
}
