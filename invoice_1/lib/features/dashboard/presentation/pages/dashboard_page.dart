import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../shared/widgets/flow_widgets.dart';
import '../../../../shared/widgets/role_document_card.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../workspace/data/workspace_repository.dart';
import '../providers/dashboard_provider.dart';
import '../widgets/recent_document_card.dart';

class DashboardPage extends ConsumerWidget {
  const DashboardPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(dashboardProvider);
    final user = ref.watch(authProvider).user;
    final role = user?.effectiveRole;
    final admin = role == 'ADMIN', accountant = role == 'ACCOUNTANT';
    final now = DateTime.now();
    final range = (
      from: DateFormat('yyyy-MM-dd').format(DateTime(now.year, now.month)),
      to: DateFormat('yyyy-MM-dd').format(DateTime(now.year, now.month + 1, 0)),
    );
    final finance = accountant
        ? ref.watch(financialTotalsProvider(range))
        : null;
    Future<void> reload() async {
      if (accountant) ref.invalidate(financialTotalsProvider(range));
      await ref.read(dashboardProvider.notifier).loadData();
    }

    if (state.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.error != null) {
      return SafeArea(
        child: FlowError(error: state.error!, onRetry: reload),
      );
    }
    final summary = state.summary;
    if (summary == null) {
      return const Center(child: Text('Chưa có dữ liệu tổng quan.'));
    }
    final distributionKnown =
        summary.totalDocuments == 0 || summary.documentsByStatus.isNotEmpty;
    final returned =
        summary.correctionCount == null || summary.rejectedCount == null
        ? null
        : summary.correctionCount! + summary.rejectedCount!;
    final List<(String, int?, Color)> tiles = admin
        ? [
            ('Tổng chứng từ', summary.totalDocuments, AppColors.primary),
            ('Lỗi xử lý', summary.totalFailed, AppColors.error),
            ('Chờ kế toán', summary.totalReviewRequired, AppColors.primary),
            ('Quá hạn', null, AppColors.primary),
          ]
        : accountant
        ? [
            (
              'Chờ xử lý',
              distributionKnown
                  ? summary.countStatus('UPLOADED') +
                        summary.countStatus('PROCESSING')
                  : null,
              AppColors.primary,
            ),
            ('Cần kiểm tra', summary.totalReviewRequired, AppColors.warning),
            ('Đã duyệt', summary.approvedCount, AppColors.primary),
            ('Bị trả lại', returned, AppColors.primary),
          ]
        : [
            ('Tổng chứng từ', summary.totalDocuments, AppColors.primary),
            ('Chờ kiểm tra', summary.totalReviewRequired, AppColors.primary),
            ('Đã duyệt', summary.approvedCount, AppColors.primary),
            ('Cần bổ sung', summary.correctionCount, AppColors.warning),
          ];
    return SafeArea(
      child: RefreshIndicator(
        onRefresh: reload,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          children: [
            FlowHeader(
              title: admin
                  ? 'Admin • Tổng quan'
                  : accountant
                  ? 'Kế toán • Tổng quan'
                  : 'Xin chào, ${user?.fullName ?? 'nhân viên'} 👋',
              subtitle: admin
                  ? 'Theo dõi tình trạng chứng từ toàn hệ thống'
                  : accountant
                  ? 'Chứng từ và dòng tiền trong phạm vi công ty'
                  : 'Tổng quan chứng từ của bạn',
            ),
            LayoutBuilder(
              builder: (context, constraints) => Wrap(
                spacing: 10,
                runSpacing: 12,
                children: tiles
                    .map(
                      (item) => SizedBox(
                        width: (constraints.maxWidth - 10) / 2,
                        child: _metric(
                          item.$1,
                          item.$2?.toString().padLeft(2, '0') ?? '—',
                          item.$3,
                        ),
                      ),
                    )
                    .toList(),
              ),
            ),
            if (admin) ...[
              const SizedBox(height: 38),
              const Text(
                'Phân bố chứng từ',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 18),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(18),
                  child: !distributionKnown
                      ? const Text('Chưa có dữ liệu phân bố trạng thái.')
                      : Column(
                          children: [
                            _distribution(
                              'Hoàn tất',
                              summary.countStatus('COMPLETED'),
                              summary.totalDocuments,
                              AppColors.success,
                            ),
                            _distribution(
                              'Chờ xử lý',
                              summary.countStatus('UPLOADED') +
                                  summary.countStatus('PROCESSING'),
                              summary.totalDocuments,
                              AppColors.primary,
                            ),
                            _distribution(
                              'Lỗi',
                              summary.totalFailed,
                              summary.totalDocuments,
                              AppColors.error,
                            ),
                          ],
                        ),
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                'Theo dõi hệ thống; không duyệt thay kế toán.',
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
              ),
              const SizedBox(height: 10),
              const Text(
                'Quá hạn: chưa có dữ liệu từ máy chủ.',
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
              ),
            ] else ...[
              if (accountant) ...[
                const SizedBox(height: 36),
                Text(
                  'Thu chi nhỏ lẻ • tháng ${now.month}',
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 18),
                if (finance!.isLoading) const LinearProgressIndicator(),
                if (finance.hasError)
                  TextButton(
                    onPressed: () =>
                        ref.invalidate(financialTotalsProvider(range)),
                    child: const Text('Chưa tải được tổng thu chi. Thử lại'),
                  ),
                if (finance.asData?.value != null)
                  LayoutBuilder(
                    builder: (context, constraints) => Wrap(
                      spacing: 10,
                      runSpacing: 12,
                      children:
                          [
                            ('Tổng thu', 'totalRevenue', AppColors.success),
                            ('Tổng chi', 'totalExpense', AppColors.error),
                          ].map((entry) {
                            final value = finance.asData!.value[entry.$2];
                            final number = value is num
                                ? value
                                : num.tryParse(value?.toString() ?? '');
                            return SizedBox(
                              width: (constraints.maxWidth - 10) / 2,
                              child: _metric(
                                entry.$1,
                                number == null
                                    ? '—'
                                    : '${NumberFormat.decimalPattern('vi').format(number)}đ',
                                entry.$3,
                                small: true,
                              ),
                            );
                          }).toList(),
                    ),
                  ),
              ],
              const SizedBox(height: 38),
              Text(
                accountant ? 'Cần xử lý gần đây' : 'Chứng từ gần đây',
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 16),
              if (state.recentDocuments.isEmpty)
                const Card(
                  child: Padding(
                    padding: EdgeInsets.all(28),
                    child: Text(
                      'Chưa có chứng từ.',
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
              ...state.recentDocuments.map(
                (doc) => accountant
                    ? RoleDocumentCard(
                        document: doc,
                        onTap: () => context.push('/documents/${doc.id}'),
                      )
                    : RecentDocumentCard(
                        document: doc,
                        onTap: () => context.push('/documents/${doc.id}'),
                      ),
              ),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () => context.go('/documents'),
                  child: const Text('Xem tất cả chứng từ  →'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _metric(
    String title,
    String value,
    Color color, {
    bool small = false,
  }) => Card(
    child: Padding(
      padding: const EdgeInsets.all(15),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (small) ...[
            Text(
              title,
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 22),
          ],
          Text(
            value,
            style: TextStyle(
              fontSize: small ? 21 : 27,
              fontWeight: FontWeight.w600,
              color: color,
            ),
          ),
          if (!small) ...[
            const SizedBox(height: 15),
            Text(
              title,
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ],
      ),
    ),
  );
  Widget _distribution(String label, int count, int total, Color color) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 14),
        child: Row(
          children: [
            SizedBox(
              width: 96,
              child: Text(
                label,
                style: const TextStyle(
                  fontSize: 12,
                  color: AppColors.textSecondary,
                ),
              ),
            ),
            Expanded(
              child: Semantics(
                label: '$label: $count',
                child: LinearProgressIndicator(
                  value: total == 0 ? 0 : (count / total).clamp(0, 1),
                  minHeight: 12,
                  color: color,
                  backgroundColor: AppColors.background,
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
          ],
        ),
      );
}
