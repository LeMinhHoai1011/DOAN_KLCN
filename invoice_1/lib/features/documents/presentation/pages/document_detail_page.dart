import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../shared/widgets/flow_widgets.dart';
import '../../../../shared/widgets/document_status_pill.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../dashboard/presentation/providers/dashboard_provider.dart';
import '../../data/models/document_model.dart';
import '../providers/document_provider.dart';
import '../../../workspace/presentation/accountant_review_page.dart';

class DocumentDetailPage extends ConsumerStatefulWidget {
  final int documentId;
  const DocumentDetailPage({super.key, required this.documentId});
  @override
  ConsumerState<DocumentDetailPage> createState() => _DocumentDetailPageState();
}

class _DocumentDetailPageState extends ConsumerState<DocumentDetailPage> {
  bool _busy = false;
  int _tab = 1;
  Future<void> _refresh() async {
    ref.invalidate(documentDetailProvider(widget.documentId));
    ref.invalidate(documentBusinessProvider(widget.documentId));
    await ref.read(documentDetailProvider(widget.documentId).future);
  }

  Future<void> _action(String action, {String? note}) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await ref
          .read(documentRepositoryProvider)
          .runWorkflowAction(widget.documentId, action, note: note);
      await _refresh();
      ref.invalidate(documentListProvider);
      ref.invalidate(dashboardProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã cập nhật trạng thái chứng từ.')),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Chưa thể gửi chứng từ. Kiểm tra quyền và làm mới trạng thái trước khi thử lại.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _date(DateTime? date) =>
      date == null ? '—' : DateFormat('dd/MM/yyyy HH:mm').format(date);

  @override
  Widget build(BuildContext context) {
    if (ref.watch(authProvider).user?.effectiveRole == 'ACCOUNTANT') {
      return AccountantReviewPage(documentId: widget.documentId);
    }
    final detail = ref.watch(documentDetailProvider(widget.documentId));
    final business = ref.watch(documentBusinessProvider(widget.documentId));
    final user = ref.watch(authProvider).user;
    final employee =
        user?.effectiveRole == 'EMPLOYEE' || user?.effectiveRole == 'USER';
    return Scaffold(
      body: SafeArea(
        child: detail.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => FlowError(error: error, onRetry: _refresh),
          data: (data) {
            final doc = data.document;
            final returned =
                doc.reviewStatus == 'REJECTED' ||
                doc.reviewStatus == 'CORRECTED';
            final canResubmit =
                employee && doc.processingStatus == 'NEED_REVIEW' && returned;
            final canSubmit =
                employee &&
                doc.processingStatus == 'UPLOADED' &&
                doc.reviewStatus == 'PENDING';
            final state = business.asData?.value;
            final events = (state?['history'] as List?) ?? const [];
            Map? feedback;
            for (final event in events.reversed) {
              if (event is Map &&
                  (event['status'] == 'REJECTED' ||
                      event['status'] == 'CORRECTED')) {
                feedback = event;
                break;
              }
            }
            final rawNote = feedback?['action']?.toString() ?? '';
            final note = rawNote.contains(':')
                ? rawNote.substring(rawNote.indexOf(':') + 1).trim()
                : '';
            final invoice = data.invoice;
            final items = invoice?['items'] is List
                ? invoice!['items'] as List
                : const [];
            return RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                children: [
                  FlowHeader(
                    title: 'Chi tiết chứng từ',
                    subtitle: '#${doc.id} • ${_date(doc.createdAt)}',
                    trailing: IconButton(
                      tooltip: 'Quay lại',
                      onPressed: () {
                        if (context.canPop()) {
                          context.pop();
                        } else {
                          context.go('/documents');
                        }
                      },
                      icon: const Icon(Icons.close, size: 20),
                    ),
                  ),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            doc.originalFileName,
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 16),
                          DocumentStatusPill(
                            processing: doc.processingStatus,
                            review: doc.reviewStatus,
                          ),
                          const SizedBox(height: 12),
                          Text(
                            'Xử lý: ${processingLabel(doc.processingStatus)}',
                            style: const TextStyle(fontSize: 12),
                          ),
                          const SizedBox(height: 5),
                          Text(
                            'Kiểm duyệt: ${reviewLabel(doc.reviewStatus)}',
                            style: const TextStyle(fontSize: 12),
                          ),
                          const SizedBox(height: 5),
                          Text(
                            'Thanh toán: ${const {'UNPAID': 'Chưa thanh toán', 'PARTIALLY_PAID': 'Thanh toán một phần', 'PAID': 'Đã thanh toán'}[state?['payment']] ?? 'Chưa xác định'}',
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  if (doc.processingStatus == 'PROCESSING') ...[
                    const SizedBox(height: 12),
                    const Card(
                      child: Padding(
                        padding: EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Hệ thống đang xử lý',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            SizedBox(height: 12),
                            LinearProgressIndicator(),
                            SizedBox(height: 12),
                            Text(
                              'Đang trích xuất thông tin. Bạn có thể rời màn hình và kiểm tra lại.',
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                  if (returned) ...[
                    const SizedBox(height: 16),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(18),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Lý do từ kế toán',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 14),
                            Text(
                              note.isEmpty
                                  ? 'Chưa tải được nội dung phản hồi. Hãy làm mới hoặc liên hệ kế toán.'
                                  : note,
                            ),
                            if (feedback?['at'] != null)
                              Padding(
                                padding: const EdgeInsets.only(top: 18),
                                child: Text(
                                  'Phản hồi: ${_date(DateTime.tryParse(feedback!['at'].toString()))}',
                                  style: const TextStyle(
                                    fontSize: 11,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                              ),
                            if (canResubmit) ...[
                              const SizedBox(height: 18),
                              SizedBox(
                                width: double.infinity,
                                child: ElevatedButton(
                                  onPressed: _busy
                                      ? null
                                      : () => context.push(
                                          '/documents/upload',
                                          extra: doc,
                                        ),
                                  child: const Text('Thay tệp và gửi lại'),
                                ),
                              ),
                              TextButton(
                                onPressed: _busy
                                    ? null
                                    : () => _action('RESUBMIT'),
                                child: const Text('Gửi lại tệp hiện tại'),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 24),
                  Row(
                    children: [
                      for (final entry in [(0, 'Tài liệu'), (1, 'Thông tin')])
                        TextButton(
                          onPressed: () => setState(() => _tab = entry.$1),
                          child: Text(
                            entry.$2,
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                              color: _tab == entry.$1
                                  ? AppColors.primary
                                  : AppColors.textPrimary,
                            ),
                          ),
                        ),
                    ],
                  ),
                  const Divider(height: 2, color: AppColors.primary),
                  const SizedBox(height: 20),
                  if (_tab == 0)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          children: [
                            const Text(
                              '▤',
                              style: TextStyle(
                                fontSize: 48,
                                color: AppColors.primary,
                              ),
                            ),
                            const SizedBox(height: 12),
                            Text(
                              doc.originalFileName,
                              textAlign: TextAlign.center,
                            ),
                            const SizedBox(height: 12),
                            Text(
                              doc.fileSize == null
                                  ? 'Chưa có dung lượng'
                                  : '${(doc.fileSize! / 1024 / 1024).toStringAsFixed(2)} MB',
                            ),
                            const SizedBox(height: 16),
                            _previewButton(doc),
                          ],
                        ),
                      ),
                    ),
                  if (_tab == 1) ...[
                    _section(
                      'Thông tin hóa đơn',
                      invoice == null
                          ? 'Chưa có thông tin hóa đơn'
                          : 'Số: ${invoice['invoiceNumber'] ?? '—'} • Ngày: ${invoice['invoiceDate'] ?? '—'}',
                      invoice == null
                          ? [const Text('Chưa có dữ liệu từ máy chủ.')]
                          : _invoiceRows(invoice),
                    ),
                    _section(
                      'Dữ liệu AI / OCR',
                      'Xem nội dung trích xuất và độ tin cậy',
                      [
                        if (data.ocr != null) ...[
                          _row('Độ tin cậy OCR', data.ocr!['confidence']),
                          _row('Ngôn ngữ', data.ocr!['language']),
                          _row('Nội dung', data.ocr!['rawText']),
                        ],
                        if (data.classification != null) ...[
                          _row('Danh mục', data.classification!['category']),
                          _row(
                            'Độ tin cậy AI',
                            data.classification!['confidence'],
                          ),
                          _row('Lý do', data.classification!['reason']),
                        ],
                        for (final field in data.extractedFields)
                          if (field is Map)
                            _row(
                              field['fieldName']?.toString() ?? 'Trường',
                              field['fieldValue'],
                            ),
                        if (data.ocr == null &&
                            data.classification == null &&
                            data.extractedFields.isEmpty)
                          const Text('Chưa có dữ liệu trích xuất.'),
                      ],
                    ),
                    _section('Dòng hàng', '${items.length} dòng', [
                      if (items.isEmpty)
                        const Text('Chưa có dữ liệu dòng hàng.'),
                      for (final item in items)
                        if (item is Map)
                          _row(
                            item['productName']?.toString() ?? 'Dòng hàng',
                            '${item['quantity'] ?? '—'} ${item['unit'] ?? ''} • ${item['amount'] ?? '—'}',
                          ),
                    ]),
                    _section('Lịch sử xử lý', 'Lần gửi, phản hồi và cập nhật', [
                      if (business.isLoading) const LinearProgressIndicator(),
                      if (business.hasError)
                        TextButton(
                          onPressed: () => ref.invalidate(
                            documentBusinessProvider(widget.documentId),
                          ),
                          child: const Text('Chưa tải được lịch sử. Thử lại'),
                        ),
                      for (final event in events)
                        if (event is Map)
                          _row(
                            _eventLabel(event['action']?.toString() ?? ''),
                            _date(
                              DateTime.tryParse(event['at']?.toString() ?? ''),
                            ),
                          ),
                      if (!business.isLoading &&
                          !business.hasError &&
                          events.isEmpty)
                        const Text('Chưa có lịch sử xử lý.'),
                      _row('Tải lên', _date(doc.createdAt)),
                      _row('Cập nhật', _date(doc.updatedAt)),
                    ]),
                    const SizedBox(height: 28),
                    _previewButton(doc),
                  ],
                  if (canSubmit) ...[
                    const SizedBox(height: 12),
                    ElevatedButton(
                      onPressed: _busy ? null : () => _action('SUBMIT'),
                      child: Text(_busy ? 'Đang gửi…' : 'Gửi kế toán rà soát'),
                    ),
                  ],
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _previewButton(DocumentModel doc) => SizedBox(
    width: double.infinity,
    child: ElevatedButton(
      onPressed: () => context.push('/documents/${doc.id}/preview'),
      child: const Text('Xem tệp gốc'),
    ),
  );
  Widget _section(String title, String subtitle, List<Widget> children) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Card(
          child: ExpansionTile(
            shape: const Border(),
            collapsedShape: const Border(),
            tilePadding: const EdgeInsets.symmetric(horizontal: 18),
            title: Text(
              title,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
            ),
            subtitle: Text(
              subtitle,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.textSecondary,
              ),
            ),
            childrenPadding: const EdgeInsets.fromLTRB(18, 8, 18, 18),
            expandedCrossAxisAlignment: CrossAxisAlignment.start,
            children: children,
          ),
        ),
      );
  Widget _row(String title, Object? value) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
        ),
        const SizedBox(height: 4),
        SelectableText(
          value?.toString() ?? '—',
          style: const TextStyle(fontSize: 14, color: AppColors.textPrimary),
        ),
      ],
    ),
  );
  List<Widget> _invoiceRows(Map<String, dynamic> invoice) => {
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
    'paymentMethod': 'Phương thức thanh toán',
  }.entries.map((e) => _row(e.value, invoice[e.key])).toList();
  String _eventLabel(String action) {
    final parts = action.split(':');
    final label =
        const {
          'SUBMIT': 'Gửi xử lý',
          'RESUBMIT': 'Gửi lại',
          'START_REVIEW': 'Bắt đầu duyệt',
          'APPROVE': 'Đã duyệt',
          'REJECT': 'Từ chối',
          'REQUEST_INFO': 'Yêu cầu bổ sung',
        }[parts.first] ??
        'Cập nhật chứng từ';
    return parts.length > 1
        ? '$label: ${parts.skip(1).join(':').trim()}'
        : label;
  }
}
