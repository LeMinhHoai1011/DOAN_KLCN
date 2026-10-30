import 'package:flutter/material.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../shared/widgets/document_status_pill.dart';
import '../../../documents/data/models/document_model.dart';

class RecentDocumentCard extends StatelessWidget {
  final DocumentModel document;
  final VoidCallback onTap;
  const RecentDocumentCard({
    super.key,
    required this.document,
    required this.onTap,
  });
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 42,
                height: 46,
                decoration: BoxDecoration(
                  color: AppColors.primarySurface,
                  borderRadius: BorderRadius.circular(10),
                ),
                alignment: Alignment.center,
                child: const Text(
                  '▤',
                  style: TextStyle(fontSize: 19, color: AppColors.primary),
                ),
              ),
              const SizedBox(width: 11),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      document.originalFileName,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '#${document.id} • ${document.documentTypeName ?? document.documentType ?? 'Chưa phân loại'}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 12),
                    DocumentStatusPill(
                      processing: document.processingStatus,
                      review: document.reviewStatus,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
