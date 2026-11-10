#!/bin/bash
# Fusionmon World First: set up (or update) the service on Unraid.
#
# For the User Scripts plugin: Settings > User Scripts > Add New Script, paste this file, edit the settings below,
# then "Run Script". Safe to run again: it updates the code, rebuilds the image and recreates the container, keeping
# the data, the admin password and the server token.
#
# What it does:
#   1. downloads (or updates) the Fusionmon code into appdata
#   2. builds the fusionmon-worldfirst Docker image
#   3. makes an admin password the first time (saved in appdata, readable only by root)
#   4. (re)starts the fusionmon-worldfirst container on port 8787
#   5. creates a token for your Minecraft server the first time (saved in appdata)
#   6. if MC_CONFIG points at your server's config/fusionmon.json, writes the address and token into it
# Restart your Minecraft server afterwards so it reads the new config.

# ---- Settings ----

# Where the code comes from (the branch with World First; once it is merged, use main)
REPO_URL="https://github.com/zacharyd3/fusionmon-fabric.git"
BRANCH="update/happy-davinci-u09tn0"

# Name shown on the leaderboard for your Minecraft server
SERVER_NAME="My Server"

# Your Minecraft server's config/fusionmon.json (inside its appdata). Leave empty to edit it by hand.
# Example: MC_CONFIG="/mnt/user/appdata/minecraft/config/fusionmon.json"
MC_CONFIG=""

# Port for the leaderboard page and the API
PORT=8787

# Optional: a Discord channel webhook to announce World Firsts (leave empty for none)
DISCORD_WEBHOOK_URL=""

# Unraid's IP as seen from the Minecraft server. Empty = detect it.
UNRAID_IP=""

# ---- No changes needed below ----

set -euo pipefail

APPDATA="/mnt/user/appdata"
SRC="$APPDATA/fusionmon-src"
DATA="$APPDATA/fusionmon-worldfirst"
SECRETS="$APPDATA/fusionmon-worldfirst-secrets"
IMAGE="fusionmon-worldfirst"
CONTAINER="fusionmon-worldfirst"

step() { echo; echo "==> $*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }

command -v docker >/dev/null || fail "Docker isn't available. Enable it in Settings > Docker."
command -v git >/dev/null || fail "git isn't available on this Unraid version."

if [ -z "$UNRAID_IP" ]; then
    UNRAID_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}' || true)
    [ -n "$UNRAID_IP" ] || UNRAID_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || true)
    [ -n "$UNRAID_IP" ] || fail "Couldn't detect Unraid's IP: set UNRAID_IP in the settings."
fi
URL="http://$UNRAID_IP:$PORT"

step "Getting the code ($BRANCH)"
if [ -d "$SRC/.git" ]; then
    git -C "$SRC" fetch --depth 1 origin "$BRANCH"
    git -C "$SRC" checkout -q -B "$BRANCH" FETCH_HEAD
else
    git clone --depth 1 --branch "$BRANCH" "$REPO_URL" "$SRC"
fi

step "Building the image"
docker build -t "$IMAGE" "$SRC/worldfirst"

step "Admin password"
mkdir -p "$SECRETS"
chmod 700 "$SECRETS"
if [ ! -s "$SECRETS/admin-token" ]; then
    openssl rand -hex 32 > "$SECRETS/admin-token"
    chmod 600 "$SECRETS/admin-token"
    echo "Made a new one, saved in $SECRETS/admin-token"
else
    echo "Using the one in $SECRETS/admin-token"
fi
ADMIN_TOKEN=$(cat "$SECRETS/admin-token")

step "Starting the container"
mkdir -p "$DATA"
chown 99:100 "$DATA"
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" --restart unless-stopped --user 99:100 \
    -p "$PORT:8787" -v "$DATA:/data" \
    -e ADMIN_TOKEN="$ADMIN_TOKEN" -e DISCORD_WEBHOOK_URL="$DISCORD_WEBHOOK_URL" \
    "$IMAGE" >/dev/null

echo -n "Waiting for it to answer"
for _ in $(seq 1 30); do
    if curl -fsS "http://127.0.0.1:$PORT/api/stats" >/dev/null 2>&1; then
        echo " ok"
        break
    fi
    echo -n "."
    sleep 1
done
curl -fsS "http://127.0.0.1:$PORT/api/stats" >/dev/null 2>&1 || fail "It didn't start: docker logs $CONTAINER"

step "Token for \"$SERVER_NAME\""
if [ ! -s "$SECRETS/server-token" ]; then
    NAME_JSON=$(printf '%s' "$SERVER_NAME" | sed 's/\\/\\\\/g; s/"/\\"/g')
    RESPONSE=$(curl -fsS -X POST "http://127.0.0.1:$PORT/api/admin/servers" \
        -H "Authorization: Bearer $ADMIN_TOKEN" -H "content-type: application/json" \
        -d "{\"name\": \"$NAME_JSON\"}") || fail "Couldn't create the token"
    TOKEN=$(printf '%s' "$RESPONSE" | sed -n 's/.*"token":"\([0-9a-f]*\)".*/\1/p')
    [ -n "$TOKEN" ] || fail "Unexpected answer: $RESPONSE"
    printf '%s' "$TOKEN" > "$SECRETS/server-token"
    chmod 600 "$SECRETS/server-token"
    echo "Made a new one, saved in $SECRETS/server-token"
else
    echo "Using the one in $SECRETS/server-token"
fi
SERVER_TOKEN=$(cat "$SECRETS/server-token")

if [ -n "$MC_CONFIG" ]; then
    step "Writing it into $MC_CONFIG"
    [ -f "$MC_CONFIG" ] || fail "$MC_CONFIG doesn't exist. Start the Minecraft server once with the mod so it creates it."
    cp "$MC_CONFIG" "$MC_CONFIG.bak"
    # The image has Node.js: it edits the JSON safely (keeps every other option as it was)
    docker run --rm --user 0 --entrypoint node -v "$(dirname "$MC_CONFIG"):/cfg" \
        -e FILE="/cfg/$(basename "$MC_CONFIG")" -e WF_URL="$URL" -e WF_TOKEN="$SERVER_TOKEN" "$IMAGE" -e '
            const fs = require("fs");
            const config = JSON.parse(fs.readFileSync(process.env.FILE, "utf8"));
            config.worldFirstUrl = process.env.WF_URL;
            config.worldFirstToken = process.env.WF_TOKEN;
            if (config.worldFirstShowNames === undefined) config.worldFirstShowNames = true;
            fs.writeFileSync(process.env.FILE, JSON.stringify(config, null, 2));
        '
    echo "Done (the old file is $MC_CONFIG.bak). Restart the Minecraft server."
fi

step "All set"
echo "Leaderboard:       $URL"
echo "Data:              $DATA/worldfirst.db"
echo "Admin password:    $SECRETS/admin-token"
if [ -z "$MC_CONFIG" ]; then
    echo
    echo "Put this in your Minecraft server's config/fusionmon.json and restart it:"
    echo "  \"worldFirstUrl\": \"$URL\","
    echo "  \"worldFirstToken\": \"$SERVER_TOKEN\","
fi
