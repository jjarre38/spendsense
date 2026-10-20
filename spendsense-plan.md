# SpendSense — AI-Powered Bank Statement Analyzer: Plan

## Top-Level Overview

**Goal:** Build a full-stack financial analysis application split across two separate repositories:
- **`spendsense-api`** — Spring Boot + PostgreSQL backend
- **`spendsense-ui`** — Angular frontend

**Scope:**
- JWT-based user authentication (register/login)
- PDF bank statement upload and transaction extraction via Apache PDFBox, initially targeting **Regions Bank** statement format
- Bank selector UI scaffolded for future multi-bank support (Regions as the first supported parser)
- Claude API integration to auto-categorize extracted transactions with JSON-schema validation and retry logic
- Chart.js-powered dashboard to visualize spending by category
- User override / feedback loop allowing correction of miscategorized transactions to improve future categorization accuracy
- UI built with **Angular + Tailwind CSS**

**Deployment:** Local only (no Docker/containerization in this iteration)

**Non-goals:**
- Parsers for banks other than Regions in this iteration (architecture supports adding them)
- Real-time data feeds or bank API integrations
- Production deployment, CI/CD pipelines, or containerization

---

## Architecture Overview

```
spendsense-ui (Angular)
  └── Auth Module (login/register)
  └── Upload Module (PDF drag-and-drop)
  └── Dashboard Module (Chart.js)
  └── Transactions Module (table + override UI)

spendsense-api (Spring Boot)
  ├── Auth Layer (JWT)
  ├── PDF Parsing (PDFBox)
  ├── AI Categorization (Claude API)
  └── PostgreSQL (Users, Statements, Transactions, Feedback)
```

---

## Sub-Tasks

---

### Sub-Task 1 — Backend Project Scaffolding & Database Schema

**Status:** `[x] done`

**Intent:**
Bootstrap the `spendsense-api` Spring Boot project with all required dependencies and define the full PostgreSQL schema that all subsequent sub-tasks will build on.

**Expected Outcomes:**
- Spring Boot project runnable locally at `http://localhost:8080`
- PostgreSQL database `spendsense_db` created with all tables
- Flyway migrations in place so schema is version-controlled
- Application properties configured for local DB connection

**Todo List:**
1. Initialize Spring Boot project (Spring Initializr) with dependencies: `Spring Web`, `Spring Data JPA`, `Spring Security`, `PostgreSQL Driver`, `Flyway`, `Lombok`, `Validation`
2. Add `pdfbox`, `java-jwt` (Auth0), and `spring-boot-starter-oauth2-resource-server` to `pom.xml`
3. Configure `application.properties` with local PostgreSQL datasource, JPA settings, and Flyway baseline
4. Create Flyway migration `V1__init_schema.sql` defining tables:
   - `users` — `id`, `email`, `password_hash`, `created_at`
   - `statements` — `id`, `user_id`, `filename`, `uploaded_at`, `status` (ENUM: PENDING, PARSED, CATEGORIZED, FAILED)
   - `transactions` — `id`, `statement_id`, `date`, `description`, `amount`, `type` (DEBIT/CREDIT), `ai_category`, `user_category`, `is_overridden`, `created_at`
   - `category_feedback` — `id`, `transaction_id`, `original_category`, `corrected_category`, `created_at`
5. Create JPA entity classes for each table
6. Create Spring Data JPA repository interfaces for each entity
7. Verify application starts and Flyway applies migration cleanly

**Relevant Context:**
- All future sub-tasks depend on this schema being stable
- `user_category` and `is_overridden` on `transactions` are the keys to the feedback loop
- `category_feedback` serves as the training signal audit log

---

### Sub-Task 2 — JWT Authentication (Register / Login)

**Status:** `[x] done`

**Intent:**
Implement stateless JWT-based authentication so all subsequent API endpoints can be secured per-user.

**Expected Outcomes:**
- `POST /api/auth/register` creates a new user (hashed password)
- `POST /api/auth/login` returns a signed JWT on valid credentials
- Spring Security filter validates JWT on all protected routes
- Unauthorized requests receive `401`

