import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../shared/widgets/flow_widgets.dart';
import '../../../shared/widgets/document_status_pill.dart';
import '../../documents/presentation/providers/document_provider.dart';
import '../../dashboard/presentation/providers/dashboard_provider.dart';

class AccountantReviewPage extends ConsumerStatefulWidget {
  final int documentId;
  const AccountantReviewPage({super.key, required this.documentId});
  @override
  ConsumerState<AccountantReviewPage> createState() =>
      _AccountantReviewPageState();
}

class _AccountantReviewPageState extends ConsumerState<AccountantReviewPage> {
  bool _busy = false;
  Future<void> _refresh() async {
    ref.invalidate(documentDetailProvider(widget.documentId));
    ref.invalidate(documentBusinessProvider(widget.documentId));
    ref.invalidate(documentAssessmentProvider(widget.documentId));
    await ref.read(documentDetailProvider(widget.documentId).future);
  }

  Future<void> _run(String action, {String? note}) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await ref
          .read(documentRepositoryProvider)
          .runWorkflowAction(widget.documentId, action, note: note);
      if (!mounted) return;
      ref.invalidate(documentListProvider);
      ref.invalidate(dashboardProvider);
      await _refresh();
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
              'Không thể cập nhật. Hãy làm mới chứng từ và kiểm tra quyền thao tác.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _note(String action) async {
    final note = await Navigator.of(context).push<String>(
      MaterialPageRoute(builder: (_) => ReviewNotePage(action: action)),
    );
    if (note != null && mounted) await _run(action, note: note);
  }

  Future<void> _approve() async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Duyệt chứng từ?'),
        content: const Text(
          'Chứng từ sẽ được khóa. Duyệt không đồng nghĩa đã thanh toán.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Hủy'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận'),
          ),
        ],
      ),
    );
    if (yes == true && mounted) await _run('APPROVE');
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(documentDetailProvider(widget.documentId));
    final business = ref.watch(documentBusinessProvider(widget.documentId));
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: detail.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => FlowError(error: error, onRetry: _refresh),
          data: (data) {
            final doc = data.document;
            final eligible =
                doc.reviewStatus == 'PENDING' &&
                ['PROCESSED', 'NEED_REVIEW'].contains(doc.processingStatus);
            final total = data.invoice?['totalAmount'] ?? doc.amount;
            final number = total is num
                ? total
                : num.tryParse(total?.toString() ?? '');
            final recon = business.asData?.value?['reconciliation']?.toString();
            final reconciliation =
                const {
                  'MATCHED': 'Khớp',
                  'MISMATCH': 'Sai lệch — cần kiểm tra trên Web',
                  'WARNING': 'Có cảnh báo — cần kiểm tra trên Web',
                  'NOT_RUN': 'Chưa đối soát',
                }[recon] ??
                'Chưa có dữ liệu';
            return Column(
              children: [
                Expanded(
                  child: RefreshIndicator(
                    onRefresh: _refresh,
                    child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                      children: [
                        FlowHeader(
                          title: 'Kế toán • Chi tiết duyệt',
                          subtitle: 'Kiểm tra chứng từ #${doc.id}',
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
                        const SizedBox(height: 16),
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(20),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '#${doc.id} • ${doc.originalFileName}',
                                  style: const TextStyle(
                                    fontSize: 18,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(height: 22),
                                DocumentStatusPill(
                                  processing: doc.processingStatus,
                                  review: doc.reviewStatus,
                                ),
                                const SizedBox(height: 22),
                                Text(
                                  doc.uploadedById == null
                                      ? 'Chưa có thông tin người gửi'
                                      : 'Người gửi: #${doc.uploadedById}',
                                  style: const TextStyle(
                                    fontSize: 13,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                                const SizedBox(height: 14),
                                Text(
                                  'Tổng giá trị: ${number == null ? 'Chưa xác định' : '${NumberFormat.decimalPattern('vi').format(number)}đ'}',
                                  style: const TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 24),
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(18),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                const Text(
                                  'Ảnh / PDF gốc',
                                  style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(height: 16),
                                Material(
                                  color: const Color(0xFFF8FAFC),
                                  borderRadius: BorderRadius.circular(12),
                                  child: InkWell(
                                    onTap: () => context.push(
                                      '/documents/${doc.id}/preview',
                                    ),
                                    child: const Padding(
                                      padding: EdgeInsets.symmetric(
                                        horizontal: 12,
                                        vertical: 46,
                                      ),
                                      child: Text(
                                        '▤   Chạm để xem tài liệu gốc',
                                        textAlign: TextAlign.center,
                                        style: TextStyle(
                                          fontSize: 13,
                                          color: AppColors.primary,
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 24),
                        Card(
                          child: ExpansionTile(
                            shape: const Border(),
                            collapsedShape: const Border(),
                            title: const Text(
                              'OCR/AI: Chỉ đọc',
                              style: TextStyle(
                                fontSize: 14,
                                color: AppColors.textSecondary,
                              ),
                            ),
                            childrenPadding: const EdgeInsets.all(18),
                            children: [
                              if (data.ocr != null)
                                SelectableText(
                                  data.ocr!['rawText']?.toString() ??
                                      'Chưa có nội dung OCR',
                                ),
                              if (data.classification != null)
                                Padding(
                                  padding: const EdgeInsets.only(top: 12),
                                  child: Text(
                                    'Phân loại: ${data.classification!['category'] ?? 'Chưa xác định'}',
                                  ),
                                ),
                              if (data.ocr == null &&
                                  data.classification == null)
                                const Text('Chưa có dữ liệu trích xuất.'),
                            ],
                          ),
                        ),
                        const SizedBox(height: 14),
                        Text(
                          'Đối soát: $reconciliation',
                          style: TextStyle(
                            fontSize: 13,
                            color: recon == 'MATCHED'
                                ? AppColors.success
                                : AppColors.warning,
                          ),
                        ),
                        const SizedBox(height: 12),
                        if (eligible)
                          TextButton(
                            onPressed: _busy
                                ? null
                                : () => _note('REQUEST_INFO'),
                            child: const Text('Yêu cầu bổ sung'),
                          ),
                        TextButton(
                          onPressed: () =>
                              context.push('/documents/${doc.id}/web'),
                          child: const Text('Xử lý trên Web'),
                        ),
                        if (eligible &&
                            business.asData?.value?['review'] != 'IN_REVIEW')
                          TextButton(
                            onPressed: _busy
                                ? null
                                : () => _run('START_REVIEW'),
                            child: const Text('Bắt đầu duyệt'),
                          ),
                      ],
                    ),
                  ),
                ),
                if (eligible)
                  Container(
                    color: Colors.white,
                    padding: const EdgeInsets.all(20),
                    child: Row(
                      children: [
                        Expanded(
                          child: FilledButton(
                            style: FilledButton.styleFrom(
                              backgroundColor: AppColors.primarySurface,
                              foregroundColor: AppColors.primary,
                              minimumSize: const Size.fromHeight(48),
                            ),
                            onPressed: _busy ? null : () => _note('REJECT'),
                            child: const Text('Từ chối'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: ElevatedButton(
                            onPressed: _busy ? null : _approve,
                            child: Text(_busy ? 'Đang gửi…' : 'Duyệt'),
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class ReviewNotePage extends StatefulWidget {
  final String action;
  const ReviewNotePage({super.key, required this.action});
  @override
  State<ReviewNotePage> createState() => _ReviewNotePageState();
}

class _ReviewNotePageState extends State<ReviewNotePage> {
  final _form = GlobalKey<FormState>();
  final _note = TextEditingController();
  String? _reason;
  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final request = widget.action == 'REQUEST_INFO';
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          children: [
            FlowHeader(
              title: request
                  ? 'Kế toán • Yêu cầu bổ sung'
                  : 'Kế toán • Từ chối',
              subtitle: 'Phản hồi cho người gửi chứng từ',
              trailing: IconButton(
                tooltip: 'Quay lại',
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close, size: 20),
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Chọn lý do phổ biến',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 20),
            ...[
              'Ảnh mờ hoặc cắt góc',
              'Thiếu thông tin',
              'Số tiền không khớp',
            ].map(
              (reason) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Material(
                  color: _reason == reason
                      ? AppColors.primarySurface
                      : Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  child: InkWell(
                    onTap: () => setState(() {
                      _reason = reason;
                      _note.text = reason;
                    }),
                    borderRadius: BorderRadius.circular(16),
                    child: Padding(
                      padding: const EdgeInsets.all(18),
                      child: Text(reason, style: const TextStyle(fontSize: 14)),
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 28),
            const Text(
              'Ghi chú bắt buộc',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 14),
            Form(
              key: _form,
              child: TextFormField(
                controller: _note,
                minLines: 6,
                maxLines: 8,
                maxLength: 900,
                decoration: const InputDecoration(
                  hintText: 'Nhập nội dung cần phản hồi cho người gửi…',
                ),
                validator: (value) => value == null || value.trim().isEmpty
                    ? 'Vui lòng nhập lý do'
                    : null,
              ),
            ),
            const SizedBox(height: 32),
            ElevatedButton(
              onPressed: () {
                if (_form.currentState!.validate()) {
                  Navigator.pop(context, _note.text.trim());
                }
              },
              child: Text(request ? 'Gửi yêu cầu bổ sung' : 'Xác nhận từ chối'),
            ),
          ],
        ),
      ),
    );
  }
}
