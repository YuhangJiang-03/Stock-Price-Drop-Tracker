# Stock Price Tracker

A full-stack web app that lets users track stock symbols and get an SMS alert
when the price drops by more than a configured percentage from the highest
value observed since tracking began.

```
stock-price-tracker/
├── backend/      # Spring Boot 3 + PostgreSQL + JWT
└── frontend/     # React 18 + Vite + axios
```

## Tech stack

| Layer       | Choice                                                                |
| ----------- | --------------------------------------------------------------------- |
| Backend     | Java 17, Spring Boot 3, Spring Security, Spring Data JPA, Bean Valid. |
| Database    | PostgreSQL 14+                                                        |
| Auth        | JWT (HS256) via `jjwt`                                                |
| Scheduler   | Spring `@Scheduled` (cron: every 5 min)                               |
| Stock API   | `StockPriceService` interface + `MockStockPriceService` (random walk) |
| Notifications | `NotificationService` interface + `LoggingNotificationService` (default) and `EmailNotificationService` (Gmail SMTP, free) |
| Frontend    | React 18 (hooks), React Router 6, axios, Vite                         |

The notification channel is selected via `app.notification.channel`:
`log` (default — writes to stdout), `email` (uses Spring's `JavaMailSender` against
any SMTP server). A future SMS channel (Twilio) can be added by writing one
more `NotificationService` implementation.

## Architecture overview

```
React (Vite, :3000)
   │   axios → /api/*
   ▼
Spring Boot (:8080)
   ├── controller/    REST endpoints
   ├── service/       business logic + provider interfaces
   ├── repository/    Spring Data JPA
   ├── model/         JPA entities (User, TrackedStock)
   ├── scheduler/     @Scheduled price checker
   └── security/      JWT filter + Spring Security config
       ▼
PostgreSQL (:5432)
```

## REST API

All endpoints under `/stocks` require an `Authorization: Bearer <jwt>` header.

| Method | Path                | Body                                          | Description              |
| ------ | ------------------- | --------------------------------------------- | ------------------------ |
| POST   | `/auth/register`    | `{email, password, phoneNumber}`              | Create account, get JWT  |
| POST   | `/auth/login`       | `{email, password}`                           | Authenticate, get JWT    |
| POST   | `/stocks`           | `{symbol, dropThresholdPercentage}`           | Start tracking a stock   |
| GET    | `/stocks`           | —                                             | List your tracked stocks |
| DELETE | `/stocks/{id}`      | —                                             | Stop tracking a stock    |

---

## Backend — running locally

### 1. Prerequisites
- Java 17+
- Maven 3.9+ (or use the wrapper if you generate one)
- PostgreSQL running locally

### 2. Create the database
```sql
CREATE DATABASE stock_tracker;
-- the default credentials in application.yml are postgres/postgres
```
Override via env vars if needed:
```
DB_URL=jdbc:postgresql://localhost:5432/stock_tracker
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=<base64-encoded 256-bit secret>
SMS_PROVIDER=mock
```
Hibernate is configured with `ddl-auto: update`, so the schema is created on
first startup.

### 3. Run
```bash
cd backend
mvn spring-boot:run
```
Server listens on `http://localhost:8080`.

### 4. Smoke test with curl
```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"secret123","phoneNumber":"+14155552671"}'

# -> { "token": "...", "email": "me@example.com" }
TOKEN=...

# Track AAPL with a 5% drop threshold
curl -X POST http://localhost:8080/stocks \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"symbol":"AAPL","dropThresholdPercentage":5}'

# List
curl http://localhost:8080/stocks -H "Authorization: Bearer $TOKEN"
```

The scheduler logs `[MOCK SMS]` lines whenever an alert would be sent.

### 5. Tweaking schedules / cooldown
`backend/src/main/resources/application.yml`:
```yaml
app:
  scheduler:
    price-check-cron: "0 */5 * * * *"     # Spring cron: every 5 min
    notification-cooldown-minutes: 60     # Per-stock SMS cooldown
```

---

## Frontend — running locally

### 1. Prerequisites
- Node.js 18+ (LTS recommended)