**Todo List:**
1. Create `AuthController` with `/register` and `/login` endpoints
2. Create `UserService` with `registerUser()` (BCrypt password hashing) and `authenticateUser()`
3. Create `JwtService` to generate and validate JWT tokens (signing key in `application.properties`)
4. Implement `JwtAuthFilter` extending `OncePerRequestFilter` — extracts and validates Bearer token, sets `SecurityContextHolder`
5. Configure `SecurityFilterChain` in `SecurityConfig` — permit `/api/auth/**`, require auth on all other routes, add `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`
6. Create `AuthRequest` and `AuthResponse` DTOs
7. Test register + login flow with Postman/curl, confirm JWT is returned and accepted on a protected ping endpoint

**Relevant Context:**
- The authenticated `userId` extracted from the JWT will be used to scope all statement and transaction queries
- Use Auth0 `java-jwt` library (`com.auth0:java-jwt`)

---

### Sub-Task 3 — PDF Upload & PDFBox Transaction Extraction

**Status:** `[x] done`

**Intent:**
Accept a PDF bank statement upload, extract raw text via PDFBox, parse transactions using regex/heuristics, and persist them to the `transactions` table linked to a `statements` record.

**Expected Outcomes:**
- `POST /api/statements/upload` accepts a multipart PDF and returns a `statementId`
- Statement record created with status `PENDING`, transitions to `PARSED` on success or `FAILED` on error
- Transactions extracted and persisted with `date`, `description`, `amount`, `type` — `ai_category` and `user_category` null at this stage
- Endpoint secured — only the authenticated user's statements are accessible

**Todo List:**
1. Create `StatementController` with `POST /api/statements/upload` (multipart file, accepts a `bankType` request param defaulting to `REGIONS`)
2. Create `StatementService` — saves the statement record, delegates to `PdfParserService`
3. Create a `BankParser` interface with a single `parse(String rawText): List<ParsedTransaction>` method — this is the extension point for future banks
4. Create `RegionsBankParser implements BankParser`:
   - Use `PDDocument` and `PDFTextStripper` from PDFBox to extract full text
   - Apply regex patterns targeting Regions Bank statement layout (date `\d{2}/\d{2}/\d{4}`, description, debit/credit columns, running balance)
   - Return a list of `ParsedTransaction` value objects
5. Create `PdfParserService` — accepts a `BankType` enum, selects the correct `BankParser` via a registry map, delegates parsing
6. Add `bank_type` column to `statements` table (already included in `V1__init_schema.sql`, so no V2 migration is needed)
7. Create `TransactionService` with `saveParsedTransactions()` — bulk insert to `transactions` table
8. Create `GET /api/statements/{id}/transactions` endpoint to return parsed transactions for a statement
9. Add `multipart.max-file-size` and `multipart.max-request-size` config (e.g. 10MB)
10. Handle parsing failures gracefully — update statement status to `FAILED`, return error response
11. Test with a real Regions Bank PDF, verify transactions are correctly extracted

**Relevant Context:**
- The `BankParser` interface is the key extensibility point — adding a new bank means implementing `BankParser` and registering it; no existing code needs to change
- Regions Bank PDFs use a column layout: date | description | amount | running balance — regex anchors on `\d{2}/\d{2}/\d{4}`
- PDFBox dependency: `org.apache.pdfbox:pdfbox:3.x`
- Parsing will never be 100% perfect — the AI categorization step in Sub-Task 4 works with the extracted description strings, so extraction quality directly affects categorization accuracy

---

### Sub-Task 4 — Claude API Integration for AI Categorization

**Status:** `[x] done`

**Intent:**
After transactions are parsed, invoke the Claude API to categorize each transaction into a spending category. Use JSON-schema validation to ensure structured output and implement retry logic for robustness.

**Expected Outcomes:**
- `POST /api/statements/{id}/categorize` triggers categorization for all transactions on a statement
- Each transaction's `ai_category` is populated (e.g. Groceries, Dining, Utilities, Transport, Entertainment, Health, Shopping, Income, Other)
- JSON-schema validation rejects malformed Claude responses and triggers a retry (up to 3 attempts)
- Statement status updated to `CATEGORIZED` on success
- Claude API key stored in `application.properties` (not hardcoded)

**Todo List:**
1. Add Claude API HTTP client dependency (`java.net.http.HttpClient` or OkHttp) and configure API key via `@Value("${claude.api.key}")` — store in `application.properties` as `claude.api.key=YOUR_API_KEY_HERE` with an inline comment instructing the developer to replace it before running
2. Create `ClaudeCategorizationService`:
   - Build a prompt that includes transaction descriptions and instructs Claude to return a JSON array matching a defined schema: `[{"description": "...", "category": "..."}]`
   - Define the expected JSON schema (9 fixed categories)
   - Send request to Claude API (`claude-3-5-haiku` or similar cost-effective model)
   - Parse and validate the response against the schema
