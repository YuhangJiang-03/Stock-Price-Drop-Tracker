import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { parseError, usersApi } from "../services/api.js";

// Mirrors the backend's MIN_QUERY_LENGTH so we don't even fire a request the
// server is going to reject as "too short".
const MIN_QUERY = 2;
// Long enough that we don't burn a request per keystroke on a fast typist,
// short enough that the dropdown still feels live.
const DEBOUNCE_MS = 220;

function Avatar({ label }) {
  const initial = (label?.[0] || "?").toUpperCase();
  return <span className="search-avatar" aria-hidden="true">{initial}</span>;
}

/**
 * Combobox-style search: typing into the input fires a debounced request and
 * shows up to N matches in a dropdown. Clicking (or pressing Enter on) a hit
 * navigates to that user's public profile.
 *
 * Keyboard:
 *  - ArrowDown / ArrowUp  cycle through results
 *  - Enter                open the highlighted result
 *  - Escape               close the dropdown
 */
export default function UserSearch() {
  const navigate = useNavigate();
  const listboxId = useId();

  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [open, setOpen] = useState(false);
  const [activeIdx, setActiveIdx] = useState(-1);

  const wrapRef = useRef(null);
  const inputRef = useRef(null);
  // Bumped on every fetch; older responses arriving late are dropped so they
  // can't overwrite a fresher result set.
  const requestSeq = useRef(0);

  const trimmed = query.trim();
  const hasQuery = trimmed.length >= MIN_QUERY;

  // Debounced search. We intentionally re-run when `query` changes (not
  // `trimmed`) so the user sees "Type at least 2 characters…" immediately
  // when they delete down to 1 char.
  useEffect(() => {
    if (!hasQuery) {
      setResults([]);
      setLoading(false);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    const seq = ++requestSeq.current;
    const timer = setTimeout(async () => {
      try {
        const data = await usersApi.search(trimmed);
        if (seq !== requestSeq.current) return; // stale
        setResults(data);
        setActiveIdx(data.length > 0 ? 0 : -1);
      } catch (err) {
        if (seq !== requestSeq.current) return;
        setError(parseError(err, "Search failed"));
        setResults([]);
      } finally {
        if (seq === requestSeq.current) setLoading(false);
      }
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query, trimmed, hasQuery]);

  // Close the dropdown on outside click so it doesn't linger when the user
  // clicks elsewhere on the page.
  useEffect(() => {
    if (!open) return;
    const onDocMouseDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onDocMouseDown);
    return () => document.removeEventListener("mousedown", onDocMouseDown);
  }, [open]);

  const goTo = (user) => {
    setOpen(false);
    setQuery("");
    setResults([]);
    inputRef.current?.blur();
    navigate(`/users/${user.id}`);
  };

  const onKeyDown = (e) => {
    if (e.key === "Escape") {
      setOpen(false);
      inputRef.current?.blur();
      return;
    }
    if (!open) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIdx((i) => (results.length === 0 ? -1 : (i + 1) % results.length));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIdx((i) =>
        results.length === 0 ? -1 : (i - 1 + results.length) % results.length
      );
    } else if (e.key === "Enter") {
      const hit = results[activeIdx];
      if (hit) {
        e.preventDefault();
        goTo(hit);
      }
    }
  };

  // What to render inside the dropdown panel. Pulled out so the JSX below stays
  // a flat decision tree — kept inline-y because every branch needs the same
  // styling shell.
  const panelContent = useMemo(() => {
    if (!hasQuery) {
      return (
        <div className="search-hint">
          Type at least {MIN_QUERY} characters to search by name or email.
        </div>
      );
    }
    if (loading) {
      return <div className="search-hint">Searching…</div>;
    }
    if (error) {
      return <div className="search-hint search-error">{error}</div>;
    }
    if (results.length === 0) {
      return <div className="search-hint">No users match “{trimmed}”.</div>;
    }
    return (
      <ul
        id={listboxId}
        role="listbox"
        className="search-results"
      >
        {results.map((u, idx) => (
          <li
            key={u.id}
            role="option"
            aria-selected={idx === activeIdx}
            className={`search-result ${idx === activeIdx ? "active" : ""}`}
            onMouseEnter={() => setActiveIdx(idx)}
            // Use mousedown so the click registers before the input's blur
            // closes the dropdown.
            onMouseDown={(e) => {
              e.preventDefault();
              goTo(u);
            }}
          >
            <Avatar label={u.displayName} />
            <div className="search-result-text">
              <div className="search-result-name">{u.displayName}</div>
              {u.joinedAt && (
                <div className="search-result-meta">
                  Joined {new Date(u.joinedAt).toLocaleDateString()}
                </div>
              )}
            </div>
          </li>
        ))}
      </ul>
    );
  }, [hasQuery, loading, error, results, trimmed, activeIdx, listboxId]);

  return (
    <div className="user-search" ref={wrapRef}>
      <div className="user-search-input-wrap">
        <span className="user-search-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
          </svg>
        </span>
        <input
          ref={inputRef}
          type="search"
          role="combobox"
          aria-expanded={open}
          aria-controls={listboxId}
          aria-autocomplete="list"
          placeholder="Search users by name or email…"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
      </div>
      {open && <div className="user-search-panel">{panelContent}</div>}
    </div>
  );
}
