import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  authApi,
  isTokenExpired,
  sessionExpiredFlag,
  timeUntilExpiry,
  tokenStorage,
  userApi,
} from "../services/api.js";

const AuthContext = createContext(null);

const USER_KEY = "stock-tracker.email";
const DISPLAY_NAME_KEY = "stock-tracker.displayName";

/** Read a token from storage, but treat an expired one as if there was none. */
function readValidToken() {
  const token = tokenStorage.get();
  if (!token) return null;
  if (isTokenExpired(token)) {
    tokenStorage.clear();
    return null;
  }
  return token;
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(readValidToken);
  const [email, setEmail] = useState(() =>
    readValidToken() ? localStorage.getItem(USER_KEY) : null
  );
  const [displayName, setDisplayName] = useState(() =>
    readValidToken() ? localStorage.getItem(DISPLAY_NAME_KEY) || null : null
  );
  const expiryTimerRef = useRef(null);

  useEffect(() => {
    if (email) localStorage.setItem(USER_KEY, email);
    else localStorage.removeItem(USER_KEY);
  }, [email]);

  useEffect(() => {
    if (displayName) localStorage.setItem(DISPLAY_NAME_KEY, displayName);
    else localStorage.removeItem(DISPLAY_NAME_KEY);
  }, [displayName]);

  const logout = useCallback(() => {
    tokenStorage.clear();
    setToken(null);
    setEmail(null);
    setDisplayName(null);
  }, []);

  // Whenever the token changes, schedule a forced logout exactly when it
  // expires so the user is bounced to /login even if they're idling on a
  // page that isn't making requests.
  useEffect(() => {
    if (expiryTimerRef.current) {
      clearTimeout(expiryTimerRef.current);
      expiryTimerRef.current = null;
    }
    if (!token) return;

    const ms = timeUntilExpiry(token);
    if (ms <= 0) {
      sessionExpiredFlag.set();
      logout();
      return;
    }
    expiryTimerRef.current = setTimeout(() => {
      sessionExpiredFlag.set();
      logout();
    }, ms);

    return () => {
      if (expiryTimerRef.current) clearTimeout(expiryTimerRef.current);
    };
  }, [token, logout]);

  const apply = (auth) => {
    tokenStorage.set(auth.token);
    setToken(auth.token);
    setEmail(auth.email);
    setDisplayName(auth.displayName || null);
  };

  const login = async (creds) => {
    const auth = await authApi.login(creds);
    apply(auth);
  };

  const register = async (payload) => {
    const auth = await authApi.register(payload);
    apply(auth);
  };

  /**
   * Update the user's display name on the server and reflect the new value
   * locally. Pass an empty string to clear it (UI will fall back to the
   * email's local-part).
   */
  const updateDisplayName = useCallback(async (nextDisplayName) => {
    const profile = await userApi.updateProfile({
      displayName: nextDisplayName ?? "",
    });
    setDisplayName(profile.displayName || null);
    return profile;
  }, []);

  const value = useMemo(
    () => ({
      token,
      email,
      displayName,
      isAuthenticated: !!token,
      login,
      register,
      logout,
      updateDisplayName,
    }),
    [token, email, displayName, logout, updateDisplayName]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
};
