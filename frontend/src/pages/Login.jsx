import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { parseError, sessionExpiredFlag } from "../services/api.js";
import AuthHero from "../components/AuthHero.jsx";

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = location.state?.from?.pathname || "/";

  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState(null);
  const [info, setInfo] = useState(null);
  const [loading, setLoading] = useState(false);

  // If we got bounced here because a JWT expired (or the server rejected one
  // we still had in storage), let the user know rather than silently dropping
  // them on a blank login form.
  useEffect(() => {
    if (sessionExpiredFlag.consume()) {
      setInfo("Your session expired — please sign in again.");
    }
  }, []);

  const onChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const onSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(form);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setError(parseError(err, "Could not sign in"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <AuthHero kicker="Welcome back" />
      <div className="auth-card">
        <h2>Sign in</h2>
        <p className="subtitle">Pick up where you left off.</p>

        {info && !error && <div className="info-banner">{info}</div>}
        {error && <div className="error-banner">{error}</div>}

        <form onSubmit={onSubmit} noValidate>
          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={form.email}
              onChange={onChange}
              required
            />
          </div>
          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              placeholder="••••••••"
              value={form.password}
              onChange={onChange}
              required
            />
          </div>
          <button className="primary" type="submit" disabled={loading}>
            {loading ? <><span className="spinner" />Signing in…</> : "Sign in"}
          </button>
        </form>

        <div className="switch">
          New here? <Link to="/register">Create an account</Link>
        </div>
      </div>
    </div>
  );
}
