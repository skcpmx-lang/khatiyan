# Khatiyan — Architecture

Single-module Android app (`app/`), Kotlin + Jetpack Compose, no DI framework, no network layer
(there is *no* INTERNET permission), light-only Bengali UI.

## Layers

```
presentation/  Compose screens + ViewModels (state in, events out; no DB access)
     │  via AppContainer (di/AppContainer.kt — lazily built singletons,
     │  ViewModel factory: utilities/ViewModelSupport.containerFactory)
     ▼
data/repository/  one repository per book (shop, loan, emi, personal, cashflow,
     │            due, dashboard, report, search) + LedgerMapper
     ▼
data/local/  Room: entities, DAOs, KhatiyanDatabase, query rows,
     │        ledger_view (8-arm UNION ALL over all money events)
     ▼
domain/  pure Kotlin (no Android imports):
           finance/  ScheduleGenerator, LoanMath, Allocation (FIFO),
                     OverpaymentException, DueEngine, InsightEngine
           model/    enums + snapshot models shared with UI
core/  Money (paisa math + parsing/formatting), BnDates (Bengali calendar text)
utilities/  BnText (বাংলা digits), DataBus (cross-screen "data changed" flow),
            FinanceValidationException
security/  PinCrypto (PBKDF2-HMAC-SHA256 + random salt), LockManager (state machine)
notification/  ReminderWorker (WorkManager, one-shot self-chaining daily at
               settings.reminderHour) + ReminderScheduler
data/backup/  BackupManager (JSON v1 file), BackupFile (+ summary/validate),
              CsvExporter (per-book ledgers + range transaction CSV),
              PdfExporter (Canvas → PdfDocument, report layout)
```

Rules of the house:

- **Viewmodels never touch DAOs directly** except read-only fallback lookups in editors
  (documented where used); all writes go through repositories so every mutation ends with
  `DataBus.poke()`, which every list/detail VM observes to refresh.
- **Domain objects are pure JVM** — they are unit-tested on the JVM without Robolectric.
- Room schema: version 1 with `exportSchema = true` (JSON snapshots under `app/schemas/` act as
  the migration ground truth); migrations are additive only when v2 arrives — v1 ships fresh
  installs, so no shipped user is affected yet.

## Money model

- Canonical unit: **paisa**, `Long`, max `999_999_999_999` (≈ 10 billion ৳). Never `Double`/`Float`.
- `Money.parse`: accepts বাংলা or ASCII digits, `৳₹$£` prefix, thousands separators, ≤2 real
  decimals (rejects otherwise — no silent rounding of user input).
- `Money.formatPlain` → `1,234.50`-style; `Money.format` adds symbol + বাংলা digits for the UI.
- Line totals (qty × unit price) use `BigDecimal` with `HALF_UP` at generation time (shop credit
  editor), then live forever as paisa.

## Books & balances

Each of the four books keeps its own rows, but **payment semantics are identical**:

| Book | Balance | Overpayment |
|---|---|---|
| Shop credit | Σ credits − Σ payments; per-credit outstanding via `Allocation.fifo` | reject unless confirmed; surplus = advance (negative balance) |
| Loan | totalPayable − Σ payments; per-installment `paidPaisa` via allocations | same; `LoanPaymentEntity.excessPaisa` |
| EMI | financed(total−down) − Σ payments; allocations like loans | same |
| Personal | Σ borrow − Σ repay per debt (FIFO oldest-first) | same; `excessPaisa` on repayment |

- **Schedules** (`ScheduleGenerator`): fixed base installment = `divRound(total, n)`,
  last installment = remainder → sum equals target exactly; weekly/monthly cadence with
  `LocalDate.plusWeeks/plusMonths` (month-end clamping is deliberate and tested).
- **Payment edit** (loan/EMI): repository `updatePayment` = overpayment recheck against the other
  payments, then `db.withTransaction { deletePayment; recordPayment }` — never a partial state.
- **Ledger view** (`ledger_view`): UNION ALL over shop credits/payments, loan+EMI
  payments, personal borrow/repay, income, expense — the single source for Reports, global
  search of transactions and the "সব লেনদেন" tab.

## Reminder design

`ReminderWorker` runs once (next occurrence of `reminderHour`), queries
`DueRepository.collect(today, windowDays = 1)` — due today or overdue, shows *one* summary
notification if anything is due/overdue today or past-due, then enqueues itself for the next
day — no periodic worker, no battery-wake spam. Per-obligation dedupe state lives in
`notification_state` so a reminder for one item fires at most once per day. Disabling reminders
cancels the schedule and clears dedupe state. No notification is ever sent without the user's
`POST_NOTIFICATIONS` grant (requested at onboarding finish and when toggling the setting).

## Security

- App lock is a **gate composable**, not navigation trickery: `AppRoot` in `MainActivity` shows
  `LockScreen` while `LockManager.locked == true`; backgrounding beyond 2 minutes re-locks.
- PIN: PBKDF2WithHmacSHA256, random 16-byte salt, ≥120k iterations, constant-time compare;
  salt+hash in DataStore. Verification always runs off the main thread (`Dispatchers.Default`).
- Biometric (optional): `BiometricPrompt` with `BIOMETRIC_STRONG or WEAK`; success unlocks the
  session only — biometrics never touch stored money or the PIN material.
- `FLAG_SECURE` is set by default (screenshot/recents-preview blocking), user-switchable.

## Backup format (`khatiyan-backup`)

JSON, `backupVersion: 1`, envelope `{ app, version, exportedAt, userName, counts, tables }` with
all tables as arrays of serialized entities. On import: strict field validation → known-version
check → row-count summary shown to the user → **single transaction**: clear all, reinsert with
original ids, remap nothing (ids preserved) → `DataBus.poke()`. A corrupt/partial file can never
be half-applied. Files move device→device only via SAF pickers (Drive, SD, any file manager).

## PDF/CSV export

- CSV: BOM + CRLF, RFC-4180 quoting; per-book ledgers and a range transaction export; written
  through `CsvExporter.writeTo` under a SAF `CreateDocument` URI.
- PDF: `PdfDocument` + Canvas (system fonts), multi-page header band/sections/footer with page
  numbers; composed from the same `ReportSnapshot` the Reports screen renders — the PDF can never
  disagree with the screen.

## Testing

- JVM: money parse/format edge cases, বাংলা digit round-trip, schedule exactness, flat-interest
  math (365-day year), FIFO allocation, overpayment exception semantics, due windows.
- Robolectric + in-memory Room: shop ledger (item totals, FIFO per-credit outstanding, credit
  delete) and loan lifecycle (schedule generation, allocation on payment, transactional payment
  edit including the rejected-edit-must-not-delete guarantee, advance-on-confirm overpayment).
- CI executes the same suites (`.github/workflows/ci.yml`) on JDK 17.