3. Implement retry logic — on JSON parse failure or schema violation, retry up to 3 times with an adjusted prompt; after 3 failures, assign `"Other"` as fallback
4. Batch transactions in groups of 50–100 per Claude request to stay within token limits
5. Update `ai_category` on each transaction after successful categorization
6. Update statement status to `CATEGORIZED`
7. Expose `GET /api/transactions/{statementId}` returning full transaction list with categories
8. Test with 100+ real transactions, verify 95%+ categorization accuracy

**Relevant Context:**
- Claude API: `https://api.anthropic.com/v1/messages`
- Prompt engineering is critical — instruct Claude to respond ONLY in JSON, include schema in the system prompt
- Batching avoids per-transaction API calls which would be slow and costly
- The `user_category` field is intentionally left null here — it gets populated via the feedback loop in Sub-Task 6

---

### Sub-Task 5 — Angular Project Scaffolding & Auth Module

**Status:** `[x] done`

**Intent:**
Bootstrap the `spendsense-ui` Angular project and implement the Auth module (login/register screens, JWT storage, HTTP interceptor) so all subsequent UI modules have an authenticated foundation.

**Expected Outcomes:**
- Angular app running locally at `http://localhost:4200`
- `/login` and `/register` routes with functional forms
- On successful login, JWT stored in `localStorage`
- `AuthInterceptor` attaches `Authorization: Bearer <token>` header to all API requests
- `AuthGuard` redirects unauthenticated users to `/login`
- Proxy config routes `/api/**` to `http://localhost:8080`

**Todo List:**
1. Initialize Angular project with Angular CLI: `ng new spendsense-ui --routing --style=scss`
2. Install dependencies: `chart.js`, `ng2-charts`, `tailwindcss`, `@tailwindcss/forms`, `postcss`, `autoprefixer`; initialize Tailwind (`npx tailwindcss init`) and configure `tailwind.config.js` content paths to cover all Angular templates and component files
3. Create `AuthModule` with `LoginComponent` and `RegisterComponent`
4. Create `AuthService` — `login()`, `register()`, `logout()`, `isAuthenticated()`, `getToken()` using `localStorage`
5. Create `AuthInterceptor` — attaches Bearer token to outgoing requests
6. Create `AuthGuard` — checks `AuthService.isAuthenticated()`, redirects to `/login` if false
7. Configure Angular proxy (`proxy.conf.json`) to forward `/api` to `localhost:8080`
8. Set up `AppRoutingModule` with lazy-loaded feature modules and `canActivate: [AuthGuard]`
9. Test login/register against the live backend, confirm token is stored and sent on requests

**Relevant Context:**
- `spendsense-ui` is a separate repo from `spendsense-api`
- JWT is stateless — no session management needed on the frontend
- Tailwind CSS is the sole styling system — do not introduce Angular Material; use `@tailwindcss/forms` plugin for consistent form input styling

---

### Sub-Task 6 — PDF Upload Module & Transaction Table UI

**Status:** `[x] done`

**Intent:**
Build the Angular Upload module and Transaction table view so users can upload a bank statement, trigger parsing + categorization, and see the resulting transactions in a table with editable category overrides.

**Expected Outcomes:**
- `/upload` route with drag-and-drop PDF upload UI
- Upload calls `POST /api/statements/upload`, then auto-triggers `POST /api/statements/{id}/categorize`
- `/transactions/:statementId` route shows a paginated table with: date, description, amount, AI category, and an editable category dropdown
- Changing the category in the dropdown sends `PATCH /api/transactions/{id}/category` and records a `category_feedback` entry
- Overridden rows are visually distinguished (e.g. icon or highlighted field)

**Todo List:**
1. Create `UploadModule` with `UploadComponent`:
   - Drag-and-drop area (or file input) for PDF files
   - Progress indicator during upload + categorization
   - On completion, navigate to `/transactions/:statementId`
2. Create `StatementService` (Angular) — `uploadStatement()`, `categorizeStatement()`, `getTransactions()`
3. Add backend endpoint `PATCH /api/transactions/{id}/category` in `TransactionController`:
   - Updates `user_category` and sets `is_overridden = true`
   - Inserts a `category_feedback` record
