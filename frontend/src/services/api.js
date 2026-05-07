import axios from "axios";

// All requests go through Vite's "/api" proxy (see vite.config.js), which
// forwards to the Spring Boot backend on :8080.
const api = axios.create({
  baseURL: "/api",
  headers: { "Content-Type": "application/json" },
});

const TOKEN_KEY = "stock-tracker.token";
const SESSION_EXPIRED_KEY = "stock-tracker.session-expired";

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

// One-shot flag the login page reads to show "your session expired".
export const sessionExpiredFlag = {
  set: () => sessionStorage.setItem(SESSION_EXPIRED_KEY, "1"),
  consume: () => {
    const v = sessionStorage.getItem(SESSION_EXPIRED_KEY);
    sessionStorage.removeItem(SESSION_EXPIRED_KEY);
    return v === "1";
  },
};

/**
 * Decode a JWT's payload without verifying the signature. Verification is the
 * server's job; we just need the `exp` claim to know whether to bother sending
 * the token at all.
 */
export function decodeJwt(token) {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

/** Returns true if the token is missing, malformed, or past its `exp`. */
export function isTokenExpired(token) {
  const claims = decodeJwt(token);
  if (!claims || typeof claims.exp !== "number") return true;
  // exp is in seconds; allow 5s clock skew so we don't bounce a token that
  // expires in flight.
  return claims.exp * 1000 <= Date.now() - 5000;
}

/** Milliseconds until the token expires, or 0 if already expired/invalid. */
export function timeUntilExpiry(token) {
  const claims = decodeJwt(token);
  if (!claims || typeof claims.exp !== "number") return 0;
  return Math.max(0, claims.exp * 1000 - Date.now());
}

// Attach the JWT (when present and still valid) to every outgoing request.
// Skip expired tokens entirely so the server doesn't even see them.
api.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token && !isTokenExpired(token)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Boot the user back to /login if the server says we're unauthenticated.
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      const hadToken = !!tokenStorage.get();
      tokenStorage.clear();
      if (hadToken) sessionExpiredFlag.set();
      if (window.location.pathname !== "/login") {
        window.location.assign("/login");
      }
    }
    return Promise.reject(err);
  }
);

// ---------- Auth endpoints ----------
export const authApi = {
  register: (payload) => api.post("/auth/register", payload).then((r) => r.data),
  login: (payload) => api.post("/auth/login", payload).then((r) => r.data),
};

// ---------- Stocks endpoints ----------
export const stocksApi = {
  list: () => api.get("/stocks").then((r) => r.data),
  add: (payload) => api.post("/stocks", payload).then((r) => r.data),
  remove: (id) => api.delete(`/stocks/${id}`).then((r) => r.data),
  history: (id, interval = "DAY") =>
    api.get(`/stocks/${id}/history`, { params: { interval } }).then((r) => r.data),
};

/** Pull a human-readable error string out of an axios error. */
export const parseError = (err, fallback = "Something went wrong") => {
  const data = err?.response?.data;
  if (!data) return fallback;
  if (typeof data === "string") return data;
  if (data.message) return data.message;
  if (data.fieldErrors) {
    return Object.entries(data.fieldErrors)
      .map(([field, msg]) => `${field}: ${msg}`)
      .join("; ");
  }
  return fallback;
};

export default api;
