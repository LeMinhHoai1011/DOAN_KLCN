import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../../../shared/widgets/flow_widgets.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../app/theme/app_colors.dart';

class NotificationsPage extends StatelessWidget {
  const NotificationsPage({super.key});
  @override
  Widget build(BuildContext context) => SafeArea(
    child: ListView(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      children: [
        const FlowHeader(
          title: 'Thông báo',
          subtitle: 'Tính năng đang được phát triển',
        ),
        const SizedBox(height: 72),
        const Card(
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: 24, vertical: 44),
            child: Column(
              children: [
                Text(
                  '♧',
                  style: TextStyle(fontSize: 48, color: AppColors.primary),
                ),
                SizedBox(height: 24),
                Text(
                  'Chưa có thông báo',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                ),
                SizedBox(height: 24),
                Text(
                  'Tính năng thông báo đang được phát triển.\nBạn vẫn có thể xem trạng thái chứng từ trong danh sách chứng từ.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 13,
                    height: 1.7,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 32),
        AppButton(
          text: 'Đến danh sách chứng từ',
          onPressed: () => context.go('/documents'),
        ),
      ],
    ),
  );
}
