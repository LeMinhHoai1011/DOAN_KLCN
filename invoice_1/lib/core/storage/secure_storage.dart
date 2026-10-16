import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../features/auth/data/models/user_model.dart';

class SecureStorage {
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  static const String _keyToken = 'jwt_token';
  static const String _keyUser = 'session_user';

  Future<void> saveToken(String token) {
    return _storage.write(key: _keyToken, value: token);
  }

  Future<String?> getToken() {
    return _storage.read(key: _keyToken);
  }

  Future<void> saveUser(UserModel user) {
    return _storage.write(key: _keyUser, value: jsonEncode(user.toJson()));
  }

  Future<UserModel?> getUser() async {
    final json = await _storage.read(key: _keyUser);
    if (json == null) return null;
    return UserModel.fromJson(jsonDecode(json) as Map<String, dynamic>);
  }

  Future<void> deleteToken() {
    return _storage.delete(key: _keyToken);
  }

  Future<void> clearSession() {
    return _storage.deleteAll();
  }
}
