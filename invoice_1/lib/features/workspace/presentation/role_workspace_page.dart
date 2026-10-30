import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../shared/widgets/flow_widgets.dart';
import '../../../app/theme/app_colors.dart';
import '../../auth/presentation/providers/auth_provider.dart';
import '../../dashboard/presentation/providers/dashboard_provider.dart';
import '../../documents/presentation/providers/document_provider.dart';
import '../../notifications/presentation/pages/notifications_page.dart';
import 'financial_transactions_page.dart';
import '../data/workspace_repository.dart';

class RoleWorkspacePage extends ConsumerWidget {
  const RoleWorkspacePage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(authProvider).user?.effectiveRole;
    if (role == 'ACCOUNTANT') return const FinancialTransactionsPage();
    if (role != 'ADMIN') return const NotificationsPage();
    final dashboard = ref.watch(dashboardProvider);
    final users = ref.watch(workspaceUsersProvider);
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
        children: [
          const FlowHeader(
            title: 'Admin • Giám sát',
            subtitle: 'Cảnh báo và trạng thái hệ thống',
          ),
          if (dashboard.isLoading) const LinearProgressIndicator(),
          if (dashboard.error != null)
            TextButton(
              onPressed: () => ref.read(dashboardProvider.notifier).loadData(),
              child: const Text('Chưa tải được thống kê. Thử lại'),
            ),
          _metric(
            dashboard.summary?.totalFailed,
            'Lỗi OCR/AI',
            AppColors.error,
            AppColors.errorSurface,
            () {
              ref
                  .read(documentListProvider.notifier)
                  .loadDocuments(status: 'FAILED', review: '');
              context.go('/documents');
            },
          ),
          _metric(
            null,
            'Chứng từ quá hạn',
            AppColors.warning,
            AppColors.warningSurface,
            null,
          ),
          _metric(
            null,
            'Chưa phân công kế toán',
            AppColors.info,
            AppColors.infoSurface,
            null,
          ),
          _metric(
            users.asData?.value.where((u) => u.status == 'ACTIVE').length,
            'Người dùng đang hoạt động',
            AppColors.success,
            AppColors.successSurface,
            () => context.push('/admin/users'),
          ),
          if (users.hasError)
            TextButton(
              onPressed: () => ref.invalidate(workspaceUsersProvider),
              child: const Text('Chưa tải được số tài khoản. Thử lại'),
            ),
          const SizedBox(height: 24),
          const Text(
            'Cảnh báo không tự cấp quyền xử lý nghiệp vụ.',
            style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
          ),
          const SizedBox(height: 10),
          const Text(
            'Chỉ số quá hạn và phân công chưa có dữ liệu từ máy chủ.',
            style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
          ),
        ],
      ),
    );
  }

  Widget _metric(
    int? count,
    String label,
    Color color,
    Color surface,
    VoidCallback? onTap,
  ) => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 7,
                ),
                decoration: BoxDecoration(
                  color: surface,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Text(
                  count == null
                      ? 'Chưa có dữ liệu'
                      : '${count.toString().padLeft(2, '0')} trường hợp',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: color,
                  ),
                ),
              ),
              const SizedBox(height: 24),
              Text(
                label,
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