### 2. Install & run
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:3000`.

The Vite dev server proxies `/api/*` to the Spring Boot backend on `:8080`,
so you don't have to deal with CORS while developing. (CORS is also enabled
on the backend for both `:3000` and `:5173`.)

### 3. Production build
```bash
cd frontend
npm run build      # outputs dist/
npm run preview    # serve the built bundle
```
For production deployment, serve `dist/` from any static host (Nginx, S3,
Cloudflare Pages…) and point its `/api` rewrite at the backend.

---

## How the alert system works

1. A user calls `POST /stocks` with a symbol + drop threshold.
2. Every 5 minutes (`StockPriceScheduler`):
   - All `TrackedStock` rows are loaded.
   - For each stock, `StockPriceService.getCurrentPrice(symbol)` is called.
   - `highestPriceSeen` is updated if the new price is higher.
   - Otherwise a percentage drop is computed:
     `(highest − current) / highest × 100`.
   - If the drop ≥ threshold **and** the per-stock cooldown has elapsed,
     `NotificationService.notify(user, subject, body)` is invoked and
     `lastNotifiedAt` is set.
3. The active `NotificationService` decides where the alert actually goes —
   the application log (default) or an email (Gmail SMTP).

## Notification channels

Switch channels with `app.notification.channel` (env var: `NOTIFICATION_CHANNEL`):

### `log` (default — zero setup)
`LoggingNotificationService` writes:
```
[NOTIFY:LOG] to=me@example.com | subject="Price alert: AAPL down 6.18%" | body="..."
```
Useful for development; nothing leaves the machine.

### `email` (free via Gmail SMTP)
`EmailNotificationService` uses Spring's `JavaMailSender`. Setup:

1. **Enable 2-Step Verification on your Google account** (required to mint an
   App Password). https://myaccount.google.com/security
2. **Create an App Password.** https://myaccount.google.com/apppasswords
   - App: "Mail", Device: "Other → Stock Tracker"
   - Google shows you a 16-character password — copy it (no spaces needed).
3. **Run the backend with these env vars:**
   ```powershell
   $env:NOTIFICATION_CHANNEL="email"
   $env:MAIL_USERNAME="your.address@gmail.com"
   $env:MAIL_PASSWORD="<the 16-char app password>"
   # Optional — defaults to MAIL_USERNAME
   $env:NOTIFICATION_EMAIL_FROM="your.address@gmail.com"
   mvn spring-boot:run
   ```
   On Linux/macOS use `export VAR=value` instead of `$env:VAR=...`.

That's it. Trigger an alert (the mock stock service will eventually produce a
big enough random drop on its own — usually within a few scheduler ticks) and
the email lands in the inbox of whatever address the user registered with.

**Notes on Gmail SMTP:**
- Free, no signup beyond an existing Gmail account.
- Limit: ~500 messages per day per account (massively more than personal alerts need).
- Emails are sent **from** your Gmail address **to** each registered user's email.
- Use any other SMTP server by overriding `MAIL_HOST` / `MAIL_PORT`
  (e.g. `smtp-relay.brevo.com:587`, `smtp.mailgun.org:587`, etc.).

### Adding more channels later
Implement `NotificationService` and gate the bean with `@ConditionalOnProperty`:
```java
@Service
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "sms")
class TwilioNotificationService implements NotificationService { ... }
```

## Plugging in a real stock-price provider
Implement `StockPriceService` with a Spring bean and remove
`MockStockPriceService` (or guard it with `@ConditionalOnProperty`):
```java
@Service
@ConditionalOnProperty(name = "app.stock.provider", havingValue = "alphaVantage")
class AlphaVantageStockPriceService implements StockPriceService { ... }
```

---

## Project layout

```
backend/src/main/java/com/stocktracker
├── StockTrackerApplication.java
├── controller/        AuthController, StockController
├── service/           AuthService, StockService, StockPriceService (+ Mock),
│                      NotificationService (+ Logging + Email impls)
├── repository/        UserRepository, TrackedStockRepository
├── model/             User, TrackedStock
├── dto/               RegisterRequest, LoginRequest, AuthResponse,
│                      TrackedStockRequest, TrackedStockResponse, ApiError
├── scheduler/         StockPriceScheduler
├── security/          SecurityConfig, JwtService, JwtAuthFilter, AppUserDetailsService
└── exception/         BadRequestException, NotFoundException, GlobalExceptionHandler

frontend/src
├── main.jsx           Entry point
├── App.jsx            Router + layout
├── styles.css
├── components/        Navbar, ProtectedRoute
├── pages/             Login, Register, Dashboard
├── context/           AuthContext (JWT in localStorage)
└── services/api.js    axios instance + auth & stocks API helpers
```

## Notes on production hardening (not in scope here)

- Replace `ddl-auto: update` with Flyway/Liquibase migrations.
- Set `JWT_SECRET` from a secret manager; rotate on schedule.
- Validate phone numbers with `libphonenumber`.
- Page the scheduler over large fleets, run it with a distributed lock
  (e.g. ShedLock) to keep it singleton across replicas.
- Rate-limit `/auth/login` and add brute-force protection.
- Add observability: Micrometer metrics + structured logs.
