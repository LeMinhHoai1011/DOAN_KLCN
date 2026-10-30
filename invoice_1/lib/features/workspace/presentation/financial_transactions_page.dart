import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../shared/widgets/flow_widgets.dart';
import '../data/workspace_repository.dart';

String financialConfirmation(String value) =>
    const {
      'CONFIRMED': 'Đã xác nhận',
      'CANCELLED': 'Đã hủy',
      'UNVERIFIED': 'Chưa xác minh',
    }[value] ??
    'Chưa xác minh';
String financialMoney(num amount) =>
    '${NumberFormat.decimalPattern('vi').format(amount)}đ';

class FinancialConfirmationPill extends StatelessWidget {
  final String value;
  const FinancialConfirmationPill({super.key, required this.value});
  @override
  Widget build(BuildContext context) {
    final color = value == 'CONFIRMED'
        ? AppColors.success
        : value == 'CANCELLED'
        ? AppColors.error
        : AppColors.warning;
    final surface = value == 'CONFIRMED'
        ? AppColors.successSurface
        : value == 'CANCELLED'
        ? AppColors.errorSurface
        : AppColors.warningSurface;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text(
        financialConfirmation(value),
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}

class FinancialTransactionsPage extends ConsumerStatefulWidget {
  const FinancialTransactionsPage({super.key});
  @override
  ConsumerState<FinancialTransactionsPage> createState() =>
      _FinancialTransactionsPageState();
}

class _FinancialTransactionsPageState
    extends ConsumerState<FinancialTransactionsPage> {
  List<FinancialEntry> _entries = [];
  Object? _error;
  bool _busy = true, _last = true;
  int _page = 0, _version = 0;
  String? _type, _confirmation;
  DateTime _month = DateTime(DateTime.now().year, DateTime.now().month);
  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load({bool more = false}) async {
    if (more && _busy) return;
    final version = ++_version;
    var rawPage = more ? _page + 1 : 0;
    final type = _type, confirmation = _confirmation;
    final from = DateFormat('yyyy-MM-dd').format(_month);
    final to = DateFormat(
      'yyyy-MM-dd',
    ).format(DateTime(_month.year, _month.month + 1, 0));
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final collected = <FinancialEntry>[];
      bool last;
      do {
        final result = await ref
            .read(workspaceRepositoryProvider)
            .transactions(
              page: rawPage,
              type: type,
              dateFrom: from,
              dateTo: to,
            );
        if (!mounted || version != _version) return;
        collected.addAll(
          result.entries.where(
            (e) => confirmation == null || e.confirmation == confirmation,
          ),
        );
        last = result.last || result.entries.isEmpty;
        if (last || confirmation == null || collected.length >= 20) break;
        rawPage++;
      } while (true);
      setState(() {
        _entries = more ? [..._entries, ...collected] : collected;
        _page = rawPage;
        _last = last;
        _busy = false;
      });
    } catch (e) {
      if (mounted && version == _version) {
        setState(() {
          _error = e;
          _busy = false;
        });
      }
    }
  }

  Future<void> _pickMonth() async {
    final date = await showDatePicker(
      context: context,
      initialDate: _month,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100, 12, 31),
      helpText: 'Chọn một ngày trong tháng cần xem',
    );
    if (date != null && mounted) {
      setState(() {
        _month = DateTime(date.year, date.month);
        _entries = [];
      });
      await _load();
    }
  }

