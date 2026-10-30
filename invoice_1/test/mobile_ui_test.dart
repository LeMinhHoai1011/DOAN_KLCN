import 'dart:io';
import 'dart:async';
import 'dart:ui' as ui;
import 'package:dio/dio.dart' show ProgressCallback;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:invoice_1/app/theme/app_theme.dart';
import 'package:invoice_1/app/app.dart';
import 'package:invoice_1/features/workspace/data/workspace_repository.dart';
import 'package:invoice_1/features/workspace/presentation/web_handoff_page.dart';
import 'package:invoice_1/features/auth/data/auth_repository.dart';
import 'package:invoice_1/features/auth/data/models/user_model.dart';
import 'package:invoice_1/features/auth/presentation/providers/auth_provider.dart';
import 'package:invoice_1/features/auth/presentation/pages/login_page.dart';
import 'package:invoice_1/features/dashboard/data/dashboard_repository.dart';
import 'package:invoice_1/features/dashboard/data/models/dashboard_summary_model.dart';
import 'package:invoice_1/features/dashboard/presentation/providers/dashboard_provider.dart';
import 'package:invoice_1/features/dashboard/presentation/pages/dashboard_page.dart';
import 'package:invoice_1/features/documents/data/document_repository.dart';
import 'package:invoice_1/features/documents/data/models/document_model.dart';
import 'package:invoice_1/features/documents/presentation/providers/document_provider.dart';
import 'package:invoice_1/features/documents/presentation/pages/my_documents_page.dart';
import 'package:invoice_1/features/documents/presentation/pages/document_detail_page.dart';
import 'package:invoice_1/features/profile/presentation/pages/profile_page.dart';
import 'package:invoice_1/shared/widgets/document_status_pill.dart';
import 'package:invoice_1/shared/widgets/flow_widgets.dart';
import 'package:invoice_1/core/network/api_client.dart';
import 'package:invoice_1/features/documents/presentation/pages/document_preview_page.dart';
import 'package:invoice_1/features/documents/presentation/pages/remote_document_preview_page.dart';

const sample = DocumentModel(
  id: 247,
  originalFileName: 'Hóa đơn văn phòng phẩm',
  processingStatus: 'NEED_REVIEW',
  reviewStatus: 'CORRECTED',
  documentTypeName: 'Hóa đơn bán hàng',
  amount: 350000,
  uploadedById: 1,
);

Future<void> captureReview(
  WidgetTester tester,
  GlobalKey key,
  String name,
) async {
  final directory = Platform.environment['UI_REVIEW_OUTPUT'];
  if (directory == null || tester.view.physicalSize.width != 390) return;
  final boundary =
      key.currentContext!.findRenderObject() as RenderRepaintBoundary;
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 1);
    final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
    await Directory(directory).create(recursive: true);
    await File(
      '$directory/$name.png',
    ).writeAsBytes(bytes!.buffer.asUint8List());
    image.dispose();
  });
}

class _AuthRepo implements AuthRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Auth extends AuthNotifier {
  final String role;
  _Auth({this.role = 'EMPLOYEE'}) : super(_AuthRepo());
  @override
  Future<void> restoreSession() async {
    state = AuthState(
      isInitialized: true,
      user: UserModel(
        userId: 1,
        username: 'an',
        fullName: 'Nguyễn Văn An',
        email: 'nhanvien@example.com',
        companyId: 1,
        roles: [role],
      ),
    );
  }
}

class _DashboardRepo implements DashboardRepository {
  @override
  bool get reviewQueue => false;
  @override
  Future<DashboardSummaryModel> getSummary() async =>
      const DashboardSummaryModel(
        totalDocuments: 12,
        totalInvoices: 8,
        totalClassified: 7,
        totalReviewRequired: 3,
        totalFailed: 1,
        approvedCount: 7,
        correctionCount: 2,
        rejectedCount: 0,
        documentsByStatus: [
          DocumentStatusCount(status: 'UPLOADED', count: 1),
          DocumentStatusCount(status: 'NEED_REVIEW', count: 3),
          DocumentStatusCount(status: 'COMPLETED', count: 7),
          DocumentStatusCount(status: 'FAILED', count: 1),
        ],
      );
  @override
  Future<List<DocumentModel>> getRecentDocuments() async => [
    sample,
    const DocumentModel(
      id: 242,
      originalFileName: 'Hóa đơn dịch vụ internet',
      processingStatus: 'COMPLETED',
      reviewStatus: 'APPROVED',
      documentTypeName: 'Hóa đơn bán hàng',
    ),
  ];
}

