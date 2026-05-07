import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext.jsx";
import { parseError, userApi } from "../services/api.js";

const MAX_LEN = 60;

export default function Profile() {
  const { email, displayName, updateDisplayName } = useAuth();

  const [draft, setDraft] = useState(displayName || "");
  const [phoneNumber, setPhoneNumber] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Pull the canonical profile on mount so the form reflects whatever is
  // actually persisted (in case localStorage drifted from the server).
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const profile = await userApi.getProfile();
        if (cancelled) return;
        setPhoneNumber(profile.phoneNumber || null);
        setDraft(profile.displayName || "");
      } catch (err) {
        if (!cancelled) setError(parseError(err, "Could not load your profile"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const fallbackName = email?.split("@")[0] || "";
  const trimmed = draft.trim();
  // Only enable Save when the value would actually change on the server.
  const dirty = trimmed !== (displayName || "");
  const tooLong = trimmed.length > MAX_LEN;

  const onSave = async (e) => {
    e.preventDefault();
    if (!dirty || tooLong) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const profile = await updateDisplayName(trimmed);
      setDraft(profile.displayName || "");
      setSuccess(
        profile.displayName
          ? "Display name updated."
          : "Display name cleared — we'll use your email instead."
      );
    } catch (err) {
      setError(parseError(err, "Could not update display name"));
    } finally {
      setSaving(false);
    }
  };

  const onClear = async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await updateDisplayName("");
      setDraft("");
      setSuccess("Display name cleared — we'll use your email instead.");
    } catch (err) {
      setError(parseError(err, "Could not clear display name"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="app-shell">
      <div className="dashboard">
        <header className="page-header">
          <h2>Your profile</h2>
          <p>Choose how your name appears in the app.</p>
        </header>

        <section className="card">
          <div className="card-header">
            <div>
              <h3>Display name</h3>
              <div className="card-subtitle">
                Shown in the navigation bar and the dashboard greeting. Leave
                blank to use <code>{fallbackName}</code> from your email.
              </div>
            </div>
          </div>

          {error && <div className="error-banner">{error}</div>}
          {success && <div className="success-banner">{success}</div>}

          {loading ? (
            <div className="loading-row">
              <div className="skeleton w-50" />
              <div className="skeleton w-30" />
            </div>
          ) : (
            <form className="profile-form" onSubmit={onSave}>
              <div className="form-group">
                <label htmlFor="email">Email</label>
                <input id="email" type="email" value={email || ""} disabled readOnly />
              </div>
              {phoneNumber && (
                <div className="form-group">
                  <label htmlFor="phoneNumber">Phone number</label>
                  <input
                    id="phoneNumber"
                    type="tel"
                    value={phoneNumber}
                    disabled
                    readOnly
                  />
                </div>
              )}
              <div className="form-group">
                <label htmlFor="displayName">Display name</label>
                <input
                  id="displayName"
                  name="displayName"
                  type="text"
                  autoComplete="nickname"
                  placeholder={fallbackName || "Your preferred name"}
                  maxLength={MAX_LEN}
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                />
                <div className="field-hint">
                  {trimmed.length}/{MAX_LEN} characters
                </div>
              </div>

              <div className="profile-actions">
                <button
                  type="submit"
                  className="primary"
                  disabled={!dirty || tooLong || saving}
                >
                  {saving ? <><span className="spinner" />Saving…</> : "Save changes"}
                </button>
                {displayName && (
                  <button
                    type="button"
                    className="ghost"
                    onClick={onClear}
                    disabled={saving}
                  >
                    Clear
                  </button>
                )}
              </div>
            </form>
          )}
        </section>
      </div>
    </div>
  );
}
