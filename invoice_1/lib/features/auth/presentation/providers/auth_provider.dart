import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/network/api_client.dart';
import '../../../../core/storage/secure_storage.dart';
import '../../data/auth_repository.dart';
import '../../data/models/user_model.dart';

final Provider<SecureStorage> secureStorageProvider =
    Provider<SecureStorage>((ref) => SecureStorage());
final Provider<ApiClient> apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(
    ref.watch(secureStorageProvider),
    onUnauthorized: () => ref.read(authProvider.notifier).expireSession(),
  );
});

final Provider<AuthRepository> authRepositoryProvider =
    Provider<AuthRepository>((ref) {
  return AuthRepository(
    ref.watch(apiClientProvider),
    ref.watch(secureStorageProvider),
  );
});

class AuthState {
  final bool isLoading;
  final bool isInitialized;
  final UserModel? user;
  final String? error;

  const AuthState({
    this.isLoading = false,
    this.isInitialized = false,
    this.user,
    this.error,
  });

  AuthState copyWith({
    bool? isLoading,
    bool? isInitialized,
    UserModel? user,
    bool clearUser = false,
    String? error,
    bool clearError = false,
  }) {
    return AuthState(
      isLoading: isLoading ?? this.isLoading,
      isInitialized: isInitialized ?? this.isInitialized,
      user: clearUser ? null : user ?? this.user,
      error: clearError ? null : error ?? this.error,
    );
  }
}

class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repository;

  AuthNotifier(this._repository) : super(const AuthState()) {
    restoreSession();
  }

  Future<void> restoreSession() async {
    try {
      final token = await _repository.getStoredToken();
      final cachedUser = await _repository.getStoredUser();
      if (token == null || token.isEmpty || cachedUser == null) {
        await _repository.logout();
        state = const AuthState(isInitialized: true);
        return;
      }

      try {
        final user = await _repository.getCurrentUser();
        state = AuthState(isInitialized: true, user: user);
      } catch (error) {
        final apiError = ApiException.from(error);
        if (apiError.statusCode == 401) {
          await _repository.logout();
          state = const AuthState(isInitialized: true);
        } else {
          state = AuthState(
            isInitialized: true,
            user: cachedUser,
            error: apiError.message,
          );
        }
      }
    } catch (error) {
      state = AuthState(
        isInitialized: true,
        error: ApiException.from(error).message,
      );
    }
  }

  Future<bool> login(String username, String password) async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final user = await _repository.login(username, password);
      state = AuthState(isInitialized: true, user: user);
      return true;
    } catch (error) {
      final apiError = ApiException.from(error);
      state = AuthState(isInitialized: true, error: apiError.message);
      return false;
    }
  }

  void expireSession() {
    state = const AuthState(isInitialized: true);
  }

  Future<void> logout() async {
    await _repository.logout();
    state = const AuthState(isInitialized: true);
  }
}

final StateNotifierProvider<AuthNotifier, AuthState> authProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref.watch(authRepositoryProvider));
});
