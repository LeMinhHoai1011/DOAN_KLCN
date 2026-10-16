import '../../../../core/network/api_client.dart';
import '../../../../core/storage/secure_storage.dart';
import 'models/user_model.dart';

class AuthRepository {
  final ApiClient _apiClient;
  final SecureStorage _secureStorage;

  AuthRepository(this._apiClient, this._secureStorage);

  Future<UserModel> login(String username, String password) async {
    try {
      final response = await _apiClient.dio.post<Map<String, dynamic>>(
        '/auth/login',
        data: {'username': username, 'password': password},
      );
      final data = response.data;
      final token = data?['token'];
      final userJson = data?['user'];
      if (token is! String ||
          token.isEmpty ||
          userJson is! Map<String, dynamic>) {
        throw const FormatException('Phản hồi đăng nhập không đúng định dạng.');
      }

      await _secureStorage.saveToken(token);
      final user = UserModel.fromJson(userJson);
      await _secureStorage.saveUser(user);
      return user;
    } catch (error) {
      if (error is FormatException) rethrow;
      throw ApiException.from(error);
    }
  }

  Future<UserModel> getCurrentUser() async {
    try {
      final response =
          await _apiClient.dio.get<Map<String, dynamic>>('/users/me');
      final data = response.data;
      if (data == null) {
        throw const FormatException('Phản hồi thông tin tài khoản bị trống.');
      }
      final user = UserModel.fromJson(data);
      await _secureStorage.saveUser(user);
      return user;
    } catch (error) {
      if (error is FormatException) rethrow;
      throw ApiException.from(error);
    }
  }

  Future<String?> getStoredToken() => _secureStorage.getToken();

  Future<UserModel?> getStoredUser() => _secureStorage.getUser();

  Future<void> logout() => _secureStorage.clearSession();
}
