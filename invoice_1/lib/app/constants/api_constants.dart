class ApiConstants {
  static const String webAppUrl = String.fromEnvironment(
    'WEB_APP_URL',
    defaultValue: 'http://localhost:5173',
  );
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8081/api/v1',
  );
  static const int connectTimeout = int.fromEnvironment(
    'API_CONNECT_TIMEOUT_MS',
    defaultValue: 15000,
  );
  static const int receiveTimeout = int.fromEnvironment(
    'API_RECEIVE_TIMEOUT_MS',
    defaultValue: 180000,
  );
}
