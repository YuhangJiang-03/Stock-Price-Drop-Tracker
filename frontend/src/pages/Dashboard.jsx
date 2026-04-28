import { useCallback, useEffect, useMemo, useState } from "react";
import { parseError, stocksApi } from "../services/api.js";
import { useAuth } from "../context/AuthContext.jsx";

function SymbolPill({ symbol }) {
  const initials = symbol.slice(0, 2);
  return (
    <span className="symbol-pill">
      <span className="icon">{initials}</span>
      {symbol}
    </span>
  );
}

function ThresholdChip({ value }) {
  return (
    <span className="threshold-chip">
      ▼ {Number(value).toFixed(2)}%
    </span>
  );
}

function EmptyState() {
  return (
    <div className="empty">
      <div className="empty-icon" aria-hidden="true">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M3 17 L9 11 L13 15 L21 6" />
          <path d="M14 6 L21 6 L21 13" />
        </svg>
      </div>
      <div className="empty-title">No stocks tracked yet</div>
      <div className="empty-sub">Add a ticker above to start watching for price drops.</div>
    </div>
  );
}

function LoadingRows() {
  return (
    <div className="loading-row">
      <div className="skeleton w-30" />
      <div className="skeleton w-70" />
      <div className="skeleton w-50" />
    </div>
  );
}

export default function Dashboard() {
  const { email } = useAuth();
  const [stocks, setStocks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [form, setForm] = useState({ symbol: "", dropThresholdPercentage: "" });
  const [submitting, setSubmitting] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setStocks(await stocksApi.list());
    } catch (err) {
      setError(parseError(err, "Could not load tracked stocks"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const onChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const onAdd = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const payload = {
        symbol: form.symbol.trim().toUpperCase(),
        dropThresholdPercentage: parseFloat(form.dropThresholdPercentage),
      };
      await stocksApi.add(payload);
      setForm({ symbol: "", dropThresholdPercentage: "" });
      await refresh();
    } catch (err) {
      setError(parseError(err, "Could not add stock"));
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (id) => {
    setError(null);
    try {
      await stocksApi.remove(id);
      setStocks((prev) => prev.filter((s) => s.id !== id));
    } catch (err) {
      setError(parseError(err, "Could not delete stock"));
    }
  };

  const stats = useMemo(() => {
    const total = stocks.length;
    const alerted = stocks.filter((s) => s.lastNotifiedAt).length;
    const avgThreshold =
      total === 0
        ? 0
        : stocks.reduce((sum, s) => sum + Number(s.dropThresholdPercentage || 0), 0) / total;
    return { total, alerted, avgThreshold };
  }, [stocks]);

  const firstName = email?.split("@")[0] ?? "there";

  return (
    <div className="app-shell">
      <div className="dashboard">
        <header className="page-header">
          <h2>Welcome back, {firstName}</h2>
          <p>Your watchlist is being polled every 5 minutes for price drops.</p>
        </header>

        <section className="stats">
          <div className="stat-tile">
            <div className="label">Tracked stocks</div>
            <div className="value">{stats.total}</div>
            <div className="hint">
              {stats.total === 0 ? "Nothing yet — add one below" : "Live monitoring active"}
            </div>
          </div>
          <div className="stat-tile">
            <div className="label">Alerts sent</div>
            <div className="value">{stats.alerted}</div>
            <div className="hint">Across all your tracked stocks</div>
          </div>
          <div className="stat-tile">
            <div className="label">Avg. threshold</div>
            <div className="value">{stats.avgThreshold.toFixed(1)}%</div>
            <div className="hint">Average drop you watch for</div>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <h3>Track a new stock</h3>
              <div className="card-subtitle">Get notified the moment it drops past your threshold.</div>
            </div>
          </div>

          {error && <div className="error-banner">{error}</div>}

          <form className="add-form" onSubmit={onAdd}>
            <div className="form-group">
              <label htmlFor="symbol">Symbol</label>
              <input
                id="symbol"
                name="symbol"
                placeholder="AAPL, TSLA, NVDA…"
                value={form.symbol}
                onChange={onChange}
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="dropThresholdPercentage">Drop threshold</label>
              <input
                id="dropThresholdPercentage"
                name="dropThresholdPercentage"
                type="number"
                step="0.01"
                min="0.01"
                max="100"
                placeholder="e.g. 5"
                value={form.dropThresholdPercentage}
                onChange={onChange}
                required
              />
            </div>
            <button className="primary" type="submit" disabled={submitting}>
              {submitting ? <><span className="spinner" />Adding…</> : "Add stock"}
            </button>
          </form>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <h3>Your watchlist</h3>
              <div className="card-subtitle">
                {stocks.length === 0
                  ? "No stocks yet"
                  : `${stocks.length} ${stocks.length === 1 ? "stock" : "stocks"} being tracked`}
              </div>
            </div>
          </div>

          {loading ? (
            <LoadingRows />
          ) : stocks.length === 0 ? (
            <EmptyState />
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Symbol</th>
                    <th>Threshold</th>
                    <th className="numeric">Highest seen</th>
                    <th>Last alert</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {stocks.map((s) => (
                    <tr key={s.id}>
                      <td><SymbolPill symbol={s.symbol} /></td>
                      <td><ThresholdChip value={s.dropThresholdPercentage} /></td>
                      <td className="numeric">
                        {s.highestPriceSeen
                          ? `$${Number(s.highestPriceSeen).toFixed(2)}`
                          : <span className="muted">—</span>}
                      </td>
                      <td>
                        {s.lastNotifiedAt
                          ? new Date(s.lastNotifiedAt).toLocaleString()
                          : <span className="muted">Never</span>}
                      </td>
                      <td className="actions">
                        <button className="danger" onClick={() => onDelete(s.id)}>
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
