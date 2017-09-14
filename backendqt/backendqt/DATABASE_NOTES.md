# Database Notes

Current default configuration uses H2 in-memory database so the backend can run before PostgreSQL credentials are finalized.

Update later:

1. Create PostgreSQL database:

```sql
CREATE DATABASE invoice_db;
```

2. Set environment variables:

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your_password"
$env:JWT_SECRET="replace_with_a_long_secure_secret"
```

3. Run with PostgreSQL profile:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=postgres
```

The application code does not need to change when switching from H2 to PostgreSQL.
