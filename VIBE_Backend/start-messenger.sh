#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Start the VIBE Messenger backend locally (services run via Maven, databases in
# Docker). Uses the manually-installed JDK 17 + Maven so Lombok doesn't break on
# JDK 25. Each service logs to ./logs/<service>.log.
#
# Usage:
#   ./start-messenger.sh          # start all messenger services
#   tail -f logs/api-gateway.log  # watch a service
#   ./start-messenger.sh stop     # stop everything
# ─────────────────────────────────────────────────────────────────────────────
set -u

ROOT="/Users/user/Downloads/VIBE/VIBE-WebApp/VIBE_Backend"
LOGS="$ROOT/logs"
export JAVA_HOME="$HOME/.local/opt/jdk-17.0.19+10/Contents/Home"
export PATH="$HOME/.local/opt/apache-maven-3.9.16/bin:$PATH"

# Load secrets (CLAUDE_API_KEY, JWT_SECRET, REDIS_PASSWORD, …) from .env so the
# services — started here via Maven, not Docker — actually receive them.
# Without this the ai-service starts keyless and every AI feature falls back to
# "unavailable". MEDIA_UPLOAD_DIR is forced to a local path because the .env
# value (/app/uploads) is a container path that doesn't exist on the host.
if [ -f "$ROOT/.env" ]; then
  set -a; . "$ROOT/.env"; set +a
  export MEDIA_UPLOAD_DIR="$ROOT/uploads"; mkdir -p "$ROOT/uploads" 2>/dev/null
fi

# Services to start. Trim this list to go lighter — the CORE chat + tokens +
# notifications only needs: auth messaging rewards notification api-gateway.
SERVICES="auth-service messaging-service rewards-service notification-service feed-service ai-service media-service api-gateway"

if [ "${1:-}" = "stop" ]; then
  echo "Stopping all VIBE services..."
  pkill -f "spring-boot:run" 2>/dev/null || true
  echo "Done."
  exit 0
fi

# 1. Make sure the databases are up
echo "==> Ensuring databases are running (Docker)..."
( cd "$ROOT" && docker-compose up -d postgres-auth postgres-rewards mongodb redis kafka zookeeper ) >/dev/null 2>&1

# 2. Clean any old service processes
echo "==> Stopping any previously-running services..."
pkill -f "spring-boot:run" 2>/dev/null || true
sleep 3

# 3. Launch each service in the background
mkdir -p "$LOGS"
for s in $SERVICES; do
  echo "==> Starting $s ..."
  ( cd "$ROOT/$s" && nohup mvn -o spring-boot:run > "$LOGS/$s.log" 2>&1 & )
  sleep 3
done

echo ""
echo "All services launching. First run recompiles a few modules, so give it"
echo "2-4 minutes. Check readiness with:"
echo ""
echo "  for p in 8081 8082 8083 8085 8086 8087 8088 8090; do printf \"\$p: \"; curl -s -o /dev/null -w \"%{http_code}\\n\" http://localhost:\$p/actuator/health; done"
echo ""
echo "When port 8090 returns 200, the gateway is ready. Then start the frontend."
