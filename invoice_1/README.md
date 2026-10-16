# invoice_1

Flutter employee application for Smart Invoice.

## Backend URL

The application uses the existing Spring Boot `/api/v1` endpoints. The default
development URL is configured for an Android Emulator:

```text
http://10.0.2.2:8081/api/v1
```

Override the URL at build/run time with `API_BASE_URL`. Use the backend machine's
LAN address for a physical phone, and an HTTPS URL for production:

```powershell
flutter run --dart-define=API_BASE_URL=http://192.168.1.20:8081/api/v1
```

Timeouts can be adjusted using `API_CONNECT_TIMEOUT_MS` and
`API_RECEIVE_TIMEOUT_MS`. The receive timeout defaults to 180 seconds because
the current backend upload endpoint runs AI processing before returning.
