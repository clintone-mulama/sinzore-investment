# Sinzore Investment Platform - Complete Backend API Documentation

Version: `1.0.0-production`  
Environment: `Node.js / Express / Firebase Admin / Daraja M-Pesa`

---

## Overview

The Sinzore Investment Platform Backend provides a secure, transactional API for managing member investment accounts, loans, repayments, M-Pesa STK push and B2C disbursements, automated ledger entries, integrity verification, notifications, background jobs, backups, and comprehensive report exports.

---

## Base URL

- **Development / Production Base**: `http://localhost:5000/api`

---

## Authentication & Authorization

All protected routes require a Bearer ID Token in the `Authorization` header:

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

### Roles

1. `ADMIN` / `TREASURER`: Full administrative access to member records, manual ledger adjustments, loan approvals/denials, backups, system health, and background job queues.
2. `MEMBER`: Access restricted to self-profile, self-contributions, self-loan applications, self-repayments, and self-notifications.

---

## Centralized Financial Core Rules

> [!IMPORTANT]
> ALL financial mutations (Contributions, Loans, Repayments, Disbursals, Adjustments) are handled atomically via `FinancialService` inside single Firestore transactions.

---

## API Routes Index

### 1. System & Health (`/api/system/health`, `/api/health`)
- `GET /api/health` - Comprehensive system status, database latency, M-Pesa status, job queue counts, and health verification logs.

### 2. Authentication (`/api/auth`)
- `POST /api/auth/login` - Verify Firebase ID Token, fetch/create user profile, return role permissions.
- `GET /api/auth/me` - Get current authenticated user profile.

### 3. M-Pesa Payments & Callbacks (`/api/mpesa`)
- `POST /api/mpesa/stkpush` - Initiate M-Pesa STK Push payment for contribution or loan repayment.
  - Body: `{ "phoneNumber": "254712345678", "amount": 1000, "transactionType": "CONTRIBUTION" | "LOAN_REPAYMENT", "loanId"?: "LN_123", "accountReference": "SINZORE" }`
- `POST /api/mpesa/callback` - Public M-Pesa STK Callback handler.
- `POST /api/mpesa/b2c/disburse` - Initiate B2C Loan Disbursal to member mobile money.
  - Body: `{ "loanId": "LN_123", "phoneNumber": "254712345678", "amount": 10000 }`
- `POST /api/mpesa/retry/:transactionId` - Manually or programmatically retry a failed payment transaction.

### 4. Contributions (`/api/contributions`)
- `POST /api/contributions` - Record manual contribution (Admin/Treasurer).
  - Body: `{ "memberId": "USR_123", "memberName": "John Doe", "amount": 5000, "paymentMethod": "CASH", "referenceId": "REF_123" }`
- `GET /api/contributions/my` - Fetch current member's contributions.
- `GET /api/contributions/all` - Fetch all contributions (Admin).

### 5. Loans (`/api/loans`)
- `POST /api/loans/apply` - Submit loan application.
  - Body: `{ "principalAmount": 10000, "durationMonths": 3, "purpose": "Emergency" }`
- `POST /api/loans/:loanId/approve` - Approve loan & trigger disbursal logic (Admin/Treasurer).
- `POST /api/loans/:loanId/deny` - Deny loan application with reason (Admin/Treasurer).
- `POST /api/loans/manual` - Record manual offline loan disbursal (Admin).
- `GET /api/loans/my` - Fetch member's loans.
- `GET /api/loans/all` - Fetch all system loans (Admin).

### 6. Loan Repayments (`/api/repayments`)
- `POST /api/repayments` - Record manual loan repayment.
  - Body: `{ "loanId": "LN_123", "amount": 2000, "paymentMethod": "CASH", "receiptNumber": "RCT_123" }`
- `GET /api/repayments/loan/:loanId` - Fetch repayments for a specific loan.

### 7. Statements & Dashboard (`/api/statements`, `/api/dashboard`)
- `GET /api/statements/my` - Member financial statement (contributions, active loan balance, repayment history).
- `GET /api/dashboard/stats` - Central dashboard summary (Total Treasury, Total Assets, Active Loans, Total Members).

### 8. Treasury Integrity Engine (`/api/integrity`)
- `POST /api/integrity/verify` - Execute non-destructive treasury recalculation from ledger baseline.
- `GET /api/integrity/alerts` - Retrieve unresolved integrity alerts.
- `GET /api/integrity/discrepancies` - Retrieve discrepancy reports.

### 9. Background Jobs Queue (`/api/jobs`)
- `GET /api/jobs/status` - Summary of active, pending, completed, and dead-letter jobs.
- `POST /api/jobs/enqueue` - Enqueue delayed or scheduled background tasks.
- `POST /api/jobs/process` - Execute pending ready background tasks.
- `GET /api/jobs/dead-letter` - View failed jobs in dead-letter queue.

### 10. Backup & Recovery (`/api/backup`)
- `POST /api/backup/snapshot` - Generate full database snapshot with SHA256 checksum.
- `GET /api/backup/export/ledger` - Export ledger records.
- `GET /api/backup/export/statistics` - Export statistics summary.
- `GET /api/backup/export/config` - Export system settings.
- `GET /api/backup/verify/:backupId` - Validate backup checksum integrity.
- `GET /api/backup/history` - List past backup metadata.

### 11. Notification Backend (`/api/notifications`)
- `GET /api/notifications` - Get user notifications (including role broadcasts).
- `GET /api/notifications/unread-count` - Unread badge count.
- `PUT /api/notifications/:id/read` - Mark notification as read.
- `PUT /api/notifications/read-all` - Mark all notifications as read.
- `DELETE /api/notifications/:id` - Delete notification.
- `POST /api/notifications/broadcast` - Broadcast notification to role or all members (Admin).

### 12. Reports Export (`/api/reports`)
- `GET /api/reports/member-statement/:memberId?` - Formatted statement data for PDF rendering.
- `GET /api/reports/treasury` - Treasury and liquidity export.
- `GET /api/reports/contributions` - Contributions export.
- `GET /api/reports/loans` - Loans export.
- `GET /api/reports/repayments` - Repayments export.
- `GET /api/reports/ledger` - Ledger export.
- `GET /api/reports/audit-logs` - Audit trail export.
- `GET /api/reports/monthly-summary` - Monthly performance export.
- `GET /api/reports/annual-summary` - Annual performance export.

---

## Standard Error Response Format

```json
{
  "success": false,
  "message": "Error description message",
  "error": "SPECIFIC_ERROR_CODE",
  "timestamp": "2026-07-22T04:00:00.000Z"
}
```

---

## Common HTTP Status Codes

- `200 OK`: Successful operation.
- `201 Created`: Resource successfully created.
- `400 Bad Request`: Validation failure or missing required fields.
- `401 Unauthorized`: Invalid or missing Firebase ID Token.
- `403 Forbidden`: Insufficient role permissions.
- `404 Not Found`: Resource or route not found.
- `409 Conflict`: Duplicate transaction or receipt detected.
- `500 Internal Server Error`: Server exception handled by global error middleware.
