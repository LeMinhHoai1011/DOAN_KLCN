import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pdfx/pdfx.dart';
import '../../../../app/theme/app_colors.dart';
import '../providers/document_provider.dart';

class RemoteDocumentPreviewPage extends ConsumerWidget {
  final int documentId;

  const RemoteDocumentPreviewPage({super.key, required this.documentId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final file = ref.watch(documentFileProvider(documentId));
    return Scaffold(
      appBar: AppBar(title: const Text('Tài liệu gốc')),
      body: file.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stackTrace) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              error.toString(),
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.error),
            ),
          ),
        ),
        data: (bytes) => _AuthenticatedFileViewer(bytes: bytes),
      ),
    );
  }
}

class _AuthenticatedFileViewer extends StatefulWidget {
  final Uint8List bytes;

  const _AuthenticatedFileViewer({required this.bytes});

  @override
  State<_AuthenticatedFileViewer> createState() => _AuthenticatedFileViewerState();
}

class _AuthenticatedFileViewerState extends State<_AuthenticatedFileViewer> {
  late final PdfControllerPinch _controller;

  @override
  void initState() {
    super.initState();
    _controller = PdfControllerPinch(
      document: PdfDocument.openData(widget.bytes),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isImage = widget.bytes.length > 3 &&
        ((widget.bytes[0] == 0xFF && widget.bytes[1] == 0xD8) ||
            (widget.bytes[0] == 0x89 &&
                widget.bytes[1] == 0x50 &&
                widget.bytes[2] == 0x4E &&
                widget.bytes[3] == 0x47));
    if (isImage) {
      return InteractiveViewer(
        child: Center(
          child: Image.memory(widget.bytes, fit: BoxFit.contain),
        ),
      );
    }
    return PdfViewPinch(
      controller: _controller,
      builders: PdfViewPinchBuilders(
        options: const DefaultBuilderOptions(),
        documentLoaderBuilder: (_) =>
            const Center(child: CircularProgressIndicator()),
        errorBuilder: (_, error) => Center(
          child: Text('Không thể xem tài liệu: $error'),
        ),
      ),
    );
  }
}
