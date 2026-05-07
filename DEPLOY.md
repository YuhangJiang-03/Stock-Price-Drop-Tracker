# Self-hosting on your Windows PC

Goal: visit `https://something.example.com` from any device on the internet
and see your Stock Tracker running on this PC, with the React UI and the
Spring Boot API on a single port behind HTTPS, restarting automatically if
your PC reboots or the process crashes.

The plan is:


| #   | Concern                             | Tool                  |
| --- | ----------------------------------- | --------------------- |
| 1   | Bundle React + Spring Boot in 1 jar | Maven (already wired) |
| 2   | Public URL + HTTPS                  | Cloudflare Tunnel     |
| 3   | Auto-start on boot, survive crashes | NSSM                  |


No router config, no port forwarding, no exposed home IP, no Let's Encrypt
plumbing — Cloudflare handles all of it because the tunnel makes an
**outbound** connection from your PC to Cloudflare's edge.

---

## 0. Prerequisites

You should already have these from local dev:

- **Java 17+** (`java -version`)
- **Node 18+** + npm (`node -v`)
- **Maven** (`mvn -v`)
- **PostgreSQL** running locally with the `stock_tracker` database

You'll add two more during this guide:

- **cloudflared** — Cloudflare's tunnel daemon
- **nssm** — Non-Sucking Service Manager (turns any .exe into a Windows service)

---

## 1. Set the secrets in `.env`

`application.yml` no longer ships with default values for `DB_PASSWORD` or
`JWT_SECRET` — it reads them from the environment. Both `run-prod.ps1` and
`install-service.ps1` populate that environment from a local `.env` file
at the repo root, so the only thing you need to maintain is one file.

If you don't have a `.env` yet, copy the template and fill it in:

```powershell
Copy-Item .env.example .env
notepad .env
```

The two required keys:

```ini
DB_PASSWORD='<your Postgres password>'
JWT_SECRET='<random base64 blob, 32+ bytes>'
```

To generate a fresh JWT secret on demand:

