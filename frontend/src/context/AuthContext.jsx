import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { authApi, tokenStorage } from "../services/api.js";

const AuthContext = createContext(null);

const USER_KEY = "stock-tracker.email";

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => tokenStorage.get());
  const [email, setEmail] = useState(() => localStorage.getItem(USER_KEY));

  useEffect(() => {
    if (email) localStorage.setItem(USER_KEY, email);
    else localStorage.removeItem(USER_KEY);
  }, [email]);

  const apply = (auth) => {
    tokenStorage.set(auth.token);
    setToken(auth.token);
    setEmail(auth.email);
  };

  const login = async (creds) => {
    const auth = await authApi.login(creds);
    apply(auth);
  };

  const register = async (payload) => {
    const auth = await authApi.register(payload);
    apply(auth);
  };

  const logout = () => {
    tokenStorage.clear();
    setToken(null);
    setEmail(null);
  };

  const value = useMemo(
    () => ({ token, email, isAuthenticated: !!token, login, register, logout }),
    [token, email]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
};
