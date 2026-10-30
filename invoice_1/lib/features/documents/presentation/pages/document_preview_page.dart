import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:pdfx/pdfx.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../core/network/api_client.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/flow_widgets.dart';
import '../../../../shared/widgets/document_status_pill.dart';
import '../../../dashboard/presentation/providers/dashboard_provider.dart';
import '../providers/document_provider.dart';

class UploadPreviewArgs {
  final File file;
  final int? versionDocumentId;

  const UploadPreviewArgs({required this.file, this.versionDocumentId});
}

class DocumentPreviewPage extends ConsumerStatefulWidget {
  final UploadPreviewArgs args;

  const DocumentPreviewPage({super.key, required this.args});

  @override
  ConsumerState<DocumentPreviewPage> createState() =>
      _DocumentPreviewPageState();
}

class _DocumentPreviewPageState extends ConsumerState<DocumentPreviewPage> {
  PdfControllerPinch? _pdfController;
  bool _isUploading = false;
  double _uploadProgress = 0;
  int? _uploadedId;
  String? _uploadedStatus;
  bool _processingFailed = false;
  ApiException? _denied;

  File get _file => widget.args.file;
  String get _fileName => _file.uri.pathSegments.last;
  bool get _isPdf => _fileName.toLowerCase().endsWith('.pdf');

  @override
  void initState() {
    super.initState();
    if (_isPdf) {
      _pdfController = PdfControllerPinch(
        document: PdfDocument.openFile(_file.path),
      );
    }
  }

  @override
  void dispose() {
    _pdfController?.dispose();
    super.dispose();
  }