```powershell
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 48
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

If you also need to rotate Postgres itself (e.g. the original password is
still in your git history and you want a clean break), pick a new password,
update `.env`, then update the database to match:

```powershell
psql -U postgres -d stock_tracker
# Enter the OLD password at the prompt
```

```sql
ALTER USER postgres WITH PASSWORD '<paste the new one from .env>';
\q
```

> `.env` is gitignored. Never commit it. If you ever paste it into a chat,
> share it with another machine, or suspect it leaked, regenerate
> `JWT_SECRET` and rotate `DB_PASSWORD` (and re-run the `ALTER USER`).

---

## 2. Build the production artifact

From the repo root:

```powershell
.\build.ps1
```

What this does:

1. `npm ci && npm run build` in `frontend/` → emits `frontend/dist/`.
2. `mvn clean package -DskipTests` in `backend/` → produces a fat jar at
  `backend\target\stock-tracker-backend-0.0.1-SNAPSHOT.jar`. Maven's
   resources plugin copies `frontend/dist/**` into the jar's
   `BOOT-INF/classes/static/` so the SPA is part of the deployable.

The build is fully self-contained from this point on — only the jar is needed
to run the app.

---

## 3. Smoke-test the production jar locally

```powershell
.\run-prod.ps1
```

You should see Spring Boot's banner and `Tomcat started on port 8080`. Then:

1. Open `http://localhost:8080` in a browser → React app renders.
2. Open `http://localhost:8080/api/auth/login` with an empty POST → 400 (it
  expects a body) — confirms the API is reachable on the same port.
3. A hard refresh on `http://localhost:8080/profile` should also load the
  app (the SPA fallback in `WebConfig` rewrites unknown paths to
   `index.html`).

Stop with `Ctrl+C` once it's working.

---

## 4. Install Cloudflare Tunnel (`cloudflared`)

Easiest install via winget (built into modern Windows):

```powershell
winget install --id Cloudflare.cloudflared
```

Verify:

```powershell
cloudflared --version
```

### 4a. Quick test — random `*.trycloudflare.com` URL

This is the fastest way to confirm the public path works. It gives you an
ephemeral URL that lasts as long as the command runs:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Cloudflared prints a URL like
`https://shy-grass-1234.trycloudflare.com`. Open it on your phone — you
should see the dashboard.

Caveats: the URL changes every run, has no auth on the cloudflared side,
and should not be your long-term answer. Move on to 4b once it works.

### 4b. Permanent named tunnel (recommended)

You need a domain on Cloudflare DNS. Cheapest path: buy one on
[Cloudflare Registrar](https://www.cloudflare.com/products/registrar/) (~$10/yr,
no markup) and let the setup walk you through pointing nameservers at Cloudflare.

Once your domain is on Cloudflare:

```powershell
# 1. Authenticate cloudflared with your Cloudflare account (opens a browser).
cloudflared tunnel login

# 2. Create the tunnel.
cloudflared tunnel create stock-tracker

# This prints a tunnel UUID and writes credentials to:
#   %USERPROFILE%\.cloudflared\<UUID>.json
```

Now create a config file at `%USERPROFILE%\.cloudflared\config.yml`:

```yaml
tunnel: <UUID-from-the-command-above>
credentials-file: C:\Users\<you>\.cloudflared\<UUID>.json

ingress:
  - hostname: stocks.yourdomain.com
    service: http://localhost:8080
  - service: http_status:404
```

Wire the hostname to the tunnel:

```powershell
cloudflared tunnel route dns stock-tracker stocks.yourdomain.com
```

Test it manually:

```powershell
cloudflared tunnel run stock-tracker
```

Open `https://stocks.yourdomain.com` — should be your app, with HTTPS, from
anywhere in the world.

---

## 5. Make it auto-start: the Spring Boot jar as a Windows service

Install NSSM:

```powershell
winget install --id NSSM.NSSM
```

Open a new **Administrator** PowerShell, then from the repo root:

```powershell
.\install-service.ps1
```

That script (idempotent — re-run it any time):

- locates the latest jar in `backend\target\`
- reads `.env` and writes `DB_PASSWORD` / `JWT_SECRET` (and any other keys
  in the file) into the service's environment block, so the daemon never
  inherits secrets from the calling user's profile
- registers the service as `StockTracker`, set to auto-start at boot
- redirects stdout/stderr to `C:\ProgramData\StockTracker\*.log`
  (rotated at 10 MB)
- starts it and prints the resulting status

Sanity check:

```powershell
Get-Service StockTracker
Invoke-WebRequest http://localhost:8080 -UseBasicParsing | Select-Object StatusCode
```

Day-to-day commands you'll use:

```powershell
nssm restart StockTracker     # after a rebuild
nssm stop    StockTracker
nssm remove  StockTracker confirm   # full uninstall
```

Need a different name or port? `.\install-service.ps1 -ServiceName MyStocks -Port 9000`.

---

## 6. Make Cloudflare Tunnel auto-start too

The tunnel daemon ships with first-class Windows service support — no NSSM
needed for this one:

```powershell
# Run as Administrator
cloudflared service install
```

That installs `cloudflared` as a service named `Cloudflared` that reads
`%USERPROFILE%\.cloudflared\config.yml` on boot. (If the installer asks
for a token, paste the one from the dashboard's tunnel page — but the
named-tunnel flow in step 4b doesn't need it.)

Verify:

```powershell
Get-Service Cloudflared
```

Both services now boot with Windows. After a reboot, `https://stocks.yourdomain.com`
should keep responding without any login.

---

## 7. Day-2 things worth knowing

**Updating to a new version.** Pull / edit / commit, then:

```powershell
nssm stop StockTracker
.\build.ps1
nssm start StockTracker
```

Cloudflare Tunnel doesn't need restarting — it just keeps proxying.

**Sleep settings.** A laptop on a desk will go to sleep and the site will
go down. Either plug it in and configure power settings to "never sleep
when plugged in", or run on a desktop / always-on machine:

```powershell
powercfg /change standby-timeout-ac 0
powercfg /change hibernate-timeout-ac 0
```

**Database backups.** You're hosting real user data now. Schedule a daily
`pg_dump` to a folder OneDrive syncs:

```powershell
# Save in C:\Users\<you>\stock-tracker-backups\, runs daily at 03:00
$cmd = 'pg_dump -U postgres -d stock_tracker -f "C:\Users\yuhan\stock-tracker-backups\stock_tracker_$(Get-Date -Format yyyyMMdd).sql"'
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-Command $cmd"
$trigger = New-ScheduledTaskTrigger -Daily -At 3am
Register-ScheduledTask -TaskName "StockTrackerBackup" -Action $action -Trigger $trigger -RunLevel Highest
```

(`pg_dump` will prompt for the DB password the first time; consider a
`[.pgpass](https://www.postgresql.org/docs/current/libpq-pgpass.html)` file
so the scheduled task can run unattended.)

**Updating the JDK / Maven.** No effect on the running service — it uses
the `java.exe` baked into the NSSM config until you reinstall and rerun
`nssm install`.

**CORS for your public hostname.** `SecurityConfig.corsConfigurationSource`
reads its allow-list from `app.cors.allowed-origin-patterns`
(env var: `APP_CORS_ORIGINS`). Defaults already cover
`http://localhost:3000/5173` and any `*.trycloudflare.com` quick-tunnel
URL. Even though the SPA is bundled in the same jar, Vite emits
`<script type="module">` tags which the browser fetches in CORS mode —
so the page's own origin still has to be on the list. Add your
permanent domain in `.env`:

```ini
APP_CORS_ORIGINS=http://localhost:5173,https://stocks.example.com
```

(comma-separated, no spaces). Restart the service after editing.

**Cost.** Domain ~$10/yr, Cloudflare Tunnel free, NSSM free, electricity
is on you. That's it.