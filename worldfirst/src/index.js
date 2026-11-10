// Fusionmon World First: a small Cloudflare Worker (with a D1 database) that records which trainer discovered each
// fusion first, across every Minecraft server that takes part.
//
// Servers report discoveries with their own token; the first report of a pair (from a server that is not banned) is
// its world first. A web page shows the leaderboard, and a Discord webhook can announce new world firsts.
// See README.md for how to deploy it and how servers join.

import { renderPage } from "./page.js";

const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

// A pair is "head>body", each part as Fusionmon writes it: "cobblemon:charizard/normal" (species id / form name).
const PART = /^[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,64}\/[a-z0-9_. -]{1,32}$/;
const PLAYER_NAME = /^[A-Za-z0-9_]{1,16}$/;
const PLAYER_ID = /^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|anon-[0-9a-f]{64})$/;

export default {
  async fetch(request, env, ctx) {
    try {
      return await route(request, env, ctx);
    } catch (error) {
      if (error instanceof HttpError) {
        return json({ error: error.message }, error.status);
      }
      console.error(error);
      return json({ error: "internal error" }, 500);
    }
  },
};

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function route(request, env, ctx) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method;

  if (method === "GET" && path === "/") {
    return new Response(renderPage(await stats(env, 25)), {
      headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=60" },
    });
  }
  if (method === "GET" && path === "/api/stats") {
    const limit = clampInt(url.searchParams.get("limit"), 1, 100, 10);
    return json(await stats(env, limit), 200, { "cache-control": "public, max-age=30" });
  }
  if (method === "GET" && path.startsWith("/api/firsts/")) {
    const pair = decodeURIComponent(path.slice("/api/firsts/".length));
    const first = await firstOf(env, pair);
    if (!first) {
      throw new HttpError(404, "not discovered yet");
    }
    return json(first);
  }
  if (method === "POST" && path === "/api/discoveries") {
    return json(await report(request, env, ctx));
  }
  if (method === "POST" && path === "/api/register") {
    return json(await register(request, env), 201);
  }
  if (path.startsWith("/api/admin/")) {
    await requireAdmin(request, env);
    return json(await admin(request, env, method, path.slice("/api/admin/".length)));
  }
  throw new HttpError(404, "not found");
}

// ---- Reports from servers ----

