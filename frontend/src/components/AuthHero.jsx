/** Marketing-style sidebar shown next to the auth card on wide screens. */
export default function AuthHero({ kicker = "Live • Markets open" }) {
  return (
    <div className="auth-hero">
      <span className="eyebrow">
        <span className="dot" />
        {kicker}
      </span>
      <h1>
        Never miss a <span className="grad">price drop</span> again.
      </h1>
      <p>
        Track the symbols you care about, set a percentage threshold, and get
        an SMS the moment a stock dips far enough off its peak. Built for
        traders who don&apos;t want to babysit a chart.
      </p>
      <ul>
        <li>Watch unlimited tickers, set per-stock thresholds.</li>
        <li>Real-time price polling every 5 minutes.</li>
        <li>SMS alerts via Twilio with a built-in cool-down.</li>
        <li>Secure JWT auth — your watchlist stays private.</li>
      </ul>
    </div>
  );
}
