// The public leaderboard page (GET /): plain HTML, no scripts or outside resources, light and dark.

export function renderPage(stats) {
  const percent = stats.percent.toLocaleString("en-US", { maximumFractionDigits: 3 });
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Fusionmon World First</title>
<style>
  :root { --bg: #fafaf7; --fg: #1d1d1b; --muted: #6b6b66; --card: #ffffff; --line: #e4e3dd; --accent: #c2410c; }
  @media (prefers-color-scheme: dark) {
    :root { --bg: #151514; --fg: #ecebe6; --muted: #a3a29b; --card: #1f1f1d; --line: #33322f; --accent: #fb923c; }
  }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
  main { max-width: 960px; margin: 0 auto; padding: 32px 16px 48px; }
  h1 { margin: 0 0 4px; font-size: 28px; }
  h2 { font-size: 18px; margin: 0 0 12px; }
  p.lead { margin: 0 0 24px; color: var(--muted); }
  .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin-bottom: 24px; }
  .tile, section { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 16px; }
  .tile b { display: block; font-size: 26px; font-variant-numeric: tabular-nums; }
  .tile span { color: var(--muted); font-size: 14px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px; }
  .bar { height: 8px; background: var(--line); border-radius: 4px; overflow: hidden; margin-top: 8px; }
  .bar i { display: block; height: 100%; background: var(--accent); min-width: 2px; }
  ol, ul { margin: 0; padding-left: 20px; }
  li { padding: 3px 0; }
  .muted { color: var(--muted); font-size: 14px; }
  .num { font-variant-numeric: tabular-nums; color: var(--muted); }
  footer { margin-top: 24px; color: var(--muted); font-size: 13px; }
</style>
</head>
<body>
<main>
  <h1>🌍 Fusionmon World First</h1>
  <p class="lead">Every fusion discovered on every server that takes part, and the trainer who found it first.</p>
  <div class="tiles">
    <div class="tile"><b>${number(stats.discovered)}</b><span>world firsts</span></div>
    <div class="tile"><b>${percent}%</b><span>of ${number(stats.possibleSpeciesPairs)} species pairs</span>
      <div class="bar"><i style="width:${Math.min(100, stats.percent)}%"></i></div></div>
    <div class="tile"><b>${number(stats.players)}</b><span>trainers</span></div>
    <div class="tile"><b>${number(stats.servers)}</b><span>servers</span></div>
  </div>
  <div class="grid">
    <section>
      <h2>Top trainers</h2>
      ${list(stats.topTrainers, (row) => `${escape(row.name)} <span class="num">${number(row.count)}</span>`, "ol")}
    </section>
    <section>
      <h2>Latest world firsts</h2>
      ${list(stats.recent, (row) => `<b>${escape(row.headName)} + ${escape(row.bodyName)}</b><br>
        <span class="muted">${row.discoverer ? escape(row.discoverer) : "Anonymous trainer"} · ${escape(row.server)}
        · ${date(row.discoveredAt)}</span>`, "ul")}
    </section>
    <section>
      <h2>Top servers</h2>
      ${list(stats.topServers, (row) => `${escape(row.name)} <span class="num">${number(row.count)}</span>`, "ol")}
    </section>
  </div>
  <footer>Fusionmon is an unofficial fan project, not affiliated with Nintendo, Game Freak, The Pokémon Company or
    Cobblemon. Trainers who opt out are shown as anonymous.</footer>
</main>
</body>
</html>`;
}

function list(rows, item, tag) {
  if (rows.length === 0) {
    return `<p class="muted">Nothing yet. Go fuse something!</p>`;
  }
  return `<${tag}>${rows.map((row) => `<li>${item(row)}</li>`).join("")}</${tag}>`;
}

function number(value) {
  return Number(value).toLocaleString("en-US");
}

function date(millis) {
  return new Date(millis).toISOString().slice(0, 10);
}

function escape(text) {
  return String(text ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}
