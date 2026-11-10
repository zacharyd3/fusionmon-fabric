import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import worker from "../src/index.js";
import { createD1 } from "../src/d1-sqlite.js";

const SCHEMA = fileURLToPath(new URL("../schema.sql", import.meta.url));
const ADMIN = "admin-secret";
const UUID_A = "11111111-2222-3333-4444-555555555555";
const UUID_B = "66666666-7777-8888-9999-aaaaaaaaaaaa";

let env;
let waits;
let posted;

beforeEach(() => {
  env = { DB: createD1(SCHEMA), ADMIN_TOKEN: ADMIN, REGISTRATION: "closed" };
  waits = [];
  posted = [];
  globalThis.fetch = async (url, init) => {
    posted.push({ url, body: JSON.parse(init.body) });
    return new Response(null, { status: 204 });
  };
});

async function call(method, path, { token, body, headers = {} } = {}) {
  const init = { method, headers: { ...headers } };
  if (token) {
    init.headers.authorization = `Bearer ${token}`;
  }
  if (body !== undefined) {
    init.body = typeof body === "string" ? body : JSON.stringify(body);
    init.headers["content-type"] = "application/json";
  }
  const response = await worker.fetch(new Request(`https://worldfirst.test${path}`, init), env,
    { waitUntil: (promise) => waits.push(promise) });
  const text = await response.text();
  let data = text;
  try {
    data = JSON.parse(text);
  } catch {
    // HTML
  }
  return { status: response.status, data, headers: response.headers };
}

async function newServer(name) {
  const { status, data } = await call("POST", "/api/admin/servers", { token: ADMIN, body: { name } });
  assert.equal(status, 200);
  assert.match(data.token, /^[0-9a-f]{64}$/);
  return data;
}

function discovery(head, body, playerId = UUID_A, playerName = "Steve") {
  return {
    pair: `${head}>${body}`, head, body, headName: "Pikachu", bodyName: "Charizard", playerId, playerName,
  };
}

const PIKACHU = "cobblemon:pikachu/normal";
const CHARIZARD = "cobblemon:charizard/normal";

test("admin endpoints need the admin token", async () => {
  assert.equal((await call("POST", "/api/admin/servers", { body: { name: "X Server" } })).status, 401);
  assert.equal((await call("POST", "/api/admin/servers", { token: "wrong", body: { name: "X" } })).status, 401);
  assert.equal((await call("GET", "/api/admin/servers", { token: ADMIN })).status, 200);
});

test("registration is closed by default and limited per address when open", async () => {
  assert.equal((await call("POST", "/api/register", { body: { name: "Mine" } })).status, 403);
  env.REGISTRATION = "open";
  env.MAX_REGISTRATIONS_PER_DAY = "2";
  const headers = { "cf-connecting-ip": "203.0.113.5" };
  assert.equal((await call("POST", "/api/register", { body: { name: "One" }, headers })).status, 201);
  assert.equal((await call("POST", "/api/register", { body: { name: "Two" }, headers })).status, 201);
  assert.equal((await call("POST", "/api/register", { body: { name: "Three" }, headers })).status, 429);
});

