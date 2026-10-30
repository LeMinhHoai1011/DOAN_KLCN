import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../shared/widgets/flow_widgets.dart';
import '../../data/models/document_model.dart';
import 'document_preview_page.dart';

class UploadDocumentPage extends ConsumerStatefulWidget {
  final DocumentModel? versionDocument;
  const UploadDocumentPage({super.key, this.versionDocument});
  @override
  ConsumerState<UploadDocumentPage> createState() => _UploadDocumentPageState();
}

class _UploadDocumentPageState extends ConsumerState<UploadDocumentPage> {
  final _picker = ImagePicker();
  bool _busy = false;
  Future<void> _image(ImageSource source) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final image = await _picker.pickImage(source: source, imageQuality: 95);
      if (image != null) await _preview(File(image.path));
    } catch (_) {
      _error();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _file() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final result = await FilePicker.pickFiles(
        type: FileType.custom,
        allowedExtensions: const ['pdf', 'jpg', 'jpeg', 'png'],
      );
      final path = result.isEmpty ? null : result.first.path;
      if (path != null) await _preview(File(path));
    } catch (_) {
      _error();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _preview(File file) async {
    final size = await file.length();
    if (!mounted) return;
    if (size > 10 * 1024 * 1024) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Tệp vượt quá 10 MB. Vui lòng chọn tệp nhỏ hơn.'),
        ),
      );
      return;
    }
    // Do not keep the source picker busy while the preview route is open.
    context.push(
      '/documents/preview',
      extra: UploadPreviewArgs(
        file: file,
        versionDocumentId: widget.versionDocument?.id,
      ),
    );
  }

  void _error() {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text(
          'Không thể mở tài liệu. Hãy kiểm tra quyền truy cập và thử lại.',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: Stack(
        children: [
          ListView(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
            children: [
              FlowHeader(
                title: widget.versionDocument == null
                    ? 'Tải chứng từ'
                    : 'Tải phiên bản mới',
                subtitle: 'Chọn nguồn để thêm ảnh hoặc PDF',
                trailing: IconButton(
                  tooltip: 'Quay lại',
                  onPressed: () => context.pop(),
                  icon: const Icon(Icons.close, size: 20),
                ),
              ),
              if (widget.versionDocument != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(
                    widget.versionDocument!.originalFileName,
                    style: const TextStyle(color: AppColors.textSecondary),
                  ),
                ),
              const SizedBox(height: 28),
              _option(
                '◎',
                'Chụp ảnh',
                'Dùng camera chụp chứng từ',
                () => _image(ImageSource.camera),
              ),
              const SizedBox(height: 20),
              _option(
                '▧',
                'Chọn từ thư viện',
                'Chọn ảnh có sẵn trên điện thoại',
                () => _image(ImageSource.gallery),
              ),
              const SizedBox(height: 20),
              _option('▤', 'Chọn tệp', 'PDF hoặc ảnh từ thiết bị', _file),
              const SizedBox(height: 58),
              const Text(
                'Định dạng: JPG, JPEG, PNG, PDF • Tối đa 10 MB',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
              ),
            ],
          ),
          if (_busy)
            const Positioned.fill(
              child: ColoredBox(
                color: Colors.black12,
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
        ],
      ),
    ),
  );
  Widget _option(
    String glyph,
    String title,
    String subtitle,
    VoidCallback onTap,
  ) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(16),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: _busy ? null : onTap,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              width: 58,
              height: 58,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: AppColors.primarySurface,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Text(
                glyph,
                style: const TextStyle(fontSize: 28, color: AppColors.primary),
              ),
            ),
            const SizedBox(width: 17),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    subtitle,
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
