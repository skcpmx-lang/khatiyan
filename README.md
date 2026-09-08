# খতিয়ান (Khatiyan)

**আপনার সব হিসাব, এক জায়গায় — সম্পূর্ণ অফলাইন।**

Khatiyan is a free, offline-first, fully-Bengali personal-finance app for Android. It is built for
the way household and small-shop finance actually works in Bangladesh: shop credit for the মুদি
দোকান, bank/NGO loan installments, product EMIs, personal debts between friends and family, plus
day-to-day income and expense — one app, one consistent ledger.

- **Free forever.** No ads, no subscriptions, no premium tier.
- **Private by design.** The manifest declares **no INTERNET permission at all** — nothing can
  phone home even if it wanted to. No analytics, no tracking, no accounts. Every byte you enter
  lives in a local Room database on your device; backups are files *you* choose to export through
  the system file picker.
- **Light mode only, Bengali UI only** — including Bengali numerals (৳১,২৩৪.৫০) — tuned for
  outdoor readability on budget phones.

## What it does

| Area | Features |
|---|---|
| Shop credit (দোকানের বাকি) | Per-shop ledger; multi-item credit entries (name/qty/unit/price with manual line override); per-credit outstanding via FIFO payment allocation; due dates; advance (negative balance) support; CSV ledger export |
| Loans (ঋণ) | Institution, principal, flat annual interest in % (stored as basis points), processing fee, auto total-payable suggestion, weekly/monthly schedule whose parts sum exactly to the total, installment timeline (PAID / PARTIAL / DUE_TODAY / OVERDUE), payment history with edit & delete |
| EMI (কিস্তির পণ্য) | Product + seller, price/down payment, financed-amount schedule, the same timeline & payment tools as loans |
| Personal debt (ব্যক্তিগত ধার) | People directory (relationship, phone), borrow/repay entries per person with due dates, repayments allocated FIFO oldest-debt-first |
| Income & expense | Category chips + custom categories, search, today/week/month/year/all ranges, sort by date or amount, running totals |
| Dashboard | Today snapshot (income/expense/repaid/due), outstanding by book, 6-month trend, upcoming payments, auto-generated insights |
| Reports | Date-range presets, grouped bar trend, debt-distribution donut, expense category bars, upcoming dues, transaction list, **CSV + on-device PDF export** |
| Global search | Shops, people, loans, EMIs, debts and transactions from one box |
| Reminders | Daily local notification (WorkManager, self-chaining) at a configurable hour — only when something is due or overdue; silent when everything is paid |
| App lock | 4–8 digit PIN (PBKDF2, salted), optional biometric unlock, auto-relock after background, screenshot blocking (FLAG_SECURE) |
| Backup / restore | Versioned JSON backup (validated + transactional restore, all-or-nothing), via the Storage Access Framework — no cloud, ever |

## Money-integrity rules the app enforces

1. **All money is `Long` paisa.** No floating-point arithmetic ever touches a stored amount
   (quantity × price line totals are computed with `BigDecimal` + `HALF_UP`, then converted exactly).
2. **Overpayment is never silent.** Paying more than the live balance is rejected with the exact
   excess shown; if you explicitly confirm, the surplus is recorded as *advance* credit — balances
   always add up.
3. **Payment edits are transactional.** Edit = delete + re-record inside one Room transaction with
   the overpayment recheck performed against the *other* payments first, so a failed edit cannot
   lose money.
4. **Schedules sum exactly to the target total** — the last installment absorbs any remainder.

## Install

1. Open **Releases**: <https://github.com/skcpmx-lang/khatiyan/releases>
2. Download `khatiyan-release-v1.0.0.apk`.
3. Install on Android 8.0+ (API 26). On Android 13+, allow the notification prompt if you turn on
   daily reminders. The APK from CI is debug-signed until production keys are configured
   (see RELEASE.md), so update paths may require an uninstall between signing changes.

## Build from source

Requirements: JDK 17 (and the Android SDK 35 platform, e.g. via Android Studio — CI provides it).

```bash
./gradlew testDebugUnitTest      # unit + Robolectric repository tests
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK — see RELEASE.md for signing policy
```

GitHub Actions runs the same test + build pipeline on every push to `main` and on pull requests
(`.github/workflows/ci.yml`); pushing a `v*` tag publishes a GitHub Release with both APKs and
SHA-256 checksums (`.github/workflows/release.yml`).

## Repository layout

```
app/src/main/java/com/shohan/khatiyan/
  core/            Money (paisa), Bengali date formatting helpers
  domain/          enums, finance math (schedule generator, flat interest,
                   FIFO allocation, overpayment policy, due engine, insights),
                   pure models
  data/            Room entities/DAOs/database, central ledger view,
                   repositories, DataStore settings, JSON backup, CSV/PDF export
  notification/    daily reminder worker + scheduler (local notifications only)
  security/        PIN hashing/verification, lock-state manager
  di/              AppContainer — manual DI, no framework
  presentation/    Compose screens: dashboard, hisab hub, shop, loan, EMI,
                   personal, cashflow (income/expense/ledger), reports, search,
                   settings, onboarding, lock + shared payment/quick-add flows
  ui/              light-only Bengali theme, shared components, hand-drawn
                   Canvas charts (no chart library)
  utilities/       Bengali digit conversion, DataBus (cross-screen refresh),
                   validation exceptions, ViewModel factory
app/src/test/      JVM unit tests + Robolectric Room repository tests
.github/workflows/ ci.yml (test + build), release.yml (tag → GitHub Release)
```

Docs: [ARCHITECTURE.md](ARCHITECTURE.md) · [RELEASE.md](RELEASE.md)

## Credits

Built by **Shohan Khan** — helloiamshohan@gmail.com.
Name "খতিয়ান" refers to the traditional village land ledger — a book that, like this app,
belongs to the household and never leaves it.

## License

MIT — see [LICENSE](LICENSE). Free to use, study, modify and redistribute.

## Version history

| Version | What changed |
|---|---|
| **1.0.1** | App-wide UI/UX refinement pass and full natural-Bengali copy rewrite (no logic changes): 6-tile quick actions grid, unified 20dp content gutters and spacing rhythm, safe-area handling for onboarding/lock, refined hero card with a calm zero state, report charts that hide themselves when there is no data, adaptive search placeholders, consistent icon family, and calmer destructive/restore wording. Module names finalized: হিসাব → দোকান / লোন / EMI / ধার. |
| 1.0.0 | First release — full offline ledger (shop credit, loans, EMI, personal debt, hisab), dashboard, reports, backup/restore, reminders, app lock. |