  Future<void> _upload() async {
    setState(() {
      _isUploading = true;
      _uploadProgress = 0;
    });
    try {
      final repository = ref.read(documentRepositoryProvider);
      final versionId = widget.args.versionDocumentId;
      var documentId = versionId;
      String message;
      bool processingSucceeded = true;
      if (versionId != null) {
        final updated = await repository.uploadDocumentVersion(
          versionId,
          _file,
          onSendProgress: _updateProgress,
        );
        documentId = updated.id;
        _uploadedStatus = updated.processingStatus;
        message = 'Đã lưu phiên bản mới.';
        if (updated.processingStatus == 'NEED_REVIEW' &&
            (updated.reviewStatus == 'REJECTED' ||
                updated.reviewStatus == 'CORRECTED')) {
          try {
            final resubmitted = await repository.runWorkflowAction(
              updated.id,
              'RESUBMIT',
            );
            _uploadedStatus = resubmitted.processingStatus;
            message = 'Đã lưu phiên bản mới và gửi lại chứng từ.';
          } catch (_) {
            processingSucceeded = false;
            message =
                'Tệp mới đã được lưu nhưng chưa gửi lại được. Mở chi tiết và làm mới trạng thái trước khi gửi lại; không cần tải lại tệp.';
          }
        }
        ref.invalidate(documentDetailProvider(versionId));
        ref.invalidate(documentBusinessProvider(versionId));
      } else {
        final result = await repository.uploadDocument(
          _file,
          onSendProgress: _updateProgress,
        );
        documentId = result.documentId;
        _uploadedStatus = result.processingStatus;
        processingSucceeded = result.success;
        message = result.success
            ? 'Đã gửi chứng từ.'
            : 'Chứng từ đã được lưu nhưng xử lý chưa thành công. Mở chi tiết để kiểm tra.';
      }

      await ref.read(documentListProvider.notifier).loadDocuments();
      await ref.read(dashboardProvider.notifier).loadData();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: processingSucceeded ? null : AppColors.error,
        ),
      );
      setState(() {
        _uploadedId = documentId;
        _isUploading = false;
        _processingFailed = !processingSucceeded;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _isUploading = false);
      if (error is ApiException && error.statusCode == 403) {
        setState(() => _denied = error);
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString()),
          backgroundColor: AppColors.error,
        ),
      );
    }
  }

  void _updateProgress(int sent, int total) {
    if (!mounted || total <= 0) return;
    setState(() => _uploadProgress = sent / total);
  }

  @override
  Widget build(BuildContext context) {
    if (_denied != null) {
      return Scaffold(
        body: SafeArea(
          child: FlowError(
            error: _denied!,
            deniedTitle: 'Không thể tải chứng từ',
            onRetry: () => setState(() => _denied = null),
          ),
        ),
      );
    }
    if (_isUploading || _uploadedId != null) return _progressScreen(context);
    final fileSize = _file.lengthSync();
    final isImage = [
      '.jpg',
      '.jpeg',
      '.png',
    ].any((extension) => _fileName.toLowerCase().endsWith(extension));
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          children: [
            FlowHeader(
              title: 'Xem trước chứng từ',
              subtitle: 'Kiểm tra tài liệu trước khi gửi',
              trailing: IconButton(
                tooltip: 'Quay lại',
                onPressed: () => context.pop(),
                icon: const Icon(Icons.close, size: 20),
              ),
            ),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: SizedBox(
                  height: 318,
                  child: isImage
                      ? ClipRRect(
                          borderRadius: BorderRadius.circular(12),
                          child: Image.file(
                            _file,
                            fit: BoxFit.contain,
                            errorBuilder: (_, _, _) => const Center(
                              child: Text(
                                'Không thể đọc ảnh. Vui lòng chọn lại.',
                              ),
                            ),
                          ),
                        )
                      : _isPdf
                      ? PdfViewPinch(
                          controller: _pdfController!,
                          builders: PdfViewPinchBuilders(
                            options: const DefaultBuilderOptions(),
                            documentLoaderBuilder: (_) => const Center(
                              child: CircularProgressIndicator(),
                            ),
                            errorBuilder: (_, _) => const Center(
                              child: Text(
                                'Không thể xem PDF. Vui lòng chọn lại.',
                              ),
                            ),
                          ),
                        )
                      : const Center(
                          child: Text('Định dạng file không hỗ trợ.'),
                        ),
                ),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              _fileName,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            Text(
              '${_isPdf ? 'PDF' : 'Ảnh'} • ${(fileSize / 1024 / 1024).toStringAsFixed(2)} MB',
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 66),
            AppButton(
              text: widget.args.versionDocumentId == null
                  ? 'Gửi chứng từ'
                  : 'Thay tệp và gửi lại',
              onPressed: _upload,
            ),
            const SizedBox(height: 12),
            TextButton(
              onPressed: () => context.pop(),
              child: const Text('Chọn tệp khác'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _progressScreen(BuildContext context) {
    final uploaded = _uploadedId != null;
    final waitingForServer = !uploaded && _uploadProgress >= 1;
    final percent = (_uploadProgress * 100).clamp(0, 100).round();
    return PopScope(
      canPop: !_isUploading,
      child: Scaffold(
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
            children: [
              FlowHeader(
                title: uploaded ? 'Chứng từ đã được gửi' : 'Đang gửi chứng từ',
                subtitle: uploaded
                    ? 'Trạng thái xử lý lấy từ máy chủ'
                    : waitingForServer
                    ? 'Bước 2/2 • Chờ kết quả từ máy chủ'
                    : 'Bước 1/2 • Truyền tệp lên máy chủ',
              ),
              const SizedBox(height: 28),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(18),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (uploaded)
                        DocumentStatusPill(
                          processing: _uploadedStatus ?? 'UNKNOWN',
                        ),
                      const SizedBox(height: 16),
                      Text(
                        uploaded
                            ? (_processingFailed
                                  ? 'Cần kiểm tra kết quả xử lý'
                                  : processingLabel(
                                      _uploadedStatus ?? 'UNKNOWN',
                                    ))
                            : waitingForServer
                            ? 'Đang chờ máy chủ xử lý'
                            : 'Đang tải lên',
                        style: const TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 24),
                      if (!uploaded && !waitingForServer)
                        Text(
                          '$percent%',
                          style: const TextStyle(
                            fontSize: 36,
                            fontWeight: FontWeight.w600,
                            color: AppColors.primary,
                          ),
                        ),
                      if (!uploaded) ...[
                        const SizedBox(height: 16),
                        LinearProgressIndicator(
                          value: waitingForServer ? null : _uploadProgress,
                          minHeight: 12,
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ],
                      const SizedBox(height: 24),
                      Text(
                        uploaded
                            ? 'Mở chi tiết để xem kết quả và phản hồi từ kế toán.'
                            : waitingForServer
                            ? 'Tệp đã truyền xong. Chưa có kết quả OCR/AI từ máy chủ.'
                            : 'Tiến độ $percent% chỉ tính truyền tệp.',
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.textSecondary,
                        ),
                      ),
                      const SizedBox(height: 20),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              if (uploaded) ...[
                AppButton(
                  text: 'Xem chi tiết chứng từ',
                  onPressed: () => context.go('/documents/$_uploadedId'),
                ),
                TextButton(
                  onPressed: () => context.go('/documents'),
                  child: const Text('Về danh sách chứng từ'),
                ),
              ] else
                const Card(
                  child: Padding(
                    padding: EdgeInsets.all(20),
                    child: Text(
                      'Phần trăm truyền tệp không phải tiến độ OCR/AI. Trạng thái xử lý sẽ cập nhật khi có phản hồi từ máy chủ.',
                      style: TextStyle(
                        fontSize: 13,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
