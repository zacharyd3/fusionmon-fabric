// Runs the World First service on your own machine (a home server, Unraid, a VPS) instead of Cloudflare: the same
// Worker code (index.js) behind Node's HTTP server, with the database in a SQLite file. No npm packages needed.
//
//   ADMIN_TOKEN=... node src/server.js        (or the Docker image: see README.md)
//
// Settings come from environment variables with the same names as wrangler.toml's [vars] and secrets, plus:
//   PORT (8787), DATA_DIR (./data: where worldfirst.db goes), TRUST_PROXY ("true" behind a reverse proxy that sets
//   X-Forwarded-For, so per-address registration limits see the real visitor).
import { createServer } from "node:http";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import worker from "./index.js";
import { createD1 } from "./d1-sqlite.js";

const SCHEMA = fileURLToPath(new URL("../schema.sql", import.meta.url));
const MAX_BODY = 16 * 1024;
// Headers about this connection, not the request: the Fetch API sets its own
const HOP_BY_HOP = new Set(["host", "connection", "keep-alive", "transfer-encoding", "content-length", "upgrade"]);
const SETTINGS = ["ADMIN_TOKEN", "DISCORD_WEBHOOK_URL", "REGISTRATION", "TOTAL_SPECIES", "DISCORD_EVERY",
  "MAX_REPORTS_PER_SERVER_HOUR", "MAX_REPORTS_PER_PLAYER_HOUR", "MAX_REGISTRATIONS_PER_DAY"];

/**
 * Starts the service. Resolves once it is listening, with the actual port (useful with port 0) and a close function.
 */
export function startServer({ port = 8787, host = "0.0.0.0", dataDir = "./data", env = {} } = {}) {
  mkdirSync(dataDir, { recursive: true });
  const db = createD1(SCHEMA, join(dataDir, "worldfirst.db"));
  const workerEnv = { DB: db };
  for (const key of SETTINGS) {
    if (env[key] !== undefined && env[key] !== "") {
      workerEnv[key] = env[key];
    }
  }
  const trustProxy = env.TRUST_PROXY === "true";

  const server = createServer(async (req, res) => {
    try {
      const chunks = [];
      let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > MAX_BODY) {
          res.writeHead(413, { "content-type": "application/json" }).end('{"error":"body too large"}');
          return;
        }
        chunks.push(chunk);
      }
      const headers = new Headers();
      for (const [name, value] of Object.entries(req.headers)) {
        if (value !== undefined && !HOP_BY_HOP.has(name)) {
          headers.set(name, Array.isArray(value) ? value.join(", ") : value);
        }
      }
      // The Worker reads the visitor's address from cf-connecting-ip, which Cloudflare sets. Here it comes from the
      // connection (or the reverse proxy), never from what the client sends
      const forwarded = trustProxy ? String(req.headers["x-forwarded-for"] ?? "").split(",")[0].trim() : "";
      headers.set("cf-connecting-ip", forwarded || req.socket.remoteAddress || "unknown");
      const method = req.method ?? "GET";
      const request = new Request(`http://${req.headers.host ?? "localhost"}${req.url}`, {
        method,
        headers,
        body: method === "GET" || method === "HEAD" ? undefined : Buffer.concat(chunks),
      });
      // waitUntil (the Discord post): let it finish in the background
      const response = await worker.fetch(request, workerEnv, {
        waitUntil: (promise) => promise.catch((error) => console.error(error)),
      });
      res.writeHead(response.status, Object.fromEntries(response.headers));
      res.end(Buffer.from(await response.arrayBuffer()));
    } catch (error) {
      console.error(error);
      if (!res.headersSent) {
        res.writeHead(500, { "content-type": "application/json" });
      }
      res.end('{"error":"internal error"}');
    }
  });

  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, host, () => {
      resolve({
        port: server.address().port,
        close: () => new Promise((done) => server.close(() => {
          db.close();
          done();
        })),
      });
    });
  });
}

// Run directly (node src/server.js, the Docker image)
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const env = process.env;
  if (!env.ADMIN_TOKEN) {
    console.warn("ADMIN_TOKEN is not set: you won't be able to create server tokens (see README.md)");
  }
  const running = await startServer({
    port: Number(env.PORT ?? 8787),
    dataDir: env.DATA_DIR ?? "./data",
    env,
  });
  console.log(`Fusionmon World First listening on port ${running.port}, data in ${env.DATA_DIR ?? "./data"}`);
  // Docker stops containers with SIGTERM; Node as the container's main process ignores it unless asked
  for (const signal of ["SIGTERM", "SIGINT"]) {
    process.once(signal, async () => {
      await running.close();
      process.exit(0);
    });
  }
}