4. Create `TransactionsModule` with `TransactionsComponent`:
   - Table styled with Tailwind utility classes (`divide-y`, `hover:bg-gray-50` rows)
   - Category column as a native `<select>` styled via `@tailwindcss/forms` with all 9 category options
   - On change emit, call `StatementService.overrideCategory()` which calls the PATCH endpoint
   - Visual indicator for overridden rows (e.g. accent text color or pencil icon via Tailwind)
5. Add `GET /api/statements` endpoint to list all statements for the authenticated user (for navigation/history)
6. Test full flow: upload PDF → auto-categorize → view table → override a category → confirm DB updated

**Relevant Context:**
- The `PATCH /api/transactions/{id}/category` endpoint and `category_feedback` insert must be atomic — use `@Transactional` in the service
- The override UI is the core feedback loop mechanism — it must be frictionless and instant (optimistic update pattern recommended)

---

### Sub-Task 7 — Chart.js Dashboard

**Status:** `[x] done`

**Intent:**
Build the analytics dashboard that visualizes spending by category using Chart.js, reflecting both AI-assigned and user-overridden categories in real time.

**Expected Outcomes:**
- `/dashboard` route showing spending analytics for a selected statement
- Donut/pie chart: spending breakdown by category (uses `user_category` if set, otherwise `ai_category`)
- Bar chart: monthly spending trend (if multiple statements uploaded)
- Summary cards: total spend, largest category, number of transactions
- Charts update immediately when user overrides a category

**Todo List:**
1. Create `DashboardModule` with `DashboardComponent`; layout uses Tailwind grid/flex utilities for stat cards and chart panels
2. Add backend endpoint `GET /api/statements/{id}/summary`:
   - Returns aggregated spend per category (preferring `user_category` over `ai_category`)
   - Returns total spend and transaction count
3. Create `DashboardService` (Angular) — `getSummary()`, `getAllStatements()`
4. Integrate `ng2-charts` (Chart.js wrapper):
   - `ChartComponent` for donut/pie chart — category breakdown
   - `ChartComponent` for bar chart — month-over-month spend (aggregate across statements)
5. Add summary stat cards (total spend, top category, transaction count)
6. Wire category overrides to refresh dashboard data — emit event from `TransactionsComponent` or use a shared `TransactionStateService`
7. Add statement selector dropdown on dashboard to switch between uploaded statements
8. Test with a populated DB — verify charts reflect overrides immediately

**Relevant Context:**
- Use `user_category ?? ai_category` logic in the SQL aggregation query (COALESCE) for accurate chart data
- `ng2-charts` v4+ is compatible with Chart.js v4 — confirm version alignment
- The shared state between the transactions table and dashboard can use an Angular `BehaviorSubject` in a service

---

## Data Flow Summary

```
User uploads PDF
  → StatementService saves record (PENDING)
  → PdfParserService extracts transactions
  → TransactionService persists raw transactions (PARSED)
  → ClaudeCategorizationService batches + calls Claude API
  → ai_category populated on each transaction (CATEGORIZED)
  → Dashboard displays charts using COALESCE(user_category, ai_category)
  → User corrects a category via dropdown
  → PATCH endpoint updates user_category + inserts category_feedback
  → Dashboard refreshes with corrected data
```

---

## Key Technical Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Auth mechanism | JWT (stateless) | No session state needed, simple Angular interceptor pattern |
| PDF parsing | Apache PDFBox | Industry standard, no external service dependency |
| Initial bank format | Regions Bank | First supported parser; BankParser interface enables adding others without modifying existing code |
| Bank parser design | BankParser interface + registry map | Open/closed principle — new banks plug in without touching existing parsers |
| AI model | Claude Haiku 5.5 (`claude-haiku-5-5`, configurable via `claude.model`) | Cost-effective for high-volume transaction categorization; Claude 3.5 Haiku was retired in Feb 2026 |
| Claude API key | application.properties placeholder | Developer replaces `YOUR_API_KEY_HERE` before running; never hardcoded |
| DB migrations | Flyway | Schema version control, auto-applied on startup |
| Frontend styling | Tailwind CSS + @tailwindcss/forms | Utility-first, no component library lock-in, consistent across all modules |
| Frontend charts | Chart.js via ng2-charts | Mature, well-documented, Angular-native wrapper |
| Category feedback | Separate `category_feedback` table | Audit trail for future model fine-tuning |
| Deployment | Local only | `localhost:8080` backend, `localhost:4200` frontend with proxy |
