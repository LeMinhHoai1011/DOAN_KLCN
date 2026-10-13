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

## AI document type classification

AI classification uses only rows with `document_types.active = true`; the catalog is
loaded from the database at request time. The recognized codes are
`VAT_INVOICE`, `RECEIPT`, `PAYMENT_VOUCHER`, `RECEIPT_VOUCHER`, `CONTRACT`, and
`OTHER`, when those exact codes are active in the deployment. No document type
codes are implicitly created or enabled by the AI pipeline.

The classification confidence is returned as `confidence`; OCR confidence remains
separate on the OCR result. `reason` stores the AI's `DOCUMENT_TYPE_REASON`
evidence and review reasons. Missing document-type evidence requires review.
Classification responses expose the effective `documentType` separately from
the original AI proposal in `predictedDocumentType`.

An administrator or accountant can correct the effective type through
`PUT /api/v1/documents/{id}/classification/correction`:

```json
{"documentType":"RECEIPT"}
```

The selected code must exist and be active. A correction preserves the AI proposal
as evidence. Corrections and reprocessing cannot change an `APPROVED` document.
Reprocessing a PDF re-renders and analyzes its pages individually.

No OpenRouter, OpenAI, Gemini, Redis, Kafka, or Docker integration is implemented in this phase.

## Tess4J OCR runtime

The backend uses `./tessdata` as its default Tess4J data directory and requests
`vie+eng` by default. Run Spring Boot with the backend project directory as the
working directory, or set `TESSDATA_PATH` to the absolute directory containing
both `vie.traineddata` and `eng.traineddata`. `OCR_LANGUAGE` can override the
requested language. Missing language data is reported as an OCR deployment error;
the service does not silently retry with English.

The checked-in models are from the official
[tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast)
repository, revision `923915d4ced2a7235221788285785a29c4a42d4a` (Apache-2.0).
Each OCR log includes `requestedLanguage`, `effectiveLanguage`, `tessdataPath`,
`vieAvailable`, `engAvailable`, and `fallbackUsed`.
