import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pdfx/pdfx.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../../shared/widgets/flow_widgets.dart';
import '../providers/document_provider.dart';

class RemoteDocumentPreviewPage extends ConsumerWidget {
  final int documentId;

  const RemoteDocumentPreviewPage({super.key, required this.documentId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final file = ref.watch(documentFileProvider(documentId));
    final document = ref
        .watch(documentDetailProvider(documentId))
        .asData
        ?.value
        .document;
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: FlowHeader(
                title: 'Xem tài liệu',
                subtitle: document?.originalFileName ?? '#$documentId',
                trailing: IconButton(
                  tooltip: 'Quay lại',
                  onPressed: () => Navigator.maybePop(context),
                  icon: const Icon(Icons.arrow_back, size: 20),
                ),
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 14, 20),
                child: Container(
                  clipBehavior: Clip.antiAlias,
                  decoration: BoxDecoration(
                    color: AppColors.divider,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  padding: const EdgeInsets.all(16),
                  child: file.when(
                    loading: () =>
                        const Center(child: CircularProgressIndicator()),
                    error: (error, _) => FlowError(
                      error: error,
                      onRetry: () =>
                          ref.invalidate(documentFileProvider(documentId)),
                    ),
                    data: (bytes) => _AuthenticatedFileViewer(
                      key: ValueKey(bytes),
                      bytes: bytes,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AuthenticatedFileViewer extends StatefulWidget {
  final Uint8List bytes;

  const _AuthenticatedFileViewer({super.key, required this.bytes});

  @override
  State<_AuthenticatedFileViewer> createState() =>
      _AuthenticatedFileViewerState();
}

class _AuthenticatedFileViewerState extends State<_AuthenticatedFileViewer> {
  PdfControllerPinch? _controller;
  bool get _isImage =>
      widget.bytes.length > 3 &&
      ((widget.bytes[0] == 0xFF && widget.bytes[1] == 0xD8) ||
          (widget.bytes[0] == 0x89 &&
              widget.bytes[1] == 0x50 &&
              widget.bytes[2] == 0x4E &&
              widget.bytes[3] == 0x47));

  @override
  void initState() {
    super.initState();
    if (!_isImage) {
      _controller = PdfControllerPinch(
        document: PdfDocument.openData(widget.bytes),
      );
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isImage =
        widget.bytes.length > 3 &&
        ((widget.bytes[0] == 0xFF && widget.bytes[1] == 0xD8) ||
            (widget.bytes[0] == 0x89 &&
                widget.bytes[1] == 0x50 &&
                widget.bytes[2] == 0x4E &&
                widget.bytes[3] == 0x47));
    if (isImage) {
      return InteractiveViewer(
        child: Center(child: Image.memory(widget.bytes, fit: BoxFit.contain)),
      );
    }
    return PdfViewPinch(
      controller: _controller!,
      builders: PdfViewPinchBuilders(
        options: const DefaultBuilderOptions(),
        documentLoaderBuilder: (_) =>
            const Center(child: CircularProgressIndicator()),
        errorBuilder: (_, error) =>
            Center(child: Text('Không thể xem tài liệu: $error')),
      ),
    );
  }
}
