import axios from "axios";

// All requests go through Vite's "/api" proxy (see vite.config.js), which
// forwards to the Spring Boot backend on :8080.
const api = axios.create({
  baseURL: "/api",
  headers: { "Content-Type": "application/json" },
});

const TOKEN_KEY = "stock-tracker.token";

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

// Attach the JWT (when present) to every outgoing request.
api.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Boot the user back to /login if the server says we're unauthenticated.
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      tokenStorage.clear();
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