test("the first report of a pair is the world first; later ones point to it", async () => {
  const a = await newServer("Alpha");
  const b = await newServer("Beta");
  env.DISCORD_WEBHOOK_URL = "https://discord.test/hook";

  const first = await call("POST", "/api/discoveries", { token: a.token, body: discovery(PIKACHU, CHARIZARD) });
  assert.equal(first.status, 200);
  assert.equal(first.data.first, true);
  assert.equal(first.data.number, 1);
  assert.equal(first.data.discoverer, "Steve");
  assert.equal(first.data.server, "Alpha");

  const second = await call("POST", "/api/discoveries", {
    token: b.token, body: discovery(PIKACHU, CHARIZARD, UUID_B, "Alex"),
  });
  assert.equal(second.data.first, false);
  assert.equal(second.data.discoverer, "Steve");
  assert.equal(second.data.server, "Alpha");

  // The reverse order is another fusion
  const reverse = await call("POST", "/api/discoveries", {
    token: b.token, body: discovery(CHARIZARD, PIKACHU, UUID_B, "Alex"),
  });
  assert.equal(reverse.data.first, true);
  assert.equal(reverse.data.number, 2);

  await Promise.all(waits);
  assert.equal(posted.length, 2);
  assert.match(posted[0].body.content, /World First #1!.*Steve.*Alpha.*Pikachu \+ Charizard/);
  assert.deepEqual(posted[0].body.allowed_mentions, { parse: [] });

  const lookup = await call("GET", `/api/firsts/${encodeURIComponent(`${PIKACHU}>${CHARIZARD}`)}`);
  assert.equal(lookup.data.discoverer, "Steve");
  assert.equal((await call("GET", "/api/firsts/cobblemon%3Aeevee%2Fnormal%3Ecobblemon%3Aeevee%2Fnormal")).status, 404);
});

test("reports are validated", async () => {
  const a = await newServer("Alpha");
  assert.equal((await call("POST", "/api/discoveries", { body: discovery(PIKACHU, CHARIZARD) })).status, 401);
  assert.equal((await call("POST", "/api/discoveries", { token: "nope", body: discovery(PIKACHU, CHARIZARD) })).status, 401);
  const bad = [
    { ...discovery(PIKACHU, CHARIZARD), head: "pikachu" },
    { ...discovery(PIKACHU, CHARIZARD), pair: `${CHARIZARD}>${PIKACHU}` },
    { ...discovery(PIKACHU, CHARIZARD), playerId: "not-a-uuid" },
    "[1, 2]",
    "not json",
  ];
  for (const body of bad) {
    assert.equal((await call("POST", "/api/discoveries", { token: a.token, body })).status, 400, JSON.stringify(body));
  }
});

test("anonymous trainers are kept anonymous", async () => {
  const a = await newServer("Alpha");
  const anon = "anon-" + "ab".repeat(32);
  const result = await call("POST", "/api/discoveries", {
    token: a.token, body: discovery(PIKACHU, CHARIZARD, anon, null),
  });
  assert.equal(result.data.first, true);
  assert.equal(result.data.discoverer, null);
  // A name that is not a Minecraft name is dropped too
  const odd = await call("POST", "/api/discoveries", {
    token: a.token, body: discovery(CHARIZARD, PIKACHU, UUID_B, "<script>"),
  });
  assert.equal(odd.data.discoverer, null);
  const stats = await call("GET", "/api/stats");
  assert.equal(stats.data.topTrainers.length, 0);
});

test("banning a server hands its world firsts to the next server, unbanning gives them back", async () => {
  const a = await newServer("Alpha");
  const b = await newServer("Beta");
  await call("POST", "/api/discoveries", { token: a.token, body: discovery(PIKACHU, CHARIZARD) });
  await new Promise((resolve) => setTimeout(resolve, 5));
  await call("POST", "/api/discoveries", { token: b.token, body: discovery(PIKACHU, CHARIZARD, UUID_B, "Alex") });

  const ban = await call("POST", `/api/admin/servers/${a.id}/ban`, { token: ADMIN });
  assert.equal(ban.data.banned, true);
  let first = await call("GET", `/api/firsts/${encodeURIComponent(`${PIKACHU}>${CHARIZARD}`)}`);
  assert.equal(first.data.discoverer, "Alex");
  assert.equal((await call("POST", "/api/discoveries", { token: a.token, body: discovery(CHARIZARD, PIKACHU) })).status, 403);

  await call("POST", `/api/admin/servers/${a.id}/unban`, { token: ADMIN });
  first = await call("GET", `/api/firsts/${encodeURIComponent(`${PIKACHU}>${CHARIZARD}`)}`);
  assert.equal(first.data.discoverer, "Steve");
});

test("a rotated token replaces the old one", async () => {
  const a = await newServer("Alpha");
  const rotated = await call("POST", `/api/admin/servers/${a.id}/rotate`, { token: ADMIN });
  assert.notEqual(rotated.data.token, a.token);
  assert.equal((await call("POST", "/api/discoveries", { token: a.token, body: discovery(PIKACHU, CHARIZARD) })).status, 401);
  assert.equal((await call("POST", "/api/discoveries", { token: rotated.data.token, body: discovery(PIKACHU, CHARIZARD) })).status, 200);
});

test("reports are rate limited per server and per player", async () => {
  const a = await newServer("Alpha");
  env.MAX_REPORTS_PER_PLAYER_HOUR = "2";
  const species = ["eevee", "vulpix", "growlithe"];
  const statuses = [];
  for (const name of species) {
    const result = await call("POST", "/api/discoveries", {
      token: a.token, body: discovery(`cobblemon:${name}/normal`, CHARIZARD),
    });
    statuses.push(result.status);
  }
  assert.deepEqual(statuses, [200, 200, 429]);
});

test("the page and stats show the leaderboard, escaped", async () => {
  const a = await newServer("<b>Evil</b> & Co");
  await call("POST", "/api/discoveries", { token: a.token, body: discovery(PIKACHU, CHARIZARD) });
  await call("POST", "/api/discoveries", {
    token: a.token, body: { ...discovery(CHARIZARD, PIKACHU), headName: "Char<img>izard" },
  });
  const stats = await call("GET", "/api/stats");
  assert.equal(stats.data.discovered, 2);
  assert.equal(stats.data.speciesPairs, 2);
  assert.equal(stats.data.topTrainers[0].name, "Steve");
  assert.equal(stats.data.topTrainers[0].count, 2);

  const page = await call("GET", "/");
  assert.equal(page.status, 200);
  assert.match(page.headers.get("content-type"), /text\/html/);
  assert.ok(!page.data.includes("<b>Evil"), "server names are escaped");
  assert.ok(page.data.includes("bEvil/b &amp; Co"), "angle brackets are stripped from server names");
  assert.ok(page.data.includes("Charimgizard"), "angle brackets are stripped from species names");
});
