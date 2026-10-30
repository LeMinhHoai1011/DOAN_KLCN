import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../app/theme/app_colors.dart';
import '../../../shared/widgets/flow_widgets.dart';
import '../data/workspace_repository.dart';

class AdminUsersPage extends ConsumerStatefulWidget {
  const AdminUsersPage({super.key});
  @override
  ConsumerState<AdminUsersPage> createState() => _AdminUsersPageState();
}

class _AdminUsersPageState extends ConsumerState<AdminUsersPage> {
  String _search = '';
  String _status(String status) =>
      const {'ACTIVE': 'Hoạt động', 'INACTIVE': 'Ngừng hoạt động'}[status] ??
      'Chưa xác định';
  String _roles(List<String> roles) => roles
      .map(
        (r) =>
            const {
              'ADMIN': 'Quản trị viên',
              'ACCOUNTANT': 'Kế toán',
              'EMPLOYEE': 'Nhân viên',
              'USER': 'Người dùng',
            }[r] ??
            r,
      )
      .join(', ');
  @override
  Widget build(BuildContext context) {
    final users = ref.watch(workspaceUsersProvider);
    return SafeArea(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Column(
              children: [
                const FlowHeader(
                  title: 'Admin • Người dùng',
                  subtitle: 'Theo dõi trạng thái tài khoản',
                ),
                TextField(
                  onChanged: (value) => setState(() => _search = value),
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.search, size: 18),
                    hintText: 'Tìm người dùng',
                  ),
                ),
                const SizedBox(height: 24),
              ],
            ),
          ),
          Expanded(
            child: users.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, _) => FlowError(
                error: error,
                onRetry: () => ref.invalidate(workspaceUsersProvider),
              ),
              data: (data) {
                final filtered = data
                    .where(
                      (u) => '${u.fullName} ${u.username} ${u.email ?? ''}'
                          .toLowerCase()
                          .contains(_search.toLowerCase()),
                    )
                    .toList();
                return RefreshIndicator(
                  onRefresh: () async {
                    ref.invalidate(workspaceUsersProvider);
                    await ref.read(workspaceUsersProvider.future);
                  },
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    children: [
                      ...filtered.map(
                        (user) => Padding(
                          padding: const EdgeInsets.only(bottom: 18),
                          child: Material(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(16),
                            clipBehavior: Clip.antiAlias,
                            child: InkWell(
                              onTap: () => showModalBottomSheet<void>(
                                context: context,
                                useSafeArea: true,
                                builder: (context) => SingleChildScrollView(
                                  padding: const EdgeInsets.all(24),
                                  child: Column(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        user.fullName,
                                        style: const TextStyle(
                                          fontSize: 22,
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                      const SizedBox(height: 16),
                                      Text('@${user.username}'),
                                      const SizedBox(height: 12),
                                      Text(user.email ?? 'Chưa có email'),
                                      const SizedBox(height: 12),
                                      Text('Vai trò: ${_roles(user.roles)}'),
                                      const SizedBox(height: 12),
                                      Text(
                                        'Trạng thái: ${_status(user.status)}',
                                      ),
                                      const SizedBox(height: 24),
                                      const Text(
                                        'Quản lý và phân quyền chi tiết trên Web.',
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Row(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const CircleAvatar(
                                      radius: 25,
                                      backgroundColor: AppColors.primarySurface,
                                      child: Text(
                                        '◉',
                                        style: TextStyle(
                                          fontSize: 22,
                                          color: AppColors.primary,
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            user.fullName,
                                            style: const TextStyle(
                                              fontSize: 16,
                                              fontWeight: FontWeight.w600,
                                            ),
                                          ),
                                          const SizedBox(height: 12),
                                          Text(
                                            '${_roles(user.roles)} • ${_status(user.status)}',
                                            style: const TextStyle(
                                              fontSize: 13,
                                              color: AppColors.textSecondary,
                                            ),
                                          ),
                                          const SizedBox(height: 18),
                                          const Text(
                                            'Xem trạng thái tài khoản ›',
                                            style: TextStyle(
                                              fontSize: 13,
                                              color: AppColors.primary,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                      if (filtered.isEmpty)
                        const Padding(
                          padding: EdgeInsets.all(32),
                          child: Text('Không tìm thấy người dùng.'),
                        ),
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 24),
                        child: Text(
                          'Phân quyền chi tiết thực hiện trên Web.',
                          style: TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary,
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