class _Documents implements DocumentRepository {
  DocumentModel document;
  String? workflowAction, workflowNote;
  final bool empty;
  final Object? error;
  final Uint8List? fileBytes;
  _Documents({
    this.document = sample,
    this.empty = false,
    this.error,
    this.fileBytes,
  });
  final calls = <({String? processing, String? review, int page})>[];
  @override
  Future<DocumentPage> getMyDocuments({
    int page = 0,
    int size = 20,
    String sort = 'createdAt,desc',
    String? search,
    String? processingStatus,
    String? reviewStatus,
  }) async {
    if (error != null) throw error!;
    calls.add((processing: processingStatus, review: reviewStatus, page: page));
    return DocumentPage(
      documents: empty ? [] : [document],
      page: page,
      totalPages: 2,
      last: empty || page >= 1,
      totalElements: 2,
    );
  }

  @override
  Future<DocumentDetailData> getDocumentDetail(int id) async =>
      DocumentDetailData(document: document);
  @override
  Future<DocumentModel> runWorkflowAction(
    int id,
    String action, {
    String? note,
  }) async {
    workflowAction = action;
    workflowNote = note;
    return document;
  }

  @override
  Future<Map<String, dynamic>?> getBusinessState(int id) async => {
    'payment': null,
    'history': [
      {
        'status': 'CORRECTED',
        'action': 'REQUEST_INFO: Ảnh hóa đơn thiếu phần tổng tiền.',
        'at': '2026-10-29T14:20:00',
      },
    ],
  };
  @override
  Future<Map<String, dynamic>?> getAssessment(int id) async => {
    'reviewReasons': ['Thiếu thông tin tổng tiền'],
  };
  @override
  Future<Uint8List> getDocumentFile(int id) async => fileBytes!;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _UploadDocuments extends _Documents {
  final completed = Completer<DocumentUploadResult>();
  ProgressCallback? progress;
  _UploadDocuments({super.fileBytes});
  @override
  Future<DocumentUploadResult> uploadDocument(
    File file, {
    ProgressCallback? onSendProgress,
  }) async {
    progress = onSendProgress;
    onSendProgress?.call(72, 100);
    return completed.future;
  }
}

class _Workspace implements WorkspaceRepository {
  @override
  Future<Map<String, dynamic>> financialTotals(String from, String to) async =>
      {'totalRevenue': 8500000, 'totalExpense': 3200000};
  @override
  Future<FinancialPage> transactions({
    int page = 0,
    String? type,
    String? dateFrom,
    String? dateTo,
  }) async => FinancialPage([
    FinancialEntry(
      id: 12,
      type: 'EXPENSE',
      confirmation: 'CONFIRMED',
      amount: 350000,
      description: 'Văn phòng phẩm',
      date: '2026-10-29',
      documentId: 247,
    ),
  ], true);
  @override
  Future<List<UserModel>> users() async => [
    UserModel(
      userId: 1,
      username: 'an',
      fullName: 'Nguyễn Văn An',
      roles: ['EMPLOYEE'],
      status: 'ACTIVE',
    ),
  ];
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FilteredWorkspace extends _Workspace {
  final pages = <int>[];
  @override
  Future<FinancialPage> transactions({
    int page = 0,
    String? type,
    String? dateFrom,
    String? dateTo,
  }) async {
    pages.add(page);
    return FinancialPage([
      FinancialEntry(
        id: page + 1,
        type: 'EXPENSE',
        amount: 100,
        confirmation: page == 0 ? 'UNVERIFIED' : 'CONFIRMED',
        description: page == 0 ? 'Khoản chờ xác minh' : 'Khoản đã xác nhận',
      ),
    ], page >= 1);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    for (final entry in {
      'Inter': 'assets/fonts/InterVariable.ttf',
      'NotoSansSymbols2': 'assets/fonts/NotoSansSymbols2-Regular.ttf',
      'NotoSansSymbols': 'assets/fonts/NotoSansSymbols-Regular.ttf',
      'MaterialIcons': 'fonts/MaterialIcons-Regular.otf',
    }.entries) {
      final loader = FontLoader(entry.key)
        ..addFont(rootBundle.load(entry.value));
      await loader.load();
    }
  });
  test(
    'processing failure and returned review remain distinct from approval',
    () {
      expect(documentStatus('FAILED', 'PENDING').label, 'Xử lý lỗi');
      expect(documentStatus('NEED_REVIEW', 'CORRECTED').label, 'Cần bổ sung');
      expect(documentStatus('COMPLETED', 'PENDING').label, isNot('Đã duyệt'));
      expect(documentStatus('COMPLETED', 'APPROVED').label, 'Đã duyệt');
    },
  );
  test(
    'Web handoff preserves deployment prefix and excludes query secrets',
    () {
      final uri = documentWebUri(
        'https://invoice.example/app/?token=secret#old',
        247,
      );
      expect(
        uri.toString(),
        'https://invoice.example/app/accountant/documents/247',
      );
      expect(
        () => documentWebUri('javascript:alert(1)', 247),
        throwsFormatException,
      );
      expect(
        () => documentWebUri('https://invoice.example', -1),
        throwsFormatException,
      );
    },
  );
  test(
    'review filter survives refresh and pagination and can be cleared',
    () async {
      final repository = _Documents();
      final notifier = DocumentListNotifier(repository);
      await Future<void>.delayed(Duration.zero);
      await notifier.loadDocuments(status: 'NEED_REVIEW', review: 'CORRECTED');
      await notifier.loadDocuments();
      await notifier.loadMore();
      expect(repository.calls.last, (
        processing: 'NEED_REVIEW',
        review: 'CORRECTED',
        page: 1,
      ));
      await notifier.loadDocuments(status: '', review: '');
      expect(notifier.state.review, isNull);
      expect(repository.calls.last.review, isNull);
      notifier.dispose();
    },
  );

