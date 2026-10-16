import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import '../../../../app/theme/app_colors.dart';
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
      if (image != null && mounted) _preview(File(image.path));
    } catch (error) {
      _error(error);
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
      if (path != null && mounted) _preview(File(path));
    } catch (error) {
      _error(error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _preview(File file) => context.push(
        '/documents/preview',
        extra: UploadPreviewArgs(
          file: file,
          versionDocumentId: widget.versionDocument?.id,
        ),
      );

  void _error(Object error) {
    if (!mounted) return;
    debugPrint('UPLOAD_PICK_FAILED: ${error.runtimeType}');
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
      behavior: SnackBarBehavior.floating,
      content: Text('Không thể mở tài liệu. Hãy kiểm tra quyền truy cập và thử lại.'),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final version = widget.versionDocument;
    return Scaffold(
      appBar: AppBar(title: Text(version == null ? 'Tải chứng từ lên' : 'Tải phiên bản mới')),
      body: SafeArea(
        child: Stack(children: [
          ListView(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
            children: [
              _Hero(isVersion: version != null),
              if (version != null) ...[
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFFFBEB),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: const Color(0xFFFDE68A)),
                  ),
                  child: Row(children: [
                    const Icon(Icons.history_rounded, color: Color(0xFFD97706)),
                    const SizedBox(width: 10),
                    Expanded(child: Text(version.originalFileName,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                      maxLines: 2, overflow: TextOverflow.ellipsis)),
                  ]),
                ),
              ],
              const SizedBox(height: 24),
              const Text('Chọn nguồn tài liệu',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800,
                  color: AppColors.textPrimary)),
              const SizedBox(height: 5),
              const Text('Chụp trực tiếp hoặc chọn tệp đã lưu trên thiết bị.',
                style: TextStyle(color: AppColors.textSecondary, fontSize: 13)),
              const SizedBox(height: 16),
              _Option(
                icon: Icons.document_scanner_outlined,
                color: const Color(0xFF2563EB),
                background: const Color(0xFFEFF6FF),
                title: 'Quét bằng camera',
                subtitle: 'Chụp hóa đơn hoặc chứng từ ngay bây giờ',
                onTap: () => _image(ImageSource.camera),
              ),
              const SizedBox(height: 12),
              _Option(
                icon: Icons.photo_library_outlined,
                color: const Color(0xFF7C3AED),
                background: const Color(0xFFF5F3FF),
                title: 'Chọn từ thư viện ảnh',
                subtitle: 'Sử dụng ảnh JPG, JPEG hoặc PNG có sẵn',
                onTap: () => _image(ImageSource.gallery),
              ),
              const SizedBox(height: 12),
              _Option(
                icon: Icons.picture_as_pdf_outlined,
                color: const Color(0xFFDC2626),
                background: const Color(0xFFFEF2F2),
                title: 'Chọn PDF hoặc ảnh',
                subtitle: 'Mở trình quản lý tệp trên thiết bị',
                onTap: _file,
              ),
              const SizedBox(height: 20),
              const _Tips(),
            ],
          ),
          if (_busy) Positioned.fill(child: ColoredBox(
            color: Colors.black26,
            child: Center(child: Card(child: Padding(
              padding: EdgeInsets.symmetric(horizontal: 22, vertical: 18),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                SizedBox(width: 22, height: 22,
                  child: CircularProgressIndicator(strokeWidth: 2.5)),
                SizedBox(width: 14),
                Text('Đang mở tài liệu...', style: TextStyle(fontWeight: FontWeight.w600)),
              ]),
            ))),
          )),
        ]),
      ),
    );
  }
}

class _Hero extends StatelessWidget {
  final bool isVersion;
  const _Hero({required this.isVersion});
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      gradient: const LinearGradient(colors: [Color(0xFF2563EB), Color(0xFF0EA5E9)]),
      borderRadius: BorderRadius.circular(22),
    ),
    child: Row(children: [
      Container(width: 58, height: 58,
        decoration: BoxDecoration(color: Colors.white.withValues(alpha: .17),
          borderRadius: BorderRadius.circular(18)),
        child: Icon(isVersion ? Icons.history_rounded : Icons.cloud_upload_outlined,
          color: Colors.white, size: 31)),
      const SizedBox(width: 16),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(isVersion ? 'Cập nhật tài liệu' : 'Số hóa chứng từ',
          style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w800)),
        const SizedBox(height: 5),
        Text(isVersion
            ? 'Bản mới sẽ được lưu trong lịch sử phiên bản.'
            : 'AI sẽ đọc và trích xuất thông tin sau khi tải lên.',
          style: TextStyle(color: Colors.white.withValues(alpha: .88), fontSize: 13, height: 1.4)),
      ])),
    ]),
  );
}

class _Option extends StatelessWidget {
  final IconData icon;
  final Color color, background;
  final String title, subtitle;
  final VoidCallback onTap;
  const _Option({required this.icon, required this.color, required this.background,
    required this.title, required this.subtitle, required this.onTap});
  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(17),
      side: const BorderSide(color: Color(0xFFE5E7EB))),
    clipBehavior: Clip.antiAlias,
    child: InkWell(onTap: onTap, child: Padding(
      padding: const EdgeInsets.all(16),
      child: Row(children: [
        Container(width: 52, height: 52,
          decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(15)),
          child: Icon(icon, color: color, size: 27)),
        const SizedBox(width: 14),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700,
            color: AppColors.textPrimary)),
          const SizedBox(height: 5),
          Text(subtitle, style: const TextStyle(fontSize: 12, height: 1.35,
            color: AppColors.textSecondary)),
        ])),
        const Icon(Icons.arrow_forward_ios_rounded, size: 16, color: Color(0xFF94A3B8)),
      ]),
    )),
  );
}

class _Tips extends StatelessWidget {
  const _Tips();
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(color: const Color(0xFFF8FAFC),
      borderRadius: BorderRadius.circular(16), border: Border.all(color: const Color(0xFFE2E8F0))),
    child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Icon(Icons.tips_and_updates_outlined, size: 20, color: Color(0xFF475569)),
        SizedBox(width: 8),
        Text('Để nhận diện tốt hơn', style: TextStyle(fontWeight: FontWeight.w700)),
      ]),
      SizedBox(height: 10),
      Text('• Ảnh rõ nét, đủ sáng và thấy trọn bốn góc chứng từ\n'
        '• Hỗ trợ PDF, JPG, JPEG và PNG\n'
        '• Bạn sẽ được xem trước trước khi gửi lên máy chủ',
        style: TextStyle(color: Color(0xFF64748B), fontSize: 12, height: 1.65)),
    ]),
  );
}