async function report(request, env, ctx) {
  const server = await requireServer(request, env);
  const body = await readJson(request);
  const head = string(body.head);
  const bodyPart = string(body.body);
  if (!PART.test(head) || !PART.test(bodyPart)) {
    throw new HttpError(400, "bad species");
  }
  const pair = `${head}>${bodyPart}`;
  if (body.pair !== undefined && body.pair !== pair) {
    throw new HttpError(400, "pair does not match head and body");
  }
  const headName = displayName(body.headName, head);
  const bodyName = displayName(body.bodyName, bodyPart);
  const playerId = string(body.playerId);
  if (!PLAYER_ID.test(playerId)) {
    throw new HttpError(400, "bad player id");
  }
  const playerName = typeof body.playerName === "string" && PLAYER_NAME.test(body.playerName) ? body.playerName : null;

  const now = Date.now();
  await rateLimit(env, server.id, playerId, now);

  const [, inserted] = await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO reports (pair, server_id, head, body, head_name, body_name, player_id, player_name, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (pair, server_id) DO NOTHING`
    ).bind(pair, server.id, head, bodyPart, headName, bodyName, playerId, playerName, now),
    env.DB.prepare(
      `INSERT INTO firsts (pair, head, body, head_name, body_name, server_id, player_id, player_name, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (pair) DO NOTHING RETURNING pair`
    ).bind(pair, head, bodyPart, headName, bodyName, server.id, playerId, playerName, now),
  ]);
  const isFirst = (inserted.results ?? []).length > 0;
  const first = await firstOf(env, pair);
  if (isFirst && env.DISCORD_WEBHOOK_URL) {
    const announcement = announce(env, first);
    if (announcement) {
      ctx.waitUntil(announcement);
    }
  }
  return { ...first, first: isFirst && first.playerId === playerId };
}

/** The world first of a pair, as the API returns it, or null. */
async function firstOf(env, pair) {
  const row = await env.DB.prepare(
    `SELECT f.pair, f.head, f.body, f.head_name, f.body_name, f.player_id, f.player_name, f.created_at,
            s.name AS server_name,
            (SELECT COUNT(*) FROM firsts f2 WHERE f2.created_at < f.created_at
               OR (f2.created_at = f.created_at AND f2.pair <= f.pair)) AS number
       FROM firsts f JOIN servers s ON s.id = f.server_id WHERE f.pair = ?`
  ).bind(pair).first();
  if (!row) {
    return null;
  }
  return {
    pair: row.pair,
    head: row.head,
    body: row.body,
    headName: row.head_name,
    bodyName: row.body_name,
    playerId: row.player_id,
    discoverer: row.player_name,
    server: row.server_name,
    discoveredAt: row.created_at,
    number: row.number,
  };
}

async function rateLimit(env, serverId, playerId, now) {
  const serverLimit = Number(env.MAX_REPORTS_PER_SERVER_HOUR ?? 600);
  const playerLimit = Number(env.MAX_REPORTS_PER_PLAYER_HOUR ?? 120);
  const counts = await env.DB.prepare(
    `SELECT (SELECT COUNT(*) FROM reports WHERE server_id = ? AND created_at > ?) AS server_count,
            (SELECT COUNT(*) FROM reports WHERE player_id = ? AND created_at > ?) AS player_count`
  ).bind(serverId, now - HOUR, playerId, now - HOUR).first();
  if (counts.server_count >= serverLimit || counts.player_count >= playerLimit) {
    throw new HttpError(429, "too many reports, try again later");
  }
}

// ---- Discord ----

/** Posts a world first to Discord: one in every DISCORD_EVERY, and every thousandth. */
function announce(env, first) {
  const every = Math.max(1, Number(env.DISCORD_EVERY ?? 1));
  if (first.number % every !== 0 && first.number % 1000 !== 0) {
    return null;
  }
  const who = first.discoverer ? `**${markdown(first.discoverer)}**` : "An anonymous trainer";
  const content = `🌍 **World First #${first.number.toLocaleString("en-US")}!** ${who} on *${markdown(first.server)}*`
    + ` is the first trainer to discover **${markdown(first.headName)} + ${markdown(first.bodyName)}**!`;
  return fetch(env.DISCORD_WEBHOOK_URL, {
    method: "POST",
    headers: JSON_HEADERS,
    // Never ping anyone, whatever a name contains
    body: JSON.stringify({ content, username: "Fusionmon World First", allowed_mentions: { parse: [] } }),
  }).catch((error) => console.error("Discord webhook failed", error));
}

function markdown(text) {
  return String(text).replace(/[\\*_~`|>#@\[\]()]/g, "\\$&");
}

// ---- Leaderboard ----

async function stats(env, limit) {
  const totalSpecies = Number(env.TOTAL_SPECIES ?? 1025);
  const [totals, top, recent, servers] = await env.DB.batch([
    env.DB.prepare(
      `SELECT (SELECT COUNT(*) FROM firsts) AS pairs,
              (SELECT COUNT(DISTINCT head || '>' || body) FROM
                 (SELECT substr(head, 1, instr(head, '/') - 1) AS head, substr(body, 1, instr(body, '/') - 1) AS body
                    FROM firsts)) AS species_pairs,
              (SELECT COUNT(DISTINCT player_id) FROM firsts) AS players,
              (SELECT COUNT(*) FROM servers WHERE banned = 0) AS servers`
    ),
    env.DB.prepare(
      `SELECT player_id, MAX(player_name) AS player_name, COUNT(*) AS count FROM firsts
        WHERE player_name IS NOT NULL GROUP BY player_id ORDER BY count DESC, MIN(created_at) LIMIT ?`
    ).bind(limit),
    env.DB.prepare(
      `SELECT f.head_name, f.body_name, f.player_name, f.created_at, s.name AS server_name
         FROM firsts f JOIN servers s ON s.id = f.server_id ORDER BY f.created_at DESC LIMIT ?`
    ).bind(limit),
    env.DB.prepare(
      `SELECT s.name, COUNT(*) AS count FROM firsts f JOIN servers s ON s.id = f.server_id
        GROUP BY s.id ORDER BY count DESC LIMIT ?`
    ).bind(limit),
  ]);
  const total = totals.results[0];
  const possible = totalSpecies * totalSpecies;
  return {
    discovered: total.pairs,
    speciesPairs: total.species_pairs,
    possibleSpeciesPairs: possible,
    percent: possible > 0 ? Math.round((total.species_pairs / possible) * 100000) / 1000 : 0,
    players: total.players,
    servers: total.servers,
    topTrainers: top.results.map((row) => ({ name: row.player_name, count: row.count })),
    recent: recent.results.map((row) => ({
      headName: row.head_name,
      bodyName: row.body_name,
      discoverer: row.player_name,
      server: row.server_name,
      discoveredAt: row.created_at,
    })),
    topServers: servers.results.map((row) => ({ name: row.name, count: row.count })),
  };
}

// ---- Servers ----

async function requireServer(request, env) {
  const token = bearer(request);
  if (!token) {
    throw new HttpError(401, "missing server token");
  }
  const server = await env.DB.prepare("SELECT id, name, banned FROM servers WHERE token_hash = ?")
    .bind(await sha256(token)).first();
  if (!server) {
    throw new HttpError(401, "unknown server token");
  }
  if (server.banned) {
    throw new HttpError(403, "this server has been removed from World First");
  }
  return server;
}

/** Self-service registration, only with REGISTRATION = "open": a few per address and day. */
async function register(request, env) {
  if (env.REGISTRATION !== "open") {
    throw new HttpError(403, "registration is closed: ask the Fusionmon team for a server token");
  }
  const body = await readJson(request);
  const name = serverName(body.name);
  const address = request.headers.get("cf-connecting-ip") ?? "unknown";
  const addressHash = await sha256(`${address}:${env.ADMIN_TOKEN ?? ""}`);
  const now = Date.now();
  const recent = await env.DB.prepare("SELECT COUNT(*) AS count FROM registrations WHERE address_hash = ? AND created_at > ?")
    .bind(addressHash, now - DAY).first();
  if (recent.count >= Number(env.MAX_REGISTRATIONS_PER_DAY ?? 3)) {
    throw new HttpError(429, "too many registrations from this address today");
  }
  await env.DB.prepare("INSERT INTO registrations (address_hash, created_at) VALUES (?, ?)").bind(addressHash, now).run();
  return createServer(env, name);
}

async function createServer(env, name) {
  const token = randomToken();
  const result = await env.DB.prepare("INSERT INTO servers (name, token_hash, created_at) VALUES (?, ?, ?) RETURNING id")
    .bind(name, await sha256(token), Date.now()).first();
  // The token is only shown once: only its hash is stored
  return { id: result.id, name, token };
}

// ---- Admin ----

async function requireAdmin(request, env) {
  const token = bearer(request);
  if (!env.ADMIN_TOKEN || !token || !(await sameSecret(token, env.ADMIN_TOKEN))) {
    throw new HttpError(401, "admin token required");
  }
}

async function admin(request, env, method, path) {
  if (method === "GET" && path === "servers") {
    const rows = await env.DB.prepare(
      `SELECT s.id, s.name, s.created_at, s.banned,
              (SELECT COUNT(*) FROM reports r WHERE r.server_id = s.id) AS reports,
              (SELECT COUNT(*) FROM firsts f WHERE f.server_id = s.id) AS firsts
         FROM servers s ORDER BY s.id`
    ).all();
    return { servers: rows.results };
  }
  if (method === "POST" && path === "servers") {
    const body = await readJson(request);
    return createServer(env, serverName(body.name));
  }
  const match = /^servers\/(\d+)\/(ban|unban|rotate)$/.exec(path);
  if (method === "POST" && match) {
    const id = Number(match[1]);
    const server = await env.DB.prepare("SELECT id, name FROM servers WHERE id = ?").bind(id).first();
    if (!server) {
      throw new HttpError(404, "no such server");
    }
    if (match[2] === "rotate") {
      // New token for a server whose token leaked; the old one stops working at once
      const token = randomToken();
      await env.DB.prepare("UPDATE servers SET token_hash = ? WHERE id = ?").bind(await sha256(token), id).run();
      return { id, name: server.name, token };
    }
    const banned = match[2] === "ban" ? 1 : 0;
    await env.DB.prepare("UPDATE servers SET banned = ? WHERE id = ?").bind(banned, id).run();
    const changed = await recompute(env, id);
    return { id, name: server.name, banned: banned === 1, recomputedPairs: changed };
  }
  throw new HttpError(404, "not found");
}

/**
 * After banning or unbanning a server: every pair it reported goes to whoever reported it first among the servers that
 * are not banned (the next in line when banning, possibly this server again when unbanning).
 */
async function recompute(env, serverId) {
  const [pairs] = await env.DB.batch([
    env.DB.prepare("SELECT COUNT(*) AS count FROM reports WHERE server_id = ?").bind(serverId),
    env.DB.prepare("DELETE FROM firsts WHERE pair IN (SELECT pair FROM reports WHERE server_id = ?)").bind(serverId),
    env.DB.prepare(
      `INSERT INTO firsts (pair, head, body, head_name, body_name, server_id, player_id, player_name, created_at)
       SELECT r.pair, r.head, r.body, r.head_name, r.body_name, r.server_id, r.player_id, r.player_name, r.created_at
         FROM reports r JOIN servers s ON s.id = r.server_id AND s.banned = 0
        WHERE r.pair IN (SELECT pair FROM reports WHERE server_id = ?)
          AND r.created_at = (SELECT MIN(r2.created_at) FROM reports r2 JOIN servers s2 ON s2.id = r2.server_id
                               AND s2.banned = 0 WHERE r2.pair = r.pair)
        ORDER BY r.created_at
       ON CONFLICT (pair) DO NOTHING`
    ).bind(serverId),
  ]);
  return pairs.results[0].count;
}

// ---- Helpers ----

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers: { ...JSON_HEADERS, ...headers } });
}

async function readJson(request) {
  const text = await request.text();
  if (text.length > 4096) {
    throw new HttpError(413, "body too large");
  }
  try {
    const value = JSON.parse(text);
    if (value === null || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("not an object");
    }
    return value;
  } catch {
    throw new HttpError(400, "body must be a JSON object");
  }
}

function bearer(request) {
  const header = request.headers.get("authorization") ?? "";
  return header.startsWith("Bearer ") ? header.slice(7).trim() : null;
}

function string(value) {
  return typeof value === "string" ? value : "";
}

/** A species name for display ("Pikachu", "Raichu (Alola)"); the id's path if missing or odd. */
function displayName(value, part) {
  const clean = typeof value === "string" ? value.replace(/[\u0000-\u001f\u007f<>]/g, "").trim() : "";
  if (clean.length > 0 && clean.length <= 48) {
    return clean;
  }
  return part.slice(part.indexOf(":") + 1, part.indexOf("/"));
}

function serverName(value) {
  const clean = typeof value === "string" ? value.replace(/[\u0000-\u001f\u007f<>]/g, "").trim() : "";
  if (clean.length < 2 || clean.length > 32) {
    throw new HttpError(400, "server name must be 2-32 characters");
  }
  return clean;
}

function clampInt(value, min, max, fallback) {
  const number = Number.parseInt(value ?? "", 10);
  return Number.isFinite(number) ? Math.min(max, Math.max(min, number)) : fallback;
}

function randomToken() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** Compares two secrets without leaking how much of them matches through timing (compares their hashes). */
async function sameSecret(a, b) {
  const [x, y] = await Promise.all([sha256(a), sha256(b)]);
  let difference = 0;
  for (let i = 0; i < x.length; i++) {
    difference |= x.charCodeAt(i) ^ y.charCodeAt(i);
  }
  return difference === 0;
}
