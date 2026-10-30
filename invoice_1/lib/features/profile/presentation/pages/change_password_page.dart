import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../app/theme/app_colors.dart';
import '../../../auth/presentation/providers/auth_provider.dart';

class ChangePasswordPage extends ConsumerStatefulWidget {
  const ChangePasswordPage({super.key});
  @override
  ConsumerState<ChangePasswordPage> createState() => _ChangePasswordPageState();
}

class _ChangePasswordPageState extends ConsumerState<ChangePasswordPage> {
  final _key = GlobalKey<FormState>();
  final _current = TextEditingController(),
      _next = TextEditingController(),
      _confirm = TextEditingController();
  bool _busy = false;
  String? _error;
  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_key.currentState!.validate() || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(authRepositoryProvider)
          .changePassword(_current.text, _next.text);
      if (mounted) {
        _current.clear();
        _next.clear();
        _confirm.clear();
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Đã đổi mật khẩu.')));
        Navigator.pop(context);
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => _error =
              'Không thể đổi mật khẩu. Kiểm tra mật khẩu hiện tại và thử lại.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Đổi mật khẩu')),
    body: SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Form(
          key: _key,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('Mật khẩu mới từ 8–100 ký tự, gồm chữ cái và chữ số.'),
              const SizedBox(height: 24),
              AppTextField(
                label: 'Mật khẩu hiện tại',
                controller: _current,
                isPassword: true,
                validator: (v) =>
                    v == null || v.isEmpty ? 'Nhập mật khẩu hiện tại' : null,
              ),
              const SizedBox(height: 20),
              AppTextField(
                label: 'Mật khẩu mới',
                controller: _next,
                isPassword: true,
                validator: (v) =>
                    v == null ||
                        v.length < 8 ||
                        v.length > 100 ||
                        !RegExp(r'[A-Za-z]').hasMatch(v) ||
                        !RegExp(r'\d').hasMatch(v)
                    ? 'Cần 8–100 ký tự, gồm chữ cái và chữ số'
                    : null,
              ),
              const SizedBox(height: 20),
              AppTextField(
                label: 'Nhập lại mật khẩu mới',
                controller: _confirm,
                isPassword: true,
                validator: (v) => v != _next.text ? 'Mật khẩu chưa khớp' : null,
              ),
              const SizedBox(height: 24),
              if (_error != null)
                Text(_error!, style: const TextStyle(color: AppColors.error)),
              AppButton(
                text: 'Lưu mật khẩu',
                isLoading: _busy,
                onPressed: _save,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
