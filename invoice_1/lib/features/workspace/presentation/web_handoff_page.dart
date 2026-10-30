import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../app/constants/api_constants.dart';
import '../../../app/theme/app_colors.dart';
import '../../../shared/widgets/app_button.dart';
import '../../../shared/widgets/flow_widgets.dart';
import '../../../shared/widgets/document_status_pill.dart';
import '../../documents/presentation/providers/document_provider.dart';

Uri documentWebUri(String base, int id) {
  final uri = Uri.parse(base);
  if (id <= 0 ||
      !['http', 'https'].contains(uri.scheme) ||
      uri.host.isEmpty ||
      uri.userInfo.isNotEmpty) {
    throw const FormatException('Địa chỉ Web chưa hợp lệ.');
  }
  final prefix = uri.path.endsWith('/') ? uri.path : '${uri.path}/';
  return Uri(
    scheme: uri.scheme,
    host: uri.host,
    port: uri.hasPort ? uri.port : null,
    path: '${prefix}accountant/documents/$id',
  );
}

class WebHandoffPage extends ConsumerWidget {
  final int documentId;
  const WebHandoffPage({super.key, required this.documentId});
  Future<void> _open(BuildContext context) async {
    try {
      final opened = await launchUrl(
        documentWebUri(ApiConstants.webAppUrl, documentId),
        mode: LaunchMode.externalApplication,
      );
      if (!opened) throw StateError('No browser');
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Không thể mở Web. Hãy kiểm tra địa chỉ Web và trình duyệt trên thiết bị.',
            ),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(documentDetailProvider(documentId));
    final assessment = ref.watch(documentAssessmentProvider(documentId));
    final reasons = (assessment.asData?.value?['reviewReasons'] as List?) ?? [];
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          children: [
            FlowHeader(
              title: 'Kế toán • Xử lý trên Web',
              subtitle: 'Chứng từ #$documentId',
              trailing: IconButton(
                tooltip: 'Quay lại',
                onPressed: () => context.pop(),
                icon: const Icon(Icons.close, size: 20),
              ),
            ),
            const SizedBox(height: 28),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    DocumentStatusPill(
                      processing:
                          detail.asData?.value.document.processingStatus ??
                          'UNKNOWN',
                      review: detail.asData?.value.document.reviewStatus,
                    ),
                    const SizedBox(height: 32),
                    const Text(
                      'Tiếp tục kiểm tra trên Web',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 24),
                    if (reasons.isNotEmpty)
                      ...reasons.map(
                        (reason) => Padding(
                          padding: const EdgeInsets.only(bottom: 16),
                          child: Text(
                            reason.toString(),
                            style: const TextStyle(
                              fontSize: 14,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ),
                      )
                    else
                      const Text(
                        'Kiểm tra dòng hàng, chỉnh dữ liệu OCR/AI và đối soát trên Web trước khi quyết định duyệt.',
                        style: TextStyle(
                          fontSize: 14,
                          height: 1.8,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    const SizedBox(height: 24),
                    const Text(
                      'Phiên Web yêu cầu đăng nhập riêng. Trạng thái chứng từ vẫn do máy chủ kiểm tra.',
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
            const SizedBox(height: 48),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.primarySurface,
                foregroundColor: AppColors.primary,
                minimumSize: const Size.fromHeight(48),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              onPressed: () async {
                await Clipboard.setData(
                  ClipboardData(text: documentId.toString()),
                );
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Đã sao chép mã chứng từ.')),
                  );
                }
              },
              child: const Text('Sao chép mã chứng từ'),
            ),
            const SizedBox(height: 24),
            AppButton(text: 'Mở trên Web', onPressed: () => _open(context)),
          ],
        ),
      ),
    );
  }
}
