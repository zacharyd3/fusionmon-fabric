# Fusionmon World First

A public record of **which trainer discovered each fusion first**, across every Minecraft server that takes part.
When a player creates a pair nobody had ever made on any participating server, their server announces a
**World First** in chat ("🌍 World First #1,234! Steve is the first trainer anywhere to discover Pikachu + Charizard!"),
it appears on the leaderboard page, and it can be posted to your Discord.

You can run it in either of two places:

- **On your own machine** (Unraid, a home server, a VPS) as a Docker container, with the data in one SQLite file. See
  [Run it on your own server](#run-it-on-your-own-server-docker-unraid).
- **On Cloudflare**, as a [Worker](https://developers.cloudflare.com/workers/) with a
  [D1](https://developers.cloudflare.com/d1/) database. The free plan is plenty, and there is nothing for you to keep
  running. See [Deploy on Cloudflare](#deploy-on-cloudflare).

Both run the same code (`src/index.js`).

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

## Run it on your own server (Docker, Unraid)

You need Docker; on Unraid it is built in. Use the Unraid web terminal (the `>_` icon at the top right).

1. Put this `worldfirst` folder on the box, for example with git:

   ```bash
   git clone https://github.com/zacharyd3/fusionmon-fabric /mnt/user/appdata/fusionmon-src
   ```

2. Build the image:

   ```bash
   docker build -t fusionmon-worldfirst /mnt/user/appdata/fusionmon-src/worldfirst
   ```

3. Make an admin password (save what it prints somewhere safe; you need it to add servers):

   ```bash
   openssl rand -hex 32
   ```

4. Start the container. Replace `YOUR_ADMIN_PASSWORD` with the password from step 3. `--user 99:100` is Unraid's
   `nobody:users`, the owner of `appdata`:

   ```bash
   mkdir -p /mnt/user/appdata/fusionmon-worldfirst && docker run -d --name fusionmon-worldfirst --restart unless-stopped --user 99:100 -p 8787:8787 -v /mnt/user/appdata/fusionmon-worldfirst:/data -e ADMIN_TOKEN=YOUR_ADMIN_PASSWORD fusionmon-worldfirst
   ```

   It shows up in Unraid's Docker tab, where you can start, stop and see its logs. If you use the Compose Manager plugin
   instead, `docker-compose.yml` in this folder does the same; put `ADMIN_TOKEN=...` in a `.env` file next to it.

5. Open `http://<your Unraid IP>:8787`: the leaderboard page, empty for now.

6. Create a token for your Minecraft server (replace the IP and password):

   ```bash
   curl -X POST http://<your Unraid IP>:8787/api/admin/servers -H "Authorization: Bearer YOUR_ADMIN_PASSWORD" -H "content-type: application/json" -d '{"name": "My Server"}'
   ```

   In the Minecraft server's `config/fusionmon.json` (in its appdata folder), set `"worldFirstUrl":
   "http://<your Unraid IP>:8787"` and `"worldFirstToken"` to the token, then restart it. Use the Unraid IP even if the
   Minecraft server runs on the same box: inside another container, `localhost` means that container.

- **Only your server?** That's all; nothing needs to be open to the internet.
- **Other people's servers too?** They need to reach it from the internet. Put it behind a reverse proxy with HTTPS
  (Nginx Proxy Manager, SWAG) or a Cloudflare Tunnel, add `-e TRUST_PROXY=true` to the `docker run` line, and give them
  `https://` addresses and their own tokens.
- **Discord:** add `-e DISCORD_WEBHOOK_URL=...` to the `docker run` line.
- **Other settings:** the same names as `[vars]` in `wrangler.toml`, as `-e NAME=value`.
- **Backups:** everything is in `/mnt/user/appdata/fusionmon-worldfirst/worldfirst.db` (the Appdata Backup plugin
  covers it).
- **Updating:** `git -C /mnt/user/appdata/fusionmon-src pull`, build the image again (step 2), then
  `docker rm -f fusionmon-worldfirst` and start it again (step 4). The data stays.

Without Docker, it also runs with Node.js 22.13 or newer: `ADMIN_TOKEN=... npm start` in this folder (data in `./data`).

## Deploy on Cloudflare

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

Create a token for it (replace the address and `ADMIN` with yours; on your own server the address is
`http://<your IP>:8787`):

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

The tests run the Worker against Node's built-in SQLite (`src/d1-sqlite.js` mimics D1, the same adapter the Docker image
uses), and the self-hosted server end to end. `npx wrangler dev` runs it as a local Worker instead.
