class UserModel {
  String get effectiveRole {
    final assigned = {...roles, ?role};
    for (final candidate in ['ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER']) {
      if (assigned.contains(candidate)) return candidate;
    }
    return 'USER';
  }

  final int userId;
  final int? companyId;
  final String username;
  final String fullName;
  final String? email;
  final String? phone;
  final String? avatar;
  final String status;
  final String? role;
  final List<String> roles;

  UserModel({
    required this.userId,
    this.companyId,
    required this.username,
    required this.fullName,
    this.email,
    this.phone,
    this.avatar,
    this.status = 'UNKNOWN',
    this.role,
    this.roles = const [],
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    final rolesJson = json['roles'];
    return UserModel(
      userId: _asInt(json['id']),
      companyId: _asNullableInt(json['companyId']),
      username: json['username'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      avatar: json['avatar'] as String?,
      status: json['status']?.toString() ?? 'UNKNOWN',
      role: json['role']?.toString(),
      roles: rolesJson is List
          ? rolesJson.map((role) => role.toString()).toList()
          : const [],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': userId,
      'companyId': companyId,
      'username': username,
      'fullName': fullName,
      'email': email,
      'phone': phone,
      'avatar': avatar,
      'status': status,
      'role': role,
      'roles': roles,
    };
  }

  static int _asInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.parse(value);
    throw const FormatException(
      'Backend user response did not contain a numeric id.',
    );
  }

  static int? _asNullableInt(Object? value) {
    if (value == null) return null;
    return _asInt(value);
  }
}
