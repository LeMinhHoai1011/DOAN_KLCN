import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:go_router/go_router.dart';
import '../features/auth/presentation/providers/auth_provider.dart';
import '../features/auth/presentation/pages/login_page.dart';
import '../features/dashboard/presentation/pages/dashboard_page.dart';
import '../features/documents/presentation/pages/my_documents_page.dart';
import '../features/documents/presentation/pages/document_detail_page.dart';
import '../features/documents/presentation/pages/upload_document_page.dart';
import '../features/documents/presentation/pages/document_preview_page.dart';
import '../features/documents/presentation/pages/remote_document_preview_page.dart';
import '../features/documents/data/models/document_model.dart';
import '../features/notifications/presentation/pages/notifications_page.dart';
import '../features/profile/presentation/pages/profile_page.dart';
import '../shared/widgets/app_scaffold.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>();

GoRouter createAppRouter(ValueListenable<AuthState> authState) {
  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    refreshListenable: authState,
    initialLocation: '/loading',
    redirect: (context, state) {
      final location = state.matchedLocation;
      if (!authState.value.isInitialized) {
        return location == '/loading' ? null : '/loading';
      }
      if (authState.value.user == null) {
        return location == '/login' ? null : '/login';
      }
      if (location == '/login' || location == '/loading') {
        return '/dashboard';
      }
      return null;
    },
    routes: [
      GoRoute(
        path: '/loading',
        builder: (context, state) => const Scaffold(
          body: Center(child: CircularProgressIndicator()),
        ),
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginPage(),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/documents/upload',
        builder: (context, state) => UploadDocumentPage(
          versionDocument: state.extra as DocumentModel?,
        ),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/documents/preview',
        builder: (context, state) =>
            DocumentPreviewPage(args: state.extra as UploadPreviewArgs),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/documents/:id/preview',
        builder: (context, state) => RemoteDocumentPreviewPage(
          documentId: int.parse(state.pathParameters['id']!),
        ),
      ),
      GoRoute(
        parentNavigatorKey: _rootNavigatorKey,
        path: '/documents/:id',
        builder: (context, state) => DocumentDetailPage(
          documentId: int.parse(state.pathParameters['id']!),
        ),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return MainScaffold(navigationShell: navigationShell);
        },
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/dashboard',
                builder: (context, state) => const DashboardPage(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/documents',
                builder: (context, state) => const MyDocumentsPage(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/notifications',
                builder: (context, state) => const NotificationsPage(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/profile',
                builder: (context, state) => const ProfilePage(),
              ),
            ],
          ),
        ],
      ),
    ],
  );
}
