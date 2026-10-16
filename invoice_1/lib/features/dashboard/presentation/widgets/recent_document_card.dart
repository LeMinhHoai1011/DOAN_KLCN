import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../documents/data/models/document_model.dart';

class RecentDocumentCard extends StatelessWidget {
  final DocumentModel document;
  final VoidCallback onTap;

  const RecentDocumentCard({
    Key? key,
    required this.document,
    required this.onTap,
  }) : super(key: key);

  String _getStatusText(String? reviewStatus, String processingStatus) {
    if (processingStatus == 'PENDING' ||
        processingStatus == 'UPLOADED' ||
        processingStatus == 'PROCESSING') {
      return 'Chờ xử lý';
    }
    switch (reviewStatus) {
      case 'PENDING':
      case 'NEED_REVIEW':
        return 'Chờ rà soát';
      case 'REJECTED':
      case 'NEED_UPDATE':
        return 'Cần cập nhật';
      case 'APPROVED':
      case 'PROCESSED':
      case 'COMPLETED':
        return 'Hoàn tất';
      default:
        return 'Chưa rõ';
    }
  }

  Color _getStatusColor(String text) {
    switch (text) {
      case 'Chờ xử lý':
      case 'Chờ rà soát':
        return AppColors.warning;
      case 'Cần cập nhật':
        return AppColors.error;
      case 'Hoàn tất':
        return AppColors.success;
      default:
        return AppColors.textSecondary;
    }
  }

  @override
  Widget build(BuildContext context) {
    final statusText = _getStatusText(document.reviewStatus, document.processingStatus);
    final statusColor = _getStatusColor(statusText);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        onTap: onTap,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: AppColors.primary.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.description, color: AppColors.primary),
        ),
        title: Text(
          document.originalFileName,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 4),
            Text(
              document.createdAt == null
                  ? 'Không có ngày tải lên'
                  : DateFormat('dd/MM/yyyy HH:mm').format(document.createdAt!),
              style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Icon(Icons.circle, size: 8, color: statusColor),
                const SizedBox(width: 4),
                Text(
                  statusText,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: statusColor,
                  ),
                ),
              ],
            )
          ],
        ),
        trailing: const Icon(Icons.chevron_right, color: AppColors.textSecondary),
      ),
    );
  }
}
