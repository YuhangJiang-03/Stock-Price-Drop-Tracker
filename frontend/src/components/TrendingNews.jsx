import { useEffect, useState } from "react";
import { newsApi, parseError } from "../services/api.js";

/**
 * Format a publish timestamp as a short, human-friendly relative string
 * ("just now", "5m ago", "3h ago", "2d ago"). Falls back to a localized
 * date for anything older than ~10 days, where the relative form starts
 * to feel less useful than an actual date.
 */
function formatRelativeTime(iso) {
  if (!iso) return "";
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const diffMs = Date.now() - then;
  if (diffMs < 60_000) return "just now";

  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 60) return `${minutes}m ago`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;

  const days = Math.floor(hours / 24);
  if (days < 10) return `${days}d ago`;

  return new Date(iso).toLocaleDateString();
}

function NewsCardSkeleton() {
  return (
    <article className="news-card is-loading" aria-hidden="true">
      <div className="news-card-image skeleton-block" />
      <div className="news-card-body">
        <div className="skeleton skeleton-line w-90" />
        <div className="skeleton skeleton-line w-70" />
        <div className="skeleton skeleton-line w-40" />
      </div>
    </article>
  );
}

function NewsCard({ article }) {
  const subtitleParts = [];
  if (article.publisher) subtitleParts.push(article.publisher);
  const relative = formatRelativeTime(article.publishedAt);
  if (relative) subtitleParts.push(relative);
  const subtitle = subtitleParts.join(" · ");

  // Up to two related-tickers as chips — more than that starts to compete
  // with the headline for attention.
  const chips = (article.relatedTickers || []).slice(0, 2);

  return (
    <a
      href={article.articleUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="news-card"
    >
      <div className="news-card-image">
        {article.imageUrl ? (
          <img
            src={article.imageUrl}
            alt=""
            loading="lazy"
            // Yahoo's CDN occasionally 403s a thumbnail — hide the broken
            // <img> so the card falls back to its gradient background
            // instead of showing the browser's default broken-image icon.
            onError={(e) => { e.currentTarget.style.display = "none"; }}
          />
        ) : (
          <div className="news-card-image-fallback" aria-hidden="true">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 17 L9 11 L13 15 L21 6" />
              <path d="M14 6 L21 6 L21 13" />
            </svg>
          </div>
        )}
        {chips.length > 0 && (
          <div className="news-card-chips">
            {chips.map((sym) => (
              <span key={sym} className="news-chip">{sym}</span>
            ))}
          </div>
        )}
      </div>
      <div className="news-card-body">
        <h4 className="news-card-title">{article.title}</h4>
        {subtitle && <div className="news-card-subtitle">{subtitle}</div>}
        <span className="news-card-cta">
          Read article
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
            <path d="M7 17 L17 7" />
            <path d="M8 7 L17 7 L17 16" />
          </svg>
        </span>
      </div>
    </a>
  );
}

export default function TrendingNews({ limit = 3 }) {
  const [articles, setArticles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await newsApi.trending(limit);
        if (!cancelled) setArticles(data);
      } catch (err) {
        if (!cancelled) setError(parseError(err, "Could not load trending news"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [limit]);

  // Quietly hide the strip when the upstream is unavailable — the dashboard
  // is still useful without it, and an error banner here would distract from
  // the actual watchlist below.
  if (error && articles.length === 0) {
    return null;
  }
  if (!loading && articles.length === 0) {
    return null;
  }

  return (
    <section className="trending-news" aria-label="Trending stock market news">
      <div className="trending-news-header">
        <span className="trending-news-eyebrow">
          <span className="trending-news-pulse" aria-hidden="true" />
          Trending now
        </span>
        <h3>Stock market headlines</h3>
      </div>
      <div className="news-grid">
        {loading
          ? Array.from({ length: limit }).map((_, i) => <NewsCardSkeleton key={i} />)
          : articles.map((a) => <NewsCard key={a.id} article={a} />)}
      </div>
    </section>
  );
}
