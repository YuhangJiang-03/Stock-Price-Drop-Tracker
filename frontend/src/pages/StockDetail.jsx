import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { parseError, stocksApi } from "../services/api.js";
import PriceChart from "../components/PriceChart.jsx";

const INTERVALS = [
  { id: "DAY", label: "1D" },
  { id: "WEEK", label: "1W" },
  { id: "MONTH", label: "1M" },
  { id: "YEAR", label: "1Y" },
];

export default function StockDetail() {
  const { id } = useParams();
  // Local state name avoids shadowing the global `setInterval` in case we
  // ever add timers to this component.
  const [activeInterval, setActiveInterval] = useState("DAY");
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(
    async (which) => {
      setLoading(true);
      setError(null);
      try {
        const data = await stocksApi.history(id, which);
        setHistory(data);
      } catch (err) {
        setError(parseError(err, "Could not load price history"));
      } finally {
        setLoading(false);
      }
    },
    [id]
  );

  useEffect(() => {
    load(activeInterval);
  }, [load, activeInterval]);

  const symbol = history?.symbol ?? "…";

  return (
    <div className="app-shell">
      <div className="detail">
        <Link to="/" className="back-link">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
            <path d="M15 6 L9 12 L15 18" />
          </svg>
          Back to watchlist
        </Link>

        <header className="page-header">
          <h2>
            <span className="symbol-pill big">
              <span className="icon">{symbol.slice(0, 2)}</span>
              {symbol}
            </span>
          </h2>
          <p>Historical price for the selected window. Hover the chart for exact values.</p>
        </header>

        <section className="card">
          <div className="card-header">
            <div>
              <h3>Price history</h3>
              <div className="card-subtitle">
                Mock provider — series is regenerated deterministically per symbol.
              </div>
            </div>
            <div className="interval-toggle" role="tablist" aria-label="Time interval">
              {INTERVALS.map((it) => (
                <button
                  key={it.id}
                  role="tab"
                  aria-selected={activeInterval === it.id}
                  className={activeInterval === it.id ? "active" : ""}
                  onClick={() => setActiveInterval(it.id)}
                  disabled={loading && activeInterval === it.id}
                >
                  {it.label}
                </button>
              ))}
            </div>
          </div>

          {error && <div className="error-banner">{error}</div>}

          <PriceChart
            points={history?.points}
            interval={history?.interval ?? activeInterval}
            loading={loading}
          />
        </section>
      </div>
    </div>
  );
}
