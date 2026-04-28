import { useCallback, useEffect, useState } from "react";
import { parseError, stocksApi } from "../services/api.js";

export default function Dashboard() {
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

  return (
    <div className="app-shell">
      <div className="dashboard">
        <section className="card">
          <h3>Track a new stock</h3>
          {error && <div className="error-banner">{error}</div>}
          <form className="add-form" onSubmit={onAdd}>
            <div className="form-group">
              <label htmlFor="symbol">Symbol</label>
              <input
                id="symbol"
                name="symbol"
                placeholder="AAPL"
                value={form.symbol}
                onChange={onChange}
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="dropThresholdPercentage">Drop threshold (%)</label>
              <input
                id="dropThresholdPercentage"
                name="dropThresholdPercentage"
                type="number"
                step="0.01"
                min="0.01"
                max="100"
                placeholder="5"
                value={form.dropThresholdPercentage}
                onChange={onChange}
                required
              />
            </div>
            <button className="primary" type="submit" disabled={submitting}>
              {submitting ? "Adding..." : "Add"}
            </button>
          </form>
        </section>

        <section className="card">
          <h3>Your tracked stocks</h3>
          {loading ? (
            <div className="empty">Loading…</div>
          ) : stocks.length === 0 ? (
            <div className="empty">No stocks tracked yet — add one above.</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Threshold</th>
                  <th>Highest seen</th>
                  <th>Last alert</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {stocks.map((s) => (
                  <tr key={s.id}>
                    <td><strong>{s.symbol}</strong></td>
                    <td>{Number(s.dropThresholdPercentage).toFixed(2)}%</td>
                    <td>{s.highestPriceSeen ? `$${Number(s.highestPriceSeen).toFixed(2)}` : "—"}</td>
                    <td>{s.lastNotifiedAt ? new Date(s.lastNotifiedAt).toLocaleString() : "Never"}</td>
                    <td>
                      <button className="danger" onClick={() => onDelete(s.id)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </div>
  );
}
