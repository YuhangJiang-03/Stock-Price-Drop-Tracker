#!/usr/bin/env bash
#
# install.sh — install everything needed to build & run Stock Price Tracker.
#
# Stack:
#   - Backend:  Java 17, Maven 3.9+, PostgreSQL 14+
#   - Frontend: Node.js 18+, npm
#
# Supported hosts:
#   - Debian / Ubuntu (apt)
#   - Fedora / RHEL / CentOS Stream (dnf)
#   - Arch / Manjaro (pacman)
#   - macOS (Homebrew)
#
# Usage:
#   chmod +x install.sh
#   ./install.sh                  # install system pkgs + project deps
#   ./install.sh --skip-system    # only fetch project dependencies
#   ./install.sh --skip-deps      # only install system packages
#   ./install.sh --with-db        # also create the stock_tracker database
#
set -euo pipefail

# ---------- pretty logging ----------------------------------------------------
log()   { printf '\033[1;34m[install]\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[warn]\033[0m %s\n'    "$*" >&2; }
err()   { printf '\033[1;31m[error]\033[0m %s\n'   "$*" >&2; }
fatal() { err "$*"; exit 1; }

# ---------- argument parsing --------------------------------------------------
SKIP_SYSTEM=0
SKIP_DEPS=0
WITH_DB=0
for arg in "$@"; do
    case "$arg" in
        --skip-system) SKIP_SYSTEM=1 ;;
        --skip-deps)   SKIP_DEPS=1 ;;
        --with-db)     WITH_DB=1 ;;
        -h|--help)
            sed -n '2,18p' "$0"
            exit 0
            ;;
        *) fatal "unknown argument: $arg (try --help)" ;;
    esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# ---------- OS / package-manager detection -----------------------------------
detect_pm() {
    if [[ "$(uname -s)" == "Darwin" ]]; then
        echo "brew"; return
    fi
    if [[ -r /etc/os-release ]]; then
        # shellcheck disable=SC1091
        . /etc/os-release
        case "${ID:-}${ID_LIKE:-}" in
            *debian*|*ubuntu*) echo "apt"; return ;;
            *fedora*|*rhel*|*centos*) echo "dnf"; return ;;
            *arch*|*manjaro*) echo "pacman"; return ;;
        esac
    fi
    fatal "unsupported OS — install Java 17, Maven, Node 18+ and PostgreSQL manually, then re-run with --skip-system"
}

PM="$(detect_pm)"
log "detected package manager: $PM"

SUDO=""
if [[ "$PM" != "brew" && "$EUID" -ne 0 ]]; then
    command -v sudo >/dev/null 2>&1 || fatal "sudo is required for $PM (or run this script as root)"
    SUDO="sudo"
fi

# ---------- system package install -------------------------------------------
install_system() {
    log "installing system packages (Java 17, Maven, Node 18+, PostgreSQL)"
    case "$PM" in
        apt)
            $SUDO apt-get update
            $SUDO apt-get install -y --no-install-recommends \
                ca-certificates curl gnupg \
                openjdk-17-jdk \
                maven \
                postgresql postgresql-contrib

            # Node 18 LTS via NodeSource — Debian/Ubuntu repos lag badly.
            if ! command -v node >/dev/null 2>&1 || \
               [[ "$(node -v 2>/dev/null | sed 's/^v//;s/\..*//')" -lt 18 ]]; then
                log "installing Node.js 20 LTS via NodeSource"
                curl -fsSL https://deb.nodesource.com/setup_20.x | $SUDO -E bash -
                $SUDO apt-get install -y nodejs
            fi
            ;;
        dnf)
            $SUDO dnf install -y \
                java-17-openjdk-devel \
                maven \
                nodejs npm \
                postgresql postgresql-server postgresql-contrib
            # Initialise the cluster on first run (idempotent — exits 1 if already done)
            if [[ ! -d /var/lib/pgsql/data/base ]]; then
                $SUDO postgresql-setup --initdb || true
            fi
            $SUDO systemctl enable --now postgresql || true
            ;;
        pacman)
            $SUDO pacman -Sy --needed --noconfirm \
                jdk17-openjdk \
                maven \
                nodejs npm \
                postgresql
            if [[ ! -d /var/lib/postgres/data/base ]]; then
                $SUDO -iu postgres initdb -D /var/lib/postgres/data || true
            fi
            $SUDO systemctl enable --now postgresql || true
            ;;
        brew)
            command -v brew >/dev/null 2>&1 || \
                fatal "Homebrew not found — install from https://brew.sh and re-run"
            brew update
            brew install openjdk@17 maven node@20 postgresql@16
            # openjdk@17 is keg-only — symlink so `java` is on PATH.
            brew link --force --overwrite openjdk@17 || true
            brew link --force --overwrite node@20 || true
            brew services start postgresql@16 || true
            ;;
    esac

    # Start Postgres on systemd-based distros (apt path doesn't enable it above)
    if [[ "$PM" == "apt" ]] && command -v systemctl >/dev/null 2>&1; then
        $SUDO systemctl enable --now postgresql || true
    fi
}

# ---------- optional: bootstrap the application database ---------------------
create_database() {
    log "creating 'stock_tracker' database (if missing)"
    if ! command -v psql >/dev/null 2>&1; then
        warn "psql not on PATH — skipping DB bootstrap"
        return
    fi
    # Use the postgres superuser via peer auth on Linux, current user on macOS/brew.
    local PSQL=(psql)
    if [[ "$PM" != "brew" ]]; then
        if [[ -n "$SUDO" ]]; then
            PSQL=("$SUDO" -u postgres psql)
        elif command -v runuser >/dev/null 2>&1; then
            PSQL=(runuser -u postgres -- psql)
        else
            warn "no sudo / runuser available — skipping DB bootstrap"
            return
        fi
    fi
    if "${PSQL[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname='stock_tracker'" \
            | grep -q 1; then
        log "database 'stock_tracker' already exists"
    else
        "${PSQL[@]}" -c "CREATE DATABASE stock_tracker;"
        log "database 'stock_tracker' created"
    fi
    warn "default app config expects user=postgres password=postgres — override with DB_USERNAME / DB_PASSWORD env vars (see README)"
}

# ---------- project dependency install ---------------------------------------
install_project_deps() {
    log "resolving backend Maven dependencies"
    if [[ ! -d backend ]]; then
        fatal "backend/ directory not found — are you in the repo root?"
    fi
    (
        cd backend
        if [[ -x ./mvnw ]]; then
            ./mvnw -q -DskipTests dependency:go-offline
        else
            mvn -q -DskipTests dependency:go-offline
        fi
    )

    log "installing frontend npm dependencies"
    if [[ ! -d frontend ]]; then
        fatal "frontend/ directory not found — are you in the repo root?"
    fi
    (
        cd frontend
        if [[ -f package-lock.json ]]; then
            npm ci
        else
            npm install
        fi
    )
}

# ---------- run -------------------------------------------------------------
if [[ "$SKIP_SYSTEM" -eq 0 ]]; then
    install_system
else
    log "skipping system package install (--skip-system)"
fi

if [[ "$WITH_DB" -eq 1 ]]; then
    create_database
fi

if [[ "$SKIP_DEPS" -eq 0 ]]; then
    install_project_deps
else
    log "skipping project dependency install (--skip-deps)"
fi

log "done."
log ""
log "next steps:"
log "  1. cp .env.example .env  &&  edit DB_PASSWORD / JWT_SECRET"
log "  2. cd backend  &&  mvn spring-boot:run     # API on :8080"
log "  3. cd frontend &&  npm run dev             # UI on :3000"
