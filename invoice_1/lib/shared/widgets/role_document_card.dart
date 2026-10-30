import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../app/theme/app_colors.dart';
import '../../features/documents/data/models/document_model.dart';
import 'document_status_pill.dart';

class RoleDocumentCard extends StatelessWidget {
  final DocumentModel document;
  final VoidCallback onTap;
  const RoleDocumentCard({
    super.key,
    required this.document,
    required this.onTap,
  });
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
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
                '#${document.id} • ${document.originalFileName}',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                '${document.uploadedById == null ? 'Chưa có thông tin người gửi' : 'Người gửi #${document.uploadedById}'}${document.createdAt == null ? '' : ' • ${DateFormat('dd/MM').format(document.createdAt!)}'}',
                style: const TextStyle(
                  fontSize: 12,
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 12,
                runSpacing: 10,
                alignment: WrapAlignment.spaceBetween,
                children: [
                  Text(
                    document.amount == null
                        ? 'Chưa có tổng tiền'
                        : '${NumberFormat.decimalPattern('vi').format(document.amount)}đ',
                    style: const TextStyle(
                      fontSize: 19,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  DocumentStatusPill(
                    processing: document.processingStatus,
                    review: document.reviewStatus,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
