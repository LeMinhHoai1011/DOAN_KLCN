import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../app/theme/app_colors.dart';
import '../../core/network/api_client.dart';

class FlowHeader extends StatelessWidget {
  final String title, subtitle;
  final Widget? trailing;
  const FlowHeader({
    super.key,
    required this.title,
    required this.subtitle,
    this.trailing,
  });
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 20, bottom: 24),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
              ),
            ),
            ?trailing,
          ],
        ),
        const SizedBox(height: 5),
        Text(
          subtitle,
          style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
        ),
      ],
    ),
  );
}

class FlowError extends StatelessWidget {
  final Object error;
  final VoidCallback onRetry;
  final String deniedTitle;
  const FlowError({
    super.key,
    required this.error,
    required this.onRetry,
    this.deniedTitle = 'Không thể truy cập dữ liệu',
  });
  @override
  Widget build(BuildContext context) {
    final denied =
        error is ApiException && (error as ApiException).statusCode == 403;
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            FlowHeader(
              title: denied
                  ? 'Không có quyền truy cập'
                  : 'Không thể tải dữ liệu',
              subtitle: denied
                  ? 'Quyền thao tác phụ thuộc vai trò'
                  : 'Có thể do mạng hoặc máy chủ',
            ),
            const SizedBox(height: 72),
            Card(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 40,
                ),
                child: Column(
                  crossAxisAlignment: denied
                      ? CrossAxisAlignment.center
                      : CrossAxisAlignment.start,
                  children: [
                    if (denied)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 20),
                        child: Icon(
                          Icons.block_outlined,
                          size: 40,
                          color: AppColors.error,
                        ),
                      )
                    else
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 7,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.errorSurface,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: const Text(
                          'Không thể kết nối',
                          style: TextStyle(
                            fontSize: 12,
                            color: AppColors.error,
                          ),
                        ),
                      ),
                    const SizedBox(height: 32),
                    Text(
                      denied ? deniedTitle : 'Dữ liệu chưa tải được',
                      textAlign: denied ? TextAlign.center : TextAlign.start,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      denied
                          ? 'Tài khoản này chưa được cấp quyền thực hiện thao tác. Liên hệ quản trị viên để được hỗ trợ.'
                          : 'Kiểm tra kết nối và thử lại.',
                      textAlign: denied ? TextAlign.center : TextAlign.start,
                      style: const TextStyle(
                        fontSize: 13,
                        height: 1.7,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 28),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 40),
            ElevatedButton(
              onPressed: denied
                  ? () {
                      final router = GoRouter.maybeOf(context);
                      if (router != null) {
                        router.go('/dashboard');
                      } else {
                        onRetry();
                      }
                    }
                  : onRetry,
              child: Text(denied ? 'Quay về trang chủ' : 'Thử lại'),
            ),
          ],
        ),
      ),
    );
  }
}
