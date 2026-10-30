import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/auth/presentation/providers/auth_provider.dart';
import '../../app/theme/app_colors.dart';

class MainScaffold extends ConsumerWidget {
  final StatefulNavigationShell navigationShell;
  const MainScaffold({super.key, required this.navigationShell});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(authProvider).user?.effectiveRole;
    final employee = role == 'EMPLOYEE' || role == 'USER';
    return Scaffold(
      backgroundColor: employee
          ? AppColors.background
          : const Color(0xFFF8FAFC),
      body: navigationShell,
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(top: BorderSide(color: AppColors.divider)),
        ),
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: 69,
            child: Row(
              children: [
                _item(
                  context,
                  0,
                  employee ? '⌂' : '◉',
                  employee ? 'Trang chủ' : 'Tổng quan',
                ),
                _item(
                  context,
                  1,
                  '▤',
                  role == 'ACCOUNTANT' ? 'Chờ xử lý' : 'Chứng từ',
                ),
                if (role == 'EMPLOYEE')
                  SizedBox(
                    width: 64,
                    child: Center(
                      child: Semantics(
                        label: 'Tải chứng từ lên',
                        button: true,
                        child: SizedBox(
                          width: 50,
                          height: 48,
                          child: ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              padding: EdgeInsets.zero,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(16),
                              ),
                            ),
                            onPressed: () => context.push('/documents/upload'),
                            child: const Text(
                              '+',
                              style: TextStyle(fontSize: 29),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                _item(
                  context,
                  2,
                  employee ? '♧' : '▥',
                  role == 'ACCOUNTANT'
                      ? 'Thu chi'
                      : role == 'ADMIN'
                      ? 'Giám sát'
                      : 'Thông báo',
                ),
                _item(context, 3, employee ? '◉' : '♙', 'Tài khoản'),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _item(BuildContext context, int index, String glyph, String label) {
    final usersPage =
        GoRouter.of(context).routeInformationProvider.value.uri.path ==
        '/admin/users';
    final selected = index == (usersPage ? 3 : navigationShell.currentIndex);
    final color = selected ? AppColors.primary : AppColors.textSecondary;
    return Expanded(
      child: InkWell(
        onTap: () => navigationShell.goBranch(index, initialLocation: selected),
        child: SizedBox(
          height: 69,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(glyph, style: TextStyle(fontSize: 20, color: color)),
              const SizedBox(height: 4),
              Text(
                label,
                maxLines: 1,
                style: TextStyle(
                  fontSize: 10,
                  color: color,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
