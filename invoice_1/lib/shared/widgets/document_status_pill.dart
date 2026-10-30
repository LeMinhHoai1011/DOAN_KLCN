import 'package:flutter/material.dart';
import '../../app/theme/app_colors.dart';

String processingLabel(String status) =>
    const {
      'UPLOADED': 'Đã tải lên',
      'PROCESSING': 'Đang xử lý',
      'PROCESSED': 'Đã xử lý',
      'NEED_REVIEW': 'Cần kiểm tra',
      'COMPLETED': 'Đã xử lý',
      'FAILED': 'Xử lý lỗi',
    }[status] ??
    'Chưa xác định';

String reviewLabel(String? status) =>
    const {
      'PENDING': 'Chờ duyệt',
      'APPROVED': 'Đã duyệt',
      'REJECTED': 'Bị từ chối',
      'CORRECTED': 'Yêu cầu bổ sung',
    }[status] ??
    'Chưa xác định';

({String label, Color ink, Color surface}) documentStatus(
  String processing,
  String? review,
) {
  if (processing == 'FAILED') {
    return (
      label: 'Xử lý lỗi',
      ink: AppColors.error,
      surface: AppColors.errorSurface,
    );
  }
  if (review == 'CORRECTED') {
    return (
      label: 'Cần bổ sung',
      ink: AppColors.warning,
      surface: AppColors.warningSurface,
    );
  }
  if (review == 'REJECTED') {
    return (
      label: 'Bị từ chối',
      ink: AppColors.error,
      surface: AppColors.errorSurface,
    );
  }
  if (review == 'APPROVED') {
    return (
      label: 'Đã duyệt',
      ink: AppColors.success,
      surface: AppColors.successSurface,
    );
  }
  if (processing == 'PROCESSING') {
    return (
      label: 'Đang xử lý',
      ink: AppColors.info,
      surface: AppColors.infoSurface,
    );
  }
  if (['NEED_REVIEW', 'PROCESSED', 'COMPLETED'].contains(processing)) {
    return (
      label: 'Chờ kiểm tra',
      ink: AppColors.warning,
      surface: AppColors.warningSurface,
    );
  }
  return (
    label: processingLabel(processing),
    ink: AppColors.textSecondary,
    surface: AppColors.background,
  );
}

class DocumentStatusPill extends StatelessWidget {
  final String processing;
  final String? review;
  const DocumentStatusPill({super.key, required this.processing, this.review});
  @override
  Widget build(BuildContext context) {
    final status = documentStatus(processing, review);
    return Container(
      constraints: const BoxConstraints(minWidth: 130),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: status.surface,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Text(
        status.label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: status.ink,
        ),
      ),
    );
  }
}
