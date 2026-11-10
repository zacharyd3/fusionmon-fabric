import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { startServer } from "../src/server.js";

const ADMIN = "admin-secret";

test("runs on its own with a SQLite file that survives a restart", async () => {
  const dataDir = mkdtempSync(join(tmpdir(), "worldfirst-"));
  try {
    let running = await startServer({ port: 0, host: "127.0.0.1", dataDir, env: { ADMIN_TOKEN: ADMIN } });
    let base = `http://127.0.0.1:${running.port}`;

    const created = await fetch(`${base}/api/admin/servers`, {
      method: "POST",
      headers: { authorization: `Bearer ${ADMIN}`, "content-type": "application/json" },
      body: JSON.stringify({ name: "Unraid" }),
    });
    assert.equal(created.status, 200);
    const { token } = await created.json();

    const report = await fetch(`${base}/api/discoveries`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({
        head: "cobblemon:pikachu/normal", body: "cobblemon:charizard/normal", headName: "Pikachu",
        bodyName: "Charizard", playerId: "11111111-2222-3333-4444-555555555555", playerName: "Steve",
      }),
    });
    assert.equal(report.status, 200);
    assert.equal((await report.json()).first, true);

    const tooBig = await fetch(`${base}/api/discoveries`, {
      method: "POST", headers: { authorization: `Bearer ${token}` }, body: "x".repeat(20000),
    });
    assert.equal(tooBig.status, 413);

    await running.close();
    running = await startServer({ port: 0, host: "127.0.0.1", dataDir, env: { ADMIN_TOKEN: ADMIN } });
    base = `http://127.0.0.1:${running.port}`;
    const stats = await (await fetch(`${base}/api/stats`)).json();
    assert.equal(stats.discovered, 1);
    const page = await fetch(base);
    assert.equal(page.status, 200);
    assert.match(await page.text(), /Fusionmon World First/);
    await running.close();
  } finally {
    rmSync(dataDir, { recursive: true, force: true });
  }
});
