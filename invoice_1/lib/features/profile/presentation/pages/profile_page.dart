import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../shared/widgets/flow_widgets.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../../core/storage/local_database.dart';

class ProfilePage extends ConsumerWidget {
  const ProfilePage({super.key});
  Future<void> _logout(BuildContext context, WidgetRef ref) async {
    await ref.read(authProvider.notifier).logout();
    try {
      await LocalDatabase.instance.clearAllData();
    } catch (_) {
      /* Session is already cleared. */
    }
    if (context.mounted) context.go('/login');
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider).user;
    if (user == null) {
      return const Center(child: Text('Vui lòng đăng nhập lại.'));
    }
    final roles = user.roles.isEmpty ? [user.role ?? 'USER'] : user.roles;
    final role = roles
        .map(
          (r) =>
              const {
                'EMPLOYEE': 'Nhân viên',
                'USER': 'Người dùng',
                'ACCOUNTANT': 'Kế toán',
                'ADMIN': 'Quản trị viên',
              }[r] ??
              r,
        )
        .join(', ');
    final initials = user.fullName
        .trim()
        .split(RegExp(r'\s+'))
        .where((s) => s.isNotEmpty)
        .take(2)
        .map((s) => s[0])
        .join()
        .toUpperCase();
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
        children: [
          const FlowHeader(
            title: 'Tài khoản',
            subtitle: 'Thông tin và bảo mật',
          ),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 31,
                        backgroundColor: AppColors.primarySurface,
                        child: Text(
                          initials,
                          style: const TextStyle(
                            fontSize: 24,
                            color: AppColors.primary,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              user.fullName,
                              style: const TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              '$role • ${roles.join(', ')}',
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 28),
                  Text(
                    user.companyId == null
                        ? 'Chưa có công ty'
                        : 'Mã công ty: ${user.companyId}',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 28),
          _item('Email', user.email ?? 'Chưa cập nhật'),
          if (user.effectiveRole == 'ADMIN')
            _item(
              'Người dùng',
              'Xem trạng thái tài khoản',
              onTap: () => context.push('/admin/users'),
            ),
          _item('Vai trò', role),
          _item(
            'Đổi mật khẩu',
            'Bảo vệ tài khoản',
            onTap: () => context.push('/profile/password'),
          ),
          _item(
            'Đăng xuất',
            'Kết thúc phiên làm việc',
            onTap: () => _logout(context, ref),
          ),
        ],
      ),
    );
  }

  Widget _item(String title, String subtitle, {VoidCallback? onTap}) => Padding(
    padding: const EdgeInsets.only(bottom: 15),
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
              Text(
                title,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                subtitle,
                style: const TextStyle(
                  fontSize: 12,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
