import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:pdfx/pdfx.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../shared/widgets/app_button.dart';
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
  ConsumerState<DocumentPreviewPage> createState() => _DocumentPreviewPageState();
}

class _DocumentPreviewPageState extends ConsumerState<DocumentPreviewPage> {
  PdfControllerPinch? _pdfController;
  bool _isUploading = false;
  double _uploadProgress = 0;

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
        ref.invalidate(documentDetailProvider(versionId));
        message = 'Đã tải phiên bản mới lên. Trạng thái: ${updated.processingStatus}';
      } else {
        final result = await repository.uploadDocument(
          _file,
          onSendProgress: _updateProgress,
        );
        documentId = result.documentId;
        processingSucceeded = result.success;
        message = result.success
            ? result.message
            : '${result.message}: ${result.errorMessage ?? result.errorCode ?? "AI processing failed"}';
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
      context.go('/documents/$documentId');
    } catch (error) {
      if (!mounted) return;
      setState(() => _isUploading = false);
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
    final fileSize = _file.lengthSync();
    final isImage = ['.jpg', '.jpeg', '.png']
        .any((extension) => _fileName.toLowerCase().endsWith(extension));
    return Scaffold(
      appBar: AppBar(title: const Text('Xem trước')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  border: Border.all(color: AppColors.divider),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: isImage
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: Image.file(_file, fit: BoxFit.contain),
                      )
                    : _isPdf
                        ? PdfViewPinch(
                            controller: _pdfController!,
                            builders: PdfViewPinchBuilders(
                              options: const DefaultBuilderOptions(),
                              documentLoaderBuilder: (_) => const Center(
                                child: CircularProgressIndicator(),
                              ),
                              errorBuilder: (_, error) => Center(
                                child: Text('Không thể xem PDF: $error'),
                              ),
                            ),
                          )
                        : const Center(child: Text('Định dạng file không hỗ trợ.')),
              ),
            ),
            const SizedBox(height: 16),
            Text('Tên file: $_fileName', style: const TextStyle(fontWeight: FontWeight.bold)),
            Text(
              'Kích thước: ${(fileSize / 1024 / 1024).toStringAsFixed(2)} MB',
              style: const TextStyle(color: AppColors.textSecondary),
            ),
            if (_isUploading) ...[
              const SizedBox(height: 12),
              LinearProgressIndicator(value: _uploadProgress == 0 ? null : _uploadProgress),
              const SizedBox(height: 4),
              const Text('Đang tải lên và xử lý chứng từ…'),
            ],
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _isUploading ? null : () => context.pop(),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: const Text('Chọn lại'),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: AppButton(
                    text: widget.args.versionDocumentId == null
                        ? 'Gửi chứng từ'
                        : 'Tải phiên bản',
                    isLoading: _isUploading,
                    onPressed: _upload,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }
}