  Future<void> _pickConfirmation() async {
    final result = await showModalBottomSheet<String>(
      context: context,
      useSafeArea: true,
      builder: (context) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Lọc trạng thái',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 20),
            ...['', 'CONFIRMED', 'UNVERIFIED', 'CANCELLED'].map(
              (value) => ListTile(
                title: Text(
                  value.isEmpty ? 'Tất cả' : financialConfirmation(value),
                ),
                selected: (_confirmation ?? '') == value,
                onTap: () => Navigator.pop(context, value),
              ),
            ),
          ],
        ),
      ),
    );
    if (result != null && mounted) {
      setState(() {
        _confirmation = result.isEmpty ? null : result;
        _entries = [];
      });
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    child: Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const FlowHeader(
                title: 'Kế toán • Thu chi nhỏ lẻ',
                subtitle: 'Giao dịch và xác nhận thanh toán',
              ),
              Row(
                children: [
                  for (final entry in [
                    (null, 'Tất cả'),
                    ('INCOME', 'Phiếu thu'),
                    ('EXPENSE', 'Phiếu chi'),
                  ]) ...[
                    if (entry.$1 != null) const SizedBox(width: 6),
                    Expanded(
                      child: SizedBox(
                        height: 42,
                        child: FilledButton(
                          style: FilledButton.styleFrom(
                            backgroundColor: _type == entry.$1
                                ? AppColors.primarySurface
                                : Colors.white,
                            foregroundColor: _type == entry.$1
                                ? AppColors.primary
                                : AppColors.textSecondary,
                            padding: const EdgeInsets.symmetric(horizontal: 4),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          onPressed: () {
                            setState(() {
                              _type = entry.$1;
                              _entries = [];
                            });
                            _load();
                          },
                          child: Text(
                            entry.$2,
                            style: const TextStyle(fontSize: 13),
                          ),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 12),
              Wrap(
                children: [
                  TextButton(
                    onPressed: _pickMonth,
                    child: Text('Tháng ${_month.month}/${_month.year}'),
                  ),
                  TextButton(
                    onPressed: _pickConfirmation,
                    child: Text(
                      _confirmation == null
                          ? 'Lọc trạng thái ⌄'
                          : '${financialConfirmation(_confirmation!)} ⌄',
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        Expanded(
          child: _busy && _entries.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : _error != null && _entries.isEmpty
              ? FlowError(error: _error!, onRetry: _load)
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(20),
                    children: [
                      if (_entries.isEmpty)
                        const Card(
                          child: Padding(
                            padding: EdgeInsets.all(28),
                            child: Text(
                              'Không có giao dịch phù hợp trong tháng đã chọn.',
                            ),
                          ),
                        ),
                      ..._entries.map(
                        (entry) => Padding(
                          padding: const EdgeInsets.only(bottom: 14),
                          child: Material(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(16),
                            clipBehavior: Clip.antiAlias,
                            child: InkWell(
                              onTap: () => context.push(
                                '/financial-transactions/${entry.id}',
                                extra: entry,
                              ),
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      '#${entry.id} • ${entry.description ?? entry.category ?? (entry.type == 'INCOME' ? 'Phiếu thu' : 'Phiếu chi')}',
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                    const SizedBox(height: 12),
                                    Text(
                                      '${financialConfirmation(entry.confirmation)}${entry.documentId == null ? '' : ' • Chứng từ #${entry.documentId}'}',
                                      style: const TextStyle(
                                        fontSize: 12,
                                        color: AppColors.textSecondary,
                                      ),
                                    ),
                                    const SizedBox(height: 18),
                                    Wrap(
                                      spacing: 12,
                                      runSpacing: 8,
                                      children: [
                                        Text(
                                          financialMoney(entry.amount),
                                          style: const TextStyle(
                                            fontSize: 20,
                                            fontWeight: FontWeight.w600,
                                          ),
                                        ),
                                        FinancialConfirmationPill(
                                          value: entry.confirmation,
                                        ),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                      if (_error != null)
                        TextButton(
                          onPressed: () => _load(more: true),
                          child: const Text(
                            'Chưa tải được trang tiếp theo. Thử lại',
                          ),
                        ),
                      if (!_last)
                        Center(
                          child: _busy
                              ? const CircularProgressIndicator()
                              : OutlinedButton(
                                  onPressed: () => _load(more: true),
                                  child: const Text('Tải thêm'),
                                ),
                        ),
                    ],
                  ),
                ),
        ),
      ],
    ),
  );
}

class FinancialDetailPage extends StatelessWidget {
  final FinancialEntry entry;
  const FinancialDetailPage({super.key, required this.entry});
  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF8FAFC),
    body: SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
        children: [
          FlowHeader(
            title: 'Kế toán • Chi tiết thu chi',
            subtitle: 'Thông tin giao dịch #${entry.id}',
            trailing: IconButton(
              tooltip: 'Quay lại',
              onPressed: () => context.pop(),
              icon: const Icon(Icons.close, size: 20),
            ),
          ),
          _row('Mã giao dịch', '#${entry.id}'),
          _row('Loại', entry.type == 'INCOME' ? 'Phiếu thu' : 'Phiếu chi'),
          _row('Số tiền', financialMoney(entry.amount)),
          _row('Trạng thái', financialConfirmation(entry.confirmation)),
          _row(
            'Chứng từ liên kết',
            entry.documentId == null
                ? 'Chưa có liên kết'
                : '#${entry.documentId}',
          ),
          const SizedBox(height: 24),
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Duyệt hóa đơn ≠ đã thanh toán',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: AppColors.warning,
                    ),
                  ),
                  SizedBox(height: 18),
                  Text(
                    'Chỉ tính khoản tiền đã xác nhận.',
                    style: TextStyle(
                      fontSize: 13,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 28),
          if (entry.documentId != null)
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.primarySurface,
                foregroundColor: AppColors.primary,
                minimumSize: const Size.fromHeight(48),
              ),
              onPressed: () => context.push('/documents/${entry.documentId}'),
              child: const Text('Xem chứng từ liên kết'),
            ),
        ],
      ),
    ),
  );
  Widget _row(String title, String value) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(
                fontSize: 13,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              value,
              style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    ),
  );
}
