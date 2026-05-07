import { useMemo, useRef, useState } from "react";

// SVG viewBox dimensions; we draw in this fixed coordinate space and let
// CSS scale the chart responsively.
const W = 720;
const H = 280;
const PADDING = { top: 16, right: 16, bottom: 28, left: 56 };
const PLOT_W = W - PADDING.left - PADDING.right;
const PLOT_H = H - PADDING.top - PADDING.bottom;

const Y_TICKS = 5;
const X_TICKS = 6;

function formatPrice(v) {
  return `$${Number(v).toFixed(2)}`;
}

function formatXLabel(date, interval) {
  if (interval === "DAY") {
    return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }
  if (interval === "WEEK") {
    return date.toLocaleDateString([], { weekday: "short" });
  }
  if (interval === "MONTH") {
    return date.toLocaleDateString([], { month: "short", day: "numeric" });
  }
  return date.toLocaleDateString([], { month: "short", year: "2-digit" });
}

function formatTooltipTime(date, interval) {
  if (interval === "DAY" || interval === "WEEK") {
    return date.toLocaleString([], {
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
    });
  }
  return date.toLocaleDateString([], {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

/**
 * Pick "nice" round numbers for Y-axis tick labels around the data range so
 * the gridlines land on values like 100/110/120 instead of 103.47/108.91/...
 */
function niceTicks(min, max, count) {
  if (min === max) {
    const v = min;
    return Array.from({ length: count }, (_, i) => v + (i - Math.floor(count / 2)));
  }
  const range = max - min;
  const step0 = range / (count - 1);
  const mag = Math.pow(10, Math.floor(Math.log10(step0)));
  const norm = step0 / mag;
  const niceNorm = norm >= 5 ? 10 : norm >= 2 ? 5 : norm >= 1 ? 2 : 1;
  const step = niceNorm * mag;
  const niceMin = Math.floor(min / step) * step;
  const ticks = [];
  for (let v = niceMin; v <= max + step / 2; v += step) ticks.push(v);
  return ticks;
}

export default function PriceChart({ points, interval, loading }) {
  const svgRef = useRef(null);
  const [hover, setHover] = useState(null); // index of hovered point

  const chart = useMemo(() => {
    if (!points || points.length === 0) return null;

    const xs = points.map((p) => new Date(p.timestamp).getTime());
    const ys = points.map((p) => Number(p.price));
    const xMin = xs[0];
    const xMax = xs[xs.length - 1];
    const yMin = Math.min(...ys);
    const yMax = Math.max(...ys);

    const ticks = niceTicks(yMin, yMax, Y_TICKS);
    const yLo = Math.min(yMin, ticks[0]);
    const yHi = Math.max(yMax, ticks[ticks.length - 1]);
    const yRange = yHi - yLo || 1;
    const xRange = xMax - xMin || 1;

    const xAt = (t) => PADDING.left + ((t - xMin) / xRange) * PLOT_W;
    const yAt = (v) => PADDING.top + (1 - (v - yLo) / yRange) * PLOT_H;

    const coords = points.map((p, i) => ({
      x: xAt(xs[i]),
      y: yAt(ys[i]),
      price: ys[i],
      time: new Date(xs[i]),
    }));

    const linePath = coords
      .map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(2)},${c.y.toFixed(2)}`)
      .join(" ");

    const areaPath =
      `M${coords[0].x.toFixed(2)},${(PADDING.top + PLOT_H).toFixed(2)} ` +
      coords.map((c) => `L${c.x.toFixed(2)},${c.y.toFixed(2)}`).join(" ") +
      ` L${coords[coords.length - 1].x.toFixed(2)},${(PADDING.top + PLOT_H).toFixed(2)} Z`;

    // Pick evenly spaced X tick indices so labels never crowd.
    const xTickCount = Math.min(X_TICKS, points.length);
    const xTickIndices = Array.from({ length: xTickCount }, (_, i) =>
      Math.round((i * (points.length - 1)) / Math.max(1, xTickCount - 1))
    );

    const first = ys[0];
    const last = ys[ys.length - 1];
    const trend = last >= first ? "up" : "down";
    const change = last - first;
    const changePct = first === 0 ? 0 : (change / first) * 100;

    return {
      coords,
      linePath,
      areaPath,
      yTicks: ticks,
      yAt,
      xTickIndices,
      first,
      last,
      change,
      changePct,
      trend,
      yLo,
      yHi,
    };
  }, [points]);

  const onMove = (e) => {
    if (!chart || !svgRef.current) return;
    const rect = svgRef.current.getBoundingClientRect();
    // Translate the cursor's client X into our SVG viewBox coordinate space.
    const cursorX = ((e.clientX - rect.left) / rect.width) * W;
    let nearest = 0;
    let bestDist = Infinity;
    chart.coords.forEach((c, i) => {
      const d = Math.abs(c.x - cursorX);
      if (d < bestDist) {
        bestDist = d;
        nearest = i;
      }
    });
    setHover(nearest);
  };

  const onLeave = () => setHover(null);

  if (loading && (!points || points.length === 0)) {
    return (
      <div className="chart-wrap">
        <div className="chart-skeleton" />
      </div>
    );
  }

  if (!chart) {
    return (
      <div className="chart-wrap">
        <div className="chart-empty">No price data available.</div>
      </div>
    );
  }

  const hoverPoint = hover != null ? chart.coords[hover] : null;
  const trendClass = chart.trend === "up" ? "up" : "down";

  return (
    <div className={`chart-wrap ${loading ? "is-refreshing" : ""}`}>
      <div className="chart-header">
        <div>
          <div className="chart-current">{formatPrice(chart.last)}</div>
          <div className={`chart-change ${trendClass}`}>
            {chart.change >= 0 ? "▲" : "▼"} {formatPrice(Math.abs(chart.change))} ({chart.changePct.toFixed(2)}%)
            <span className="chart-change-label">over the selected window</span>
          </div>
        </div>
      </div>

      <svg
        ref={svgRef}
        viewBox={`0 0 ${W} ${H}`}
        className="price-chart"
        preserveAspectRatio="none"
        onMouseMove={onMove}
        onMouseLeave={onLeave}
        role="img"
        aria-label="Price history chart"
      >
        <defs>
          <linearGradient id="chart-stroke" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#7c5cff" />
            <stop offset="55%" stopColor="#4f8cff" />
            <stop offset="100%" stopColor="#22d3ee" />
          </linearGradient>
          <linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#4f8cff" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#4f8cff" stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* Y gridlines + labels */}
        {chart.yTicks.map((t, i) => {
          const y = chart.yAt(t);
          return (
            <g key={`y-${i}`}>
              <line
                x1={PADDING.left}
                x2={W - PADDING.right}
                y1={y}
                y2={y}
                className="grid-line"
              />
              <text
                x={PADDING.left - 8}
                y={y + 4}
                textAnchor="end"
                className="axis-label"
              >
                {formatPrice(t)}
              </text>
            </g>
          );
        })}

        {/* X axis labels (no vertical gridlines — keeps it less busy) */}
        {chart.xTickIndices.map((idx) => {
          const c = chart.coords[idx];
          return (
            <text
              key={`x-${idx}`}
              x={c.x}
              y={H - PADDING.bottom + 18}
              textAnchor="middle"
              className="axis-label"
            >
              {formatXLabel(c.time, interval)}
            </text>
          );
        })}

        <path d={chart.areaPath} fill="url(#chart-fill)" />
        <path
          d={chart.linePath}
          fill="none"
          stroke="url(#chart-stroke)"
          strokeWidth="2.25"
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        {hoverPoint && (
          <g pointerEvents="none">
            <line
              x1={hoverPoint.x}
              x2={hoverPoint.x}
              y1={PADDING.top}
              y2={PADDING.top + PLOT_H}
              className="hover-line"
            />
            <circle
              cx={hoverPoint.x}
              cy={hoverPoint.y}
              r="5"
              className="hover-dot-outer"
            />
            <circle
              cx={hoverPoint.x}
              cy={hoverPoint.y}
              r="2.5"
              className="hover-dot-inner"
            />
          </g>
        )}
      </svg>

      {hoverPoint && (
        <div
          className="chart-tooltip"
          style={{
            // Translate viewBox X into a CSS percent so the tooltip tracks
            // the cursor regardless of the chart's rendered width.
            left: `${(hoverPoint.x / W) * 100}%`,
          }}
        >
          <div className="chart-tooltip-price">{formatPrice(hoverPoint.price)}</div>
          <div className="chart-tooltip-time">
            {formatTooltipTime(hoverPoint.time, interval)}
          </div>
        </div>
      )}
    </div>
  );
}
