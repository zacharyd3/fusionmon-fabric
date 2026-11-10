# Fusionmon World First

A public record of **which trainer discovered each fusion first**, across every Minecraft server that takes part.
When a player creates a pair nobody had ever made on any participating server, their server announces a
**World First** in chat ("🌍 World First #1,234! Steve is the first trainer anywhere to discover Pikachu + Charizard!"),
it appears on the leaderboard page, and it can be posted to your Discord.

It is a small [Cloudflare Worker](https://developers.cloudflare.com/workers/) with a
[D1](https://developers.cloudflare.com/d1/) database. The free plan is plenty (100,000 requests and 5 GB a day), and
there is no server for you to keep running.

## How it works

- A server only takes part if its owner puts `worldFirstUrl` and `worldFirstToken` in `config/fusionmon.json`. By
  default nothing leaves the server.
- Each server has its own token. Only the SHA-256 of a token is stored.
- When a player creates a fusion that is new **for them**, the server sends the pair
  (`cobblemon:pikachu/normal>cobblemon:charizard/normal`), the species names, and the player: their UUID and name, or,
  if they chose to be anonymous (`/worldfirst name hide`), an id that can't be traced back to their account. Every
  player is told on their first join.
- The first report of a pair, by the time it reaches the Worker, is its world first. Reports that can't be sent (no
  network, the Worker down) wait in the world folder and are sent later.
- Rate limits per server and per player stop a broken or malicious server from flooding it. If a server cheats, ban
  it: its world firsts go to whoever reported each pair next.

## Deploy (once, about 10 minutes)

You need [Node.js](https://nodejs.org/) 22 or newer and a free Cloudflare account. From this folder:

```bash
npx wrangler login
```

```bash
npx wrangler d1 create fusionmon-worldfirst
```

Copy the `database_id` it prints into `wrangler.toml` (replace `REPLACE_WITH_YOUR_DATABASE_ID`), then create the tables:

```bash
npx wrangler d1 execute fusionmon-worldfirst --remote --file=schema.sql
```

Choose an admin password (a long random string) and store it as a secret. It is never in a file:

```bash
npx wrangler secret put ADMIN_TOKEN
```

Optional: a Discord channel webhook (Channel settings → Integrations → Webhooks → New webhook → Copy URL) to announce
world firsts:

```bash
npx wrangler secret put DISCORD_WEBHOOK_URL
```

Deploy:

```bash
npx wrangler deploy
```

It prints your address, e.g. `https://fusionmon-worldfirst.<you>.workers.dev`. Open it to see the leaderboard.

## Let a server join

Create a token for it (replace the address and `ADMIN` with yours):

```bash
curl -X POST https://fusionmon-worldfirst.<you>.workers.dev/api/admin/servers -H "Authorization: Bearer ADMIN" -H "content-type: application/json" -d '{"name": "CobbleFusion"}'
```

The answer has the token. It is shown only once, so send it to the server owner privately. They put it in
`config/fusionmon.json` and restart:

```json
"worldFirstUrl": "https://fusionmon-worldfirst.<you>.workers.dev",
"worldFirstToken": "the token",
"worldFirstShowNames": true
```

With `REGISTRATION = "open"` in `wrangler.toml`, server owners can get a token themselves (three per address per day):

```bash
curl -X POST https://fusionmon-worldfirst.<you>.workers.dev/api/register -H "content-type: application/json" -d '{"name": "My Server"}'
```

## Admin

All with `-H "Authorization: Bearer ADMIN"`:

| Request | What it does |
|---|---|
| `GET /api/admin/servers` | Every server with its reports and world firsts |
| `POST /api/admin/servers` `{"name": "..."}` | New server and its token |
| `POST /api/admin/servers/<id>/ban` | The server can't report any more; its world firsts go to the next server in line |
| `POST /api/admin/servers/<id>/unban` | Undoes a ban (its world firsts come back) |
| `POST /api/admin/servers/<id>/rotate` | New token for a server whose token leaked; the old one stops working |

## Public API

| Request | Answer |
|---|---|
| `GET /` | The leaderboard page |
| `GET /api/stats?limit=10` | Totals, % of species pairs discovered, top trainers, latest world firsts, top servers |
| `GET /api/firsts/<pair>` | Who discovered a pair first (the pair URL-encoded), or 404 |
| `POST /api/discoveries` | For servers (with their token) |

Settings in `wrangler.toml` → `[vars]`: `REGISTRATION`, `TOTAL_SPECIES` (for the % bar), `DISCORD_EVERY` (post one in
every N world firsts, plus every 1000th, if your Discord gets too busy), and the rate limits.

## Development

```bash
npm test
```

The tests run the Worker against Node's built-in SQLite (`test/d1.js` mimics D1). `npx wrangler dev` runs it locally
with a local D1 database (apply the schema with `--local` instead of `--remote` first).
