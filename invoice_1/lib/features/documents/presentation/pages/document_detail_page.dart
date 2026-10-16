import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../dashboard/presentation/providers/dashboard_provider.dart';
import '../providers/document_provider.dart';

class DocumentDetailPage extends ConsumerWidget {
  final int documentId;

  const DocumentDetailPage({super.key, required this.documentId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(documentDetailProvider(documentId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Chi tiết chứng từ'),
        actions: [
          IconButton(
            tooltip: 'Xem file',
            icon: const Icon(Icons.visibility_outlined),
            onPressed: () => context.push('/documents/$documentId/preview'),
          ),
          IconButton(
            tooltip: 'Làm mới',
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(documentDetailProvider(documentId)),
          ),
        ],
      ),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stackTrace) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  error.toString(),
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.error),
                ),
                const SizedBox(height: 12),
                ElevatedButton(
                  onPressed: () =>
                      ref.invalidate(documentDetailProvider(documentId)),
                  child: const Text('Thử lại'),
                ),
              ],
            ),
          ),
        ),
        data: (data) {
          final document = data.document;
          final canSubmit =
              document.processingStatus == 'UPLOADED' &&
              document.reviewStatus == 'PENDING';
          final canResubmit =
              document.processingStatus == 'NEED_REVIEW' &&
              (document.reviewStatus == 'REJECTED' ||
                  document.reviewStatus == 'CORRECTED');
          return RefreshIndicator(
            onRefresh: () async =>
                ref.invalidate(documentDetailProvider(documentId)),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  child: Column(
                    children: [
                      _info('ID', document.id.toString()),
                      _info('Tên file gốc', document.originalFileName),
                      _info(
                        'Loại chứng từ',
                        document.documentTypeName ??
                            document.documentType ??
                            document.documentTypeCode ??
                            'Chưa phân loại',
                      ),
                      _info('Trạng thái xử lý', document.processingStatus),
                      _info('Trạng thái rà soát', document.reviewStatus ?? '—'),
                      _info(
                        'Người tải lên',
                        document.uploadedById?.toString() ?? '—',
                      ),
                      _info('Ngày tải lên', _formatDate(document.createdAt)),
                      _info(
                        'Cập nhật lần cuối',
                        _formatDate(document.updatedAt),
                      ),
                      _info(
                        'Kích thước',
                        document.fileSize == null
                            ? '—'
                            : '${(document.fileSize! / 1024).toStringAsFixed(1)} KB',
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () =>
                      context.push('/documents/$documentId/preview'),
                  icon: const Icon(Icons.open_in_new),
                  label: const Text('Xem ảnh / PDF'),
                ),
                if (canResubmit) ...[
                  const SizedBox(height: 8),
                  ElevatedButton.icon(
                    onPressed: () => _runWorkflowAction(
                      context,
                      ref,
                      document.id,
                      'RESUBMIT',
                    ),
                    icon: const Icon(Icons.send),
                    label: const Text('Gửi lại để rà soát'),
                  ),
                ],
                if (canSubmit) ...[
                  const SizedBox(height: 8),
                  ElevatedButton.icon(
                    onPressed: () =>
                        _runWorkflowAction(context, ref, document.id, 'SUBMIT'),
                    icon: const Icon(Icons.send),
                    label: const Text('Gửi kế toán rà soát'),
                  ),
                ],
                if (document.reviewStatus == 'REJECTED' ||
                    document.reviewStatus == 'CORRECTED') ...[
                  const SizedBox(height: 8),
                  const Text(
                    'Backend có API tải phiên bản mới nhưng quyền EMPLOYEE mặc định không có DOCUMENT_UPDATE. '
                    'Yêu cầu quản trị viên cấp quyền trước khi sử dụng chức năng này.',
                    style: TextStyle(color: AppColors.textSecondary),
                  ),
                ],
                if (data.ocr != null) ...[
                  const SizedBox(height: 20),
                  _section('Kết quả OCR', [
                    _info('Ngôn ngữ', data.ocr!['language']?.toString() ?? '—'),
                    _info(
                      'Độ tin cậy',
                      data.ocr!['confidence']?.toString() ?? '—',
                    ),
                    _info('Công cụ', data.ocr!['ocrEngine']?.toString() ?? '—'),
                    _info('Nội dung', data.ocr!['rawText']?.toString() ?? '—'),
                  ]),
                ],
                if (data.classification != null) ...[
                  const SizedBox(height: 20),
                  _section('Phân loại AI', [
                    _info(
                      'Danh mục',
                      data.classification!['category']?.toString() ?? '—',
                    ),
                    _info(
                      'Trạng thái',
                      data.classification!['status']?.toString() ?? '—',
                    ),
                    _info(
                      'Độ tin cậy',
                      data.classification!['confidence']?.toString() ?? '—',
                    ),
                    _info(
                      'Lý do',
                      data.classification!['reason']?.toString() ?? '—',
                    ),
                  ]),
                ],
                if (data.invoice != null) ...[
                  const SizedBox(height: 20),
                  _section('Thông tin hóa đơn', _invoiceRows(data.invoice!)),
                  if (data.invoice!['items'] is List) ...[
                    const SizedBox(height: 8),
                    _section(
                      'Dòng hàng',
                      (data.invoice!['items'] as List)
                          .map(
                            (item) => _info(
                              'Dòng',
                              item is Map
                                  ? [
                                          item['productName'],
                                          item['quantity'],
                                          item['unit'],
                                          item['amount'],
                                        ]
                                        .where((value) => value != null)
                                        .join(' · ')
                                  : item.toString(),
                            ),
                          )
                          .toList(),
                    ),
                  ],
                ],
                if (data.extractedFields.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  _section(
                    'Trường đã trích xuất',
                    data.extractedFields
                        .map(
                          (field) => _info(
                            field is Map
                                ? field['fieldName']?.toString() ?? 'Trường'
                                : 'Trường',
                            field is Map
                                ? field['fieldValue']?.toString() ?? '—'
                                : jsonEncode(field),
                          ),
                        )
                        .toList(),
                  ),
                ],
              ],
            ),
          );
        },
      ),
    );
  }

  static String _formatDate(DateTime? date) {
    return date == null ? '—' : DateFormat('dd/MM/yyyy HH:mm').format(date);
  }

  static Future<void> _runWorkflowAction(
    BuildContext context,
    WidgetRef ref,
    int documentId,
    String action,
  ) async {
    try {
      final updated = await ref
          .read(documentRepositoryProvider)
          .runWorkflowAction(documentId, action);
      ref.invalidate(documentDetailProvider(documentId));
      ref.read(documentListProvider.notifier).loadDocuments();
      ref.read(dashboardProvider.notifier).loadData();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Đã gửi thao tác $action. Trạng thái: ${updated.processingStatus}',
            ),
          ),
        );
      }
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error.toString()),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  static Widget _section(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        Card(child: Column(children: children)),
      ],
    );
  }

  static Widget _info(String label, String value) {
    return ListTile(
      dense: true,
      title: Text(
        label,
        style: const TextStyle(color: AppColors.textSecondary),
      ),
      subtitle: SelectableText(value),
    );
  }

  static List<Widget> _invoiceRows(Map<String, dynamic> invoice) {
    const fields = {
      'invoiceNumber': 'Số hóa đơn',
      'invoiceSeries': 'Ký hiệu',
      'invoiceDate': 'Ngày hóa đơn',
      'sellerName': 'Người bán',
      'sellerTaxCode': 'Mã số thuế người bán',
      'buyerName': 'Người mua',
      'buyerTaxCode': 'Mã số thuế người mua',
      'subtotal': 'Tiền trước thuế',
      'vatAmount': 'Tiền thuế',
      'totalAmount': 'Tổng tiền',
      'paymentMethod': 'Thanh toán',
    };
    return fields.entries
        .where((entry) => invoice[entry.key] != null)
        .map((entry) => _info(entry.value, invoice[entry.key].toString()))
        .toList();
  }
}
