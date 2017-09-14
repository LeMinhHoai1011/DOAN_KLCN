# Backend Core API Notes

Run locally with temporary H2 database:

```powershell
.\mvnw.cmd spring-boot:run
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

H2 console:

```text
http://localhost:8080/h2-console
```

H2 JDBC URL:

```text
jdbc:h2:mem:invoice_core
```

Basic flow:

1. Register:

```http
POST /api/v1/auth/register
```

2. Login:

```http
POST /api/v1/auth/login
```

3. Use returned token:

```http
Authorization: Bearer <token>
```

Main protected endpoints:

```http
GET /api/v1/users/me
PUT /api/v1/users/me
PUT /api/v1/users/me/password

POST /api/v1/documents
POST /api/v1/documents/upload
GET /api/v1/documents
GET /api/v1/documents/{id}
PUT /api/v1/documents/{id}
DELETE /api/v1/documents/{id}

GET /api/v1/documents/{id}/ocr
PUT /api/v1/documents/{id}/ocr

POST /api/v1/invoices
GET /api/v1/invoices
GET /api/v1/invoices/{id}
PUT /api/v1/invoices/{id}
DELETE /api/v1/invoices/{id}

GET /api/v1/documents/{id}/classification
PUT /api/v1/documents/{id}/classification
POST /api/v1/documents/{id}/classification/approve
POST /api/v1/documents/{id}/classification/review
PUT /api/v1/documents/{id}/classification/correction

GET /api/v1/dashboard/statistics
```

No AI, OCR engine, OpenRouter, OpenAI, Gemini, MinIO, Redis, Kafka, or Docker integration is implemented in this phase.
