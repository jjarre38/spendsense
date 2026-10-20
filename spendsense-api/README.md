# spendsense-api

Spring Boot backend for **SpendSense** — an AI-powered bank statement analyzer.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ (or use the included `./mvnw` wrapper) |
| PostgreSQL | 14+ |

---

## 1 — Create the database

Connect to your local PostgreSQL instance and run:

```sql
CREATE USER spendsense WITH PASSWORD 'spendsense';
CREATE DATABASE spendsense_db OWNER spendsense;
```

> The application connects to `localhost:5432/spendsense_db` with credentials `spendsense / spendsense`.  
> Flyway will create all tables automatically on first startup.

---

## 2 — Set the Claude API key

Open `src/main/resources/application.properties` and replace the placeholder:

```properties
# Replace with your Anthropic API key before running
claude.api.key=YOUR_API_KEY_HERE
```

with your actual key from [console.anthropic.com](https://console.anthropic.com).
Until it is set, `POST /api/statements/{id}/categorize` returns `503` (uploads and parsing still work).

The model defaults to `claude-haiku-5-5` and can be changed with `claude.model`.

---

## 3 — (Optional) Change the JWT signing secret

The default value in `application.properties` is a placeholder:

```properties
jwt.secret=CHANGE_ME_TO_A_256_BIT_SECRET
```

Replace it with any random 32-byte (256-bit) base64-encoded string before running in any non-trivial environment.

---

## 4 — Run the application

```bash
./mvnw spring-boot:run
```

The API will start on **http://localhost:8080**.

Flyway applies `V1__init_schema.sql` on first startup, creating all tables in `spendsense_db`.

Run the tests with `./mvnw test`.

---

## API endpoints

All routes except register/login require `Authorization: Bearer <token>`.

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/auth/register` | Create account, returns JWT |
| POST | `/api/auth/login` | Returns JWT |
| GET | `/api/auth/me` | Current user |
| POST | `/api/statements/upload?bankType=REGIONS` | Upload + parse a PDF (multipart `file`) |
| GET | `/api/statements` | The user's statements, newest first |
| POST | `/api/statements/{id}/categorize` | Categorize transactions with Claude |
| GET | `/api/statements/{id}/transactions` | Transactions for a statement |
| GET | `/api/transactions/{statementId}` | Same, per the plan's Sub-Task 4 |
| PATCH | `/api/transactions/{id}/category` | User override (`{"category": "..."}`), records feedback |
| GET | `/api/statements/{id}/summary` | Spend by effective category, totals |
| GET | `/api/statements/monthly-spend` | Debit spend per month across all statements |

---

## Project structure

```
spendsense-api/
├── pom.xml
└── src/main/
    ├── java/com/spendsense/api/
    │   ├── SpendSenseApiApplication.java
    │   ├── config/          ← Security config added in Sub-Task 2
    │   ├── entity/
    │   │   ├── User.java
    │   │   ├── Statement.java
    │   │   ├── Transaction.java
    │   │   └── CategoryFeedback.java
    │   └── repository/
    │       ├── UserRepository.java
    │       ├── StatementRepository.java
    │       ├── TransactionRepository.java
    │       └── CategoryFeedbackRepository.java
    └── resources/
        ├── application.properties
        └── db/migration/
            └── V1__init_schema.sql
```

---

## Schema overview

| Table | Purpose |
|-------|---------|
| `users` | Registered accounts |
| `statements` | Uploaded PDF statements (one per upload) |
| `transactions` | Extracted line items from a statement |
| `category_feedback` | Audit log of user category corrections |
