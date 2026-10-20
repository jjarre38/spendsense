# spendsense-ui

Angular frontend for SpendSense — AI-Powered Bank Statement Analyzer.

## Prerequisites

- Node.js 18+
- Angular CLI 21: `npm install -g @angular/cli`
- `spendsense-api` running on `http://localhost:8080`

## Setup

```bash
npm install --legacy-peer-deps
```

## Run (development)

```bash
ng serve
```

Opens at `http://localhost:4200`. All `/api` requests are proxied to `http://localhost:8080`.

## Project Structure

```
src/app/
├── core/
│   ├── guards/         AuthGuard
│   ├── interceptors/   AuthInterceptor (attaches JWT Bearer header)
│   ├── models/         auth.model.ts, statement.model.ts
│   └── services/       AuthService, StatementService
├── features/
│   ├── auth/           Login + Register (Sub-Task 5)
│   ├── upload/         PDF upload + progress (Sub-Task 6)
│   ├── transactions/   Transaction table + category override (Sub-Task 6)
│   └── dashboard/      Chart.js analytics dashboard (Sub-Task 7)
└── app-routing-module.ts  Lazy-loaded routes with AuthGuard
```

## Routes

| Path | Guard | Description |
|---|---|---|
| `/login` | — | Login form |
| `/register` | — | Register form |
| `/upload` | AuthGuard | PDF upload |
| `/transactions/:statementId` | AuthGuard | Transaction table |
| `/dashboard` | AuthGuard | Spending analytics |
