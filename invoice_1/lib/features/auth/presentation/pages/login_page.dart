import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/auth_provider.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../../../../app/theme/app_colors.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});
  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _formKey = GlobalKey<FormState>();
  final _username = TextEditingController(),
      _password = TextEditingController();
  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    if (!_formKey.currentState!.validate()) return;
    final success = await ref
        .read(authProvider.notifier)
        .login(_username.text.trim(), _password.text);
    if (success && mounted) context.go('/dashboard');
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(authProvider);
    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) => SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: constraints.maxHeight),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const SizedBox(height: 64),
                    Center(
                      child: Container(
                        width: 108,
                        height: 108,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: AppColors.primarySurface,
                          borderRadius: BorderRadius.circular(28),
                        ),
                        child: const Text(
                          '▤',
                          style: TextStyle(
                            fontSize: 54,
                            color: AppColors.primary,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 28),
                    const Text(
                      'InvoiceFlow',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 30,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 5),
                    const Text(
                      'Quản lý chứng từ thông minh',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 62),
                    if (state.error != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: Text(
                          state.error!,
                          style: const TextStyle(color: AppColors.error),
                        ),
                      ),
                    AppTextField(
                      label: 'Tên đăng nhập',
                      hint: 'Nhập tài khoản',
                      controller: _username,
                      validator: (value) =>
                          value == null || value.trim().isEmpty
                          ? 'Vui lòng nhập tài khoản'
                          : null,
                    ),
                    const SizedBox(height: 26),
                    AppTextField(
                      label: 'Mật khẩu',
                      hint: '••••••••',
                      controller: _password,
                      isPassword: true,
                      validator: (value) => value == null || value.isEmpty
                          ? 'Vui lòng nhập mật khẩu'
                          : null,
                    ),
                    const SizedBox(height: 28),
                    AppButton(
                      text: 'Đăng nhập',
                      isLoading: state.isLoading,
                      onPressed: _login,
                    ),
                    const SizedBox(height: 20),
                    const Text(
                      'Đăng nhập bằng tài khoản đã được cấp',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 32),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
