import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { parseError, usersApi } from "../services/api.js";
import { useAuth } from "../context/AuthContext.jsx";

function Avatar({ label }) {
  const initial = (label?.[0] || "?").toUpperCase();
  return <span className="profile-avatar" aria-hidden="true">{initial}</span>;
}

/**
 * Public read-only view of another user, fetched by id. The server only ever
 * returns {@code displayName} + {@code joinedAt} here — no email or phone — so
 * this page is intentionally minimal. Visiting your own id shows the same view
 * with a small "this is you" hint and a link to the editable /profile page.
 */
export default function UserProfile() {
  const { id } = useParams();
  const { email, displayName: myDisplayName } = useAuth();

  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setUser(null);
    (async () => {
      try {
        const data = await usersApi.getById(id);
        if (!cancelled) setUser(data);
      } catch (err) {
        if (!cancelled) setError(parseError(err, "Could not load this profile"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id]);

  // Compare the resolved user's displayName against ours to detect "this is
  // me". The summary endpoint doesn't return email, so we lean on the same
  // fallback the server uses (displayName || email-local-part).
  const myFallback = myDisplayName || email?.split("@")[0] || "";
  const isSelf = user && user.displayName === myFallback;

  return (
    <div className="app-shell">
      <div className="dashboard">
        <Link to="/" className="back-link">← Back to dashboard</Link>

        {error && <div className="error-banner">{error}</div>}

        <section className="card user-profile-card">
          {loading ? (
            <div className="loading-row">
              <div className="skeleton w-30" />
              <div className="skeleton w-50" />
              <div className="skeleton w-70" />
            </div>
          ) : user ? (
            <>
              <div className="user-profile-hero">
                <Avatar label={user.displayName} />
                <div>
                  <h2 className="user-profile-name">{user.displayName}</h2>
                  {user.joinedAt && (
                    <div className="user-profile-meta">
                      Member since{" "}
                      {new Date(user.joinedAt).toLocaleDateString(undefined, {
                        year: "numeric",
                        month: "long",
                        day: "numeric",
                      })}
                    </div>
                  )}
                </div>
              </div>

              {isSelf && (
                <div className="info-banner">
                  This is your public profile. Other users see exactly what's on
                  this page — no email, no phone.{" "}
                  <Link to="/profile">Edit your display name →</Link>
                </div>
              )}
            </>
          ) : (
            !error && (
              <div className="empty">
                <div className="empty-title">User not found</div>
                <div className="empty-sub">
                  This profile may have been deleted or never existed.
                </div>
              </div>
            )
          )}
        </section>
      </div>
    </div>
  );
}
