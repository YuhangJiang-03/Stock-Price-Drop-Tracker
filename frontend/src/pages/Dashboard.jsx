import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { parseError, stocksApi } from "../services/api.js";
import { useAuth } from "../context/AuthContext.jsx";
import TrendingNews from "../components/TrendingNews.jsx";

function SymbolPill({ symbol, to }) {
  const initials = symbol.slice(0, 2);
  const inner = (
    <>
      <span className="icon">{initials}</span>
      {symbol}
    </>
  );
  if (to) {
    return (
      <Link to={to} className="symbol-pill linkable">
        {inner}
      </Link>
    );
  }
  return <span className="symbol-pill">{inner}</span>;
}

function ThresholdChip({ value, direction }) {
  if (value == null || value === "") return <span className="muted">—</span>;
  const isRise = direction === "rise";
  return (
    <span className={`threshold-chip ${isRise ? "rise" : "drop"}`}>
      {isRise ? "▲" : "▼"} {Number(value).toFixed(2)}%
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
      <div className="empty-sub">Add a ticker above to start watching for price moves.</div>
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

function lastAlertLabel(stock) {
  const drop = stock.lastDropAlertAt ? new Date(stock.lastDropAlertAt) : null;
  const rise = stock.lastRiseAlertAt ? new Date(stock.lastRiseAlertAt) : null;
  if (!drop && !rise) return null;
  // Pick whichever is most recent so we always show the freshest signal.
  if (drop && (!rise || drop >= rise)) {
    return { kind: "drop", at: drop };
  }
  return { kind: "rise", at: rise };
}

export default function Dashboard() {
  const { email, displayName } = useAuth();
  const navigate = useNavigate();
  const [stocks, setStocks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [form, setForm] = useState({
    symbol: "",
    dropThresholdPercentage: "",
    riseThresholdPercentage: "",
  });
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
      // Only include the rise threshold if the user actually filled it in;
      // omitting the field tells the backend "don't track rises for this one".
      const rise = form.riseThresholdPercentage.trim();
      if (rise !== "") {
        payload.riseThresholdPercentage = parseFloat(rise);
      }
      await stocksApi.add(payload);
      setForm({ symbol: "", dropThresholdPercentage: "", riseThresholdPercentage: "" });
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
    const alerted = stocks.filter((s) => s.lastDropAlertAt || s.lastRiseAlertAt).length;
    const tracksRise = stocks.filter((s) => s.riseThresholdPercentage != null).length;
    return { total, alerted, tracksRise };
  }, [stocks]);

  // Prefer the user's chosen display name; fall back to the email-local-part
  // so the greeting still feels personal even before they pick one.
  const greetingName = displayName || email?.split("@")[0] || "there";

  return (
    <div className="app-shell">
      <div className="dashboard">
        <TrendingNews limit={3} />

        <header className="page-header">
          <h2>Welcome back, {greetingName}</h2>
          <p>Your watchlist is being polled every 5 minutes for price moves in either direction.</p>
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
            <div className="hint">Across drops and rises combined</div>
          </div>
          <div className="stat-tile">
            <div className="label">Watching for rises</div>
            <div className="value">{stats.tracksRise}</div>
            <div className="hint">Stocks with a rise threshold set</div>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <h3>Track a new stock</h3>
              <div className="card-subtitle">
                Get notified the moment it moves past either threshold. Rise tracking is optional.
              </div>
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
            <div className="form-group">
              <label htmlFor="riseThresholdPercentage">Rise threshold (optional)</label>
              <input
                id="riseThresholdPercentage"
                name="riseThresholdPercentage"
                type="number"
                step="0.01"
                min="0.01"
                max="1000"
                placeholder="e.g. 5"
                value={form.riseThresholdPercentage}
                onChange={onChange}
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
                    <th>Drop</th>
                    <th>Rise</th>
                    <th className="numeric">Highest seen</th>
                    <th className="numeric">Lowest seen</th>
                    <th>Last alert</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {stocks.map((s) => {
                    const lastAlert = lastAlertLabel(s);
                    // Clicking anywhere on the row drills in, except for the
                    // Remove button (which has its own handler + stopPropagation).
                    const onRowClick = (e) => {
                      if (e.target.closest("button")) return;
                      navigate(`/stocks/${s.id}`);
                    };
                    return (
                      <tr key={s.id} className="row-clickable" onClick={onRowClick}>
                        <td><SymbolPill symbol={s.symbol} to={`/stocks/${s.id}`} /></td>
                        <td><ThresholdChip value={s.dropThresholdPercentage} direction="drop" /></td>
                        <td><ThresholdChip value={s.riseThresholdPercentage} direction="rise" /></td>
                        <td className="numeric">
                          {s.highestPriceSeen
                            ? `$${Number(s.highestPriceSeen).toFixed(2)}`
                            : <span className="muted">—</span>}
                        </td>
                        <td className="numeric">
                          {s.lowestPriceSeen
                            ? `$${Number(s.lowestPriceSeen).toFixed(2)}`
                            : <span className="muted">—</span>}
                        </td>
                        <td>
                          {lastAlert ? (
                            <span className={`alert-tag ${lastAlert.kind}`}>
                              {lastAlert.kind === "drop" ? "▼ drop" : "▲ rise"}
                              <span className="alert-time">{lastAlert.at.toLocaleString()}</span>
                            </span>
                          ) : <span className="muted">Never</span>}
                        </td>
                        <td className="actions">
                          <button
                            className="danger"
                            onClick={(e) => { e.stopPropagation(); onDelete(s.id); }}
                          >
                            Remove
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
