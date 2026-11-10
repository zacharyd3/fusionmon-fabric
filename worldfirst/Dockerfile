# Fusionmon World First on your own machine (Unraid, a home server, a VPS): see README.md, "Run it on your own server".
FROM node:22-alpine
WORKDIR /app
COPY package.json schema.sql ./
COPY src ./src
RUN mkdir -p /data && chown node:node /data
USER node
ENV PORT=8787 DATA_DIR=/data NODE_ENV=production
EXPOSE 8787
VOLUME /data
HEALTHCHECK --interval=60s --timeout=5s CMD wget -qO- http://127.0.0.1:8787/api/stats > /dev/null || exit 1
CMD ["node", "--disable-warning=ExperimentalWarning", "src/server.js"]