  for (final width in [390.0, 320.0]) {
    testWidgets('confirmation filter reads through server pages at $width', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final workspace = _FilteredWorkspace();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authProvider.overrideWith((ref) => _Auth(role: 'ACCOUNTANT')),
            dashboardRepositoryProvider.overrideWithValue(_DashboardRepo()),
            documentRepositoryProvider.overrideWithValue(_Documents()),
            workspaceRepositoryProvider.overrideWithValue(workspace),
          ],
          child: const SmartInvoiceApp(),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Thu chi').last);
      await tester.pumpAndSettle();
      await tester.tap(find.textContaining('Lọc trạng thái'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Đã xác nhận'));
      await tester.pumpAndSettle();
      expect(workspace.pages, [0, 0, 1]);
      expect(find.textContaining('Khoản đã xác nhận'), findsOneWidget);
      expect(find.textContaining('Khoản chờ xác minh'), findsNothing);
      expect(tester.takeException(), isNull);
    });
    testWidgets('empty, network error and permission states at $width', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final boundary = GlobalKey();
      Future<void> show(Widget page, _Documents documents, String name) async {
        await tester.pumpWidget(
          ProviderScope(
            key: UniqueKey(),
            overrides: [
              authProvider.overrideWith((ref) => _Auth()),
              documentRepositoryProvider.overrideWithValue(documents),
            ],
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: AppTheme.lightTheme,
              home: RepaintBoundary(
                key: boundary,
                child: Scaffold(body: page),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await captureReview(tester, boundary, name);
      }

      await show(
        const MyDocumentsPage(),
        _Documents(empty: true),
        'documents-empty',
      );
      expect(find.text('Xóa bộ lọc'), findsOneWidget);
      await tester.tap(find.text('Xóa bộ lọc'));
      await tester.pumpAndSettle();
      await show(
        const MyDocumentsPage(),
        _Documents(error: const ApiException('network')),
        'documents-error',
      );
      expect(find.text('Thử lại'), findsOneWidget);
      await show(
        FlowError(
          error: const ApiException('denied', statusCode: 403),
          deniedTitle: 'Không thể tải chứng từ',
          onRetry: () {},
        ),
        _Documents(),
        'permission-denied',
      );
      expect(find.text('Quay về trang chủ'), findsOneWidget);
    });
    testWidgets(
      'file preview and upload progress distinguish transfer from OCR at $width',
      (tester) async {
        tester.view.physicalSize = Size(width, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        late Directory temp;
        late File file;
        late Uint8List bytes;
        await tester.runAsync(() async {
          temp = await Directory.systemTemp.createTemp('invoiceflow-ui-');
          final recorder = ui.PictureRecorder();
          final canvas = Canvas(recorder);
          canvas.drawColor(Colors.white, BlendMode.src);
          final paint = Paint()..color = const Color(0xFFE2E8F0);
          for (var y = 80.0; y < 550; y += 60) {
            canvas.drawRect(Rect.fromLTWH(40, y, 320, 2), paint);
          }
          final picture = recorder.endRecording();
          final image = await picture.toImage(400, 600);
          bytes = (await image.toByteData(
            format: ui.ImageByteFormat.png,
          ))!.buffer.asUint8List();
          file = await File('${temp.path}/invoice.png').writeAsBytes(bytes);
          image.dispose();
          picture.dispose();
        });
        addTearDown(() async {
          await tester.pumpWidget(const SizedBox.shrink());
          await FileImage(file).evict();
          await file.delete();
          await temp.delete();
        });
        await tester.pumpWidget(const MaterialApp(home: SizedBox()));
        await tester.runAsync(
          () => precacheImage(
            FileImage(file),
            tester.element(find.byType(SizedBox).first),
          ),
        );
        final documents = _UploadDocuments(fileBytes: bytes);
        final boundary = GlobalKey();
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              authProvider.overrideWith((ref) => _Auth()),
              documentRepositoryProvider.overrideWithValue(documents),
              dashboardRepositoryProvider.overrideWithValue(_DashboardRepo()),
            ],
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: AppTheme.lightTheme,
              home: RepaintBoundary(
                key: boundary,
                child: DocumentPreviewPage(args: UploadPreviewArgs(file: file)),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.pumpAndSettle();
        await captureReview(tester, boundary, 'upload-preview');
        await tester.tap(find.text('Gửi chứng từ'));
        await tester.pumpAndSettle();
        expect(find.text('72%'), findsOneWidget);
        await captureReview(tester, boundary, 'upload-progress');
        documents.progress!(100, 100);
        await tester.pump(const Duration(milliseconds: 300));
        expect(find.text('100%'), findsNothing);
        expect(find.text('Đang chờ máy chủ xử lý'), findsOneWidget);
        documents.completed.complete(
          const DocumentUploadResult(
            success: true,
            documentId: 247,
            processingStatus: 'PROCESSING',
            reviewStatus: 'PENDING',
            message: 'accepted',
          ),
        );
        await tester.pumpAndSettle();
        await captureReview(tester, boundary, 'upload-processing');
        expect(find.text('Xem chi tiết chứng từ'), findsOneWidget);
        await tester.pumpWidget(
          ProviderScope(
            key: UniqueKey(),
            overrides: [
              authProvider.overrideWith((ref) => _Auth()),
              documentRepositoryProvider.overrideWithValue(documents),
            ],
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: AppTheme.lightTheme,
              home: RepaintBoundary(
                key: boundary,
                child: const RemoteDocumentPreviewPage(documentId: 247),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.runAsync(() async {
          unawaited(
            precacheImage(
              MemoryImage(bytes),
              tester.element(find.byType(RemoteDocumentPreviewPage)),
            ),
          );
          await Future<void>.delayed(const Duration(milliseconds: 100));
        });
        await tester.pumpAndSettle();
        await captureReview(tester, boundary, 'document-file');
        expect(tester.takeException(), isNull);
      },
    );
    for (final role in ['ACCOUNTANT', 'ADMIN']) {
      testWidgets('$role navigation and review privileges at $width', (
        tester,
      ) async {
        tester.view.physicalSize = Size(width, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final documents = _Documents(
          document: const DocumentModel(
            id: 247,
            originalFileName: 'Chứng từ cần kiểm tra',
              amount: 350000,
              uploadedById: 1,
            processingStatus: 'PROCESSED',
            reviewStatus: 'PENDING',
          ),
        );
        final appBoundary = GlobalKey();
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              authProvider.overrideWith((ref) => _Auth(role: role)),
              dashboardRepositoryProvider.overrideWithValue(_DashboardRepo()),
              documentRepositoryProvider.overrideWithValue(documents),
              workspaceRepositoryProvider.overrideWithValue(_Workspace()),
            ],
            child: RepaintBoundary(
              key: appBoundary,
              child: const SmartInvoiceApp(),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await captureReview(
          tester,
          appBoundary,
          '${role.toLowerCase()}-dashboard',
        );
        expect(find.text('+'), findsNothing);
        await tester.tap(
          find.text(role == 'ACCOUNTANT' ? 'Thu chi' : 'Giám sát').last,
        );
        await tester.pumpAndSettle();
        await captureReview(
          tester,
          appBoundary,
          '${role.toLowerCase()}-workspace',
        );
        if (role == 'ACCOUNTANT') {
          expect(find.textContaining('Văn phòng phẩm'), findsOneWidget);
          await tester.tap(find.textContaining('Văn phòng phẩm'));
          await tester.pumpAndSettle();
          expect(find.text('Đã xác nhận'), findsOneWidget);
          await captureReview(tester, appBoundary, 'financial-detail');
          await tester.tap(find.text('Xem chứng từ liên kết'));
        } else {
          await tester.tap(find.text('Người dùng đang hoạt động'));
          await tester.pumpAndSettle();
          expect(find.text('Nguyễn Văn An'), findsOneWidget);
          await captureReview(tester, appBoundary, 'admin-users');
          await tester.tap(find.text('Giám sát').last);
          await tester.pumpAndSettle();
          await tester.tap(find.text('Chứng từ').last);
          await tester.pumpAndSettle();
          await captureReview(tester, appBoundary, 'admin-documents');
          await tester.tap(find.textContaining('Chứng từ cần kiểm tra'));
        }
        await tester.pumpAndSettle();
        if (role == 'ACCOUNTANT') {
          await captureReview(tester, appBoundary, 'accountant-review');
          await tester.scrollUntilVisible(find.text('Yêu cầu bổ sung'), 300);
          await tester.tap(find.text('Yêu cầu bổ sung'));
          await tester.pumpAndSettle();
          await captureReview(tester, appBoundary, 'accountant-request-info');
          await tester.tap(find.text('Gửi yêu cầu bổ sung'));
          await tester.pumpAndSettle();
          expect(find.text('Vui lòng nhập lý do'), findsOneWidget);
          await tester.enterText(
            find.byType(TextFormField),
            'Cần ảnh rõ tổng tiền',
          );
          await tester.tap(find.text('Gửi yêu cầu bổ sung'));
          await tester.pumpAndSettle();
          expect(documents.workflowAction, 'REQUEST_INFO');
          expect(documents.workflowNote, 'Cần ảnh rõ tổng tiền');
          await tester.tap(find.text('Xử lý trên Web'));
          await tester.pumpAndSettle();
          await tester.pump(const Duration(seconds: 5));
          await tester.pumpAndSettle();
          await captureReview(tester, appBoundary, 'web-handoff');
          String? copied;
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
              .setMockMethodCallHandler(SystemChannels.platform, (call) async {
                if (call.method == 'Clipboard.setData') {
                  copied = (call.arguments as Map)['text'] as String?;
                }
                return null;
              });
          await tester.tap(find.text('Sao chép mã chứng từ'));
          await tester.pumpAndSettle();
          expect(copied, '247');
        } else {
          expect(find.text('Duyệt chứng từ'), findsNothing);
          expect(find.text('Gửi kế toán rà soát'), findsNothing);
        }
        expect(tester.takeException(), isNull);
      });
    }
    testWidgets(
      'navigation shell opens profile, password and upload at $width',
      (tester) async {
        tester.view.physicalSize = Size(width, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final appBoundary = GlobalKey();
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              authProvider.overrideWith((ref) => _Auth()),
              dashboardRepositoryProvider.overrideWithValue(_DashboardRepo()),
              documentRepositoryProvider.overrideWithValue(_Documents()),
            ],
            child: RepaintBoundary(
              key: appBoundary,
              child: const SmartInvoiceApp(),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await captureReview(tester, appBoundary, 'employee-shell');
        await tester.tap(find.text('Thông báo').last);
        await tester.pumpAndSettle();
        await captureReview(tester, appBoundary, 'notifications');
        await tester.tap(find.text('Đến danh sách chứng từ'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Tài khoản').last);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Đổi mật khẩu'));
        await tester.pumpAndSettle();
        expect(find.text('Mật khẩu hiện tại'), findsOneWidget);
        await tester.pageBack();
        await tester.pumpAndSettle();
        await tester.tap(find.text('+'));
        await tester.pumpAndSettle();
        expect(find.text('Chụp ảnh'), findsOneWidget);
        await captureReview(tester, appBoundary, 'upload-source');
        expect(tester.takeException(), isNull);
      },
    );
    testWidgets('Figma screens render and filter works at width $width', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final repository = _Documents();
      final boundary = GlobalKey();
      Future<void> screen(Widget child, String name) async {
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              authProvider.overrideWith((ref) => _Auth()),
              dashboardRepositoryProvider.overrideWithValue(_DashboardRepo()),
              documentRepositoryProvider.overrideWithValue(repository),
            ],
            child: RepaintBoundary(
              key: boundary,
              child: MaterialApp(
                debugShowCheckedModeBanner: false,
                theme: AppTheme.lightTheme,
                home: Scaffold(body: child),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull, reason: '$name at $width');
        if (Platform.environment['UI_REVIEW_OUTPUT'] != null && width == 390) {
          final render =
              boundary.currentContext!.findRenderObject()
                  as RenderRepaintBoundary;
          await tester.runAsync(() async {
            final image = await render.toImage(pixelRatio: 1);
            final bytes = await image.toByteData(
              format: ui.ImageByteFormat.png,
            );
            final directory = Directory(
              Platform.environment['UI_REVIEW_OUTPUT']!,
            );
            await directory.create(recursive: true);
            await File(
              '${directory.path}/$name.png',
            ).writeAsBytes(bytes!.buffer.asUint8List());
            image.dispose();
          });
        }
      }

      await screen(const LoginPage(), 'login');
      await screen(const DashboardPage(), 'dashboard');
      await screen(const MyDocumentsPage(), 'documents');
      await tester.tap(find.text('Lọc'));
      await tester.pumpAndSettle();
      await captureReview(tester, boundary, 'document-filter');
      await tester.tap(find.text('Yêu cầu bổ sung'));
      await tester.tap(find.text('Áp dụng bộ lọc'));
      await tester.pumpAndSettle();
      expect(repository.calls.last.review, 'CORRECTED');
      expect(tester.takeException(), isNull);
      await screen(const DocumentDetailPage(documentId: 247), 'detail');
      expect(find.text('Ảnh hóa đơn thiếu phần tổng tiền.'), findsOneWidget);
      await screen(const ProfilePage(), 'profile');
    });
  }
}
