-- Fusionmon World First: who discovered each fusion first, across every server that takes part.
-- Apply with: npx wrangler d1 execute fusionmon-worldfirst --remote --file=schema.sql

-- Minecraft servers that may report discoveries. Only the SHA-256 of their token is stored.
CREATE TABLE IF NOT EXISTS servers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_at INTEGER NOT NULL,
    banned INTEGER NOT NULL DEFAULT 0
);

-- The first report of each pair from each server. Kept even when it is not a world first, so that banning a server
-- can hand its world firsts to the next server in line.
CREATE TABLE IF NOT EXISTS reports (
    pair TEXT NOT NULL,
    server_id INTEGER NOT NULL REFERENCES servers(id),
    head TEXT NOT NULL,
    body TEXT NOT NULL,
    head_name TEXT NOT NULL,
    body_name TEXT NOT NULL,
    player_id TEXT NOT NULL,
    player_name TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (pair, server_id)
);
CREATE INDEX IF NOT EXISTS reports_server_time ON reports(server_id, created_at);
CREATE INDEX IF NOT EXISTS reports_player_time ON reports(player_id, created_at);
CREATE INDEX IF NOT EXISTS reports_pair_time ON reports(pair, created_at);

-- The world first of each pair: the earliest report from a server that is not banned.
CREATE TABLE IF NOT EXISTS firsts (
    pair TEXT PRIMARY KEY,
    head TEXT NOT NULL,
    body TEXT NOT NULL,
    head_name TEXT NOT NULL,
    body_name TEXT NOT NULL,
    server_id INTEGER NOT NULL REFERENCES servers(id),
    player_id TEXT NOT NULL,
    player_name TEXT,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS firsts_time ON firsts(created_at);
CREATE INDEX IF NOT EXISTS firsts_player ON firsts(player_id);
CREATE INDEX IF NOT EXISTS firsts_server ON firsts(server_id);

-- Self-service registrations (only when REGISTRATION = "open"), to limit them per address.
CREATE TABLE IF NOT EXISTS registrations (
    address_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS registrations_address ON registrations(address_hash, created_at);
