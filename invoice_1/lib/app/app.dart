import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'routes.dart';
import '../features/auth/presentation/providers/auth_provider.dart';
import 'theme/app_theme.dart';

class SmartInvoiceApp extends ConsumerStatefulWidget {
  const SmartInvoiceApp({super.key});

  @override
  ConsumerState<SmartInvoiceApp> createState() => _SmartInvoiceAppState();
}

class _SmartInvoiceAppState extends ConsumerState<SmartInvoiceApp> {
  late final ValueNotifier<AuthState> _authRefresh;
  late final GoRouter _router;
  late final ProviderSubscription<AuthState> _authSubscription;

  @override
  void initState() {
    super.initState();
    _authRefresh = ValueNotifier(ref.read(authProvider));
    _authSubscription = ref.listenManual<AuthState>(
      authProvider,
      (_, state) => _authRefresh.value = state,
    );
    _router = createAppRouter(_authRefresh);
  }

  @override
  void dispose() {
    _authSubscription.close();
    _router.dispose();
    _authRefresh.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'InvoiceFlow',
      theme: AppTheme.lightTheme,
      routerConfig: _router,
      debugShowCheckedModeBanner: false,
    );
  }
}
