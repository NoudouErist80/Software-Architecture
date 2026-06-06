#!/bin/bash
set -e

echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║   VIBE Platform — Local Dev Startup (Maven + Docker DB)  ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

cd "$(dirname "$0")"

# ─── Start infrastructure only ───────────────────────────────────────────────
echo "► Starting infrastructure containers (DB, Redis, Kafka)..."
docker-compose up -d postgres-auth postgres-rewards mongodb redis zookeeper kafka

echo "► Waiting 45s for infrastructure to be ready..."
sleep 45
echo "► Infrastructure ready."

# ─── Build shared common ──────────────────────────────────────────────────────
echo "► Building vibe-common..."
cd shared/vibe-common && mvn clean install -DskipTests -q && cd ../..
echo "  ✓ vibe-common built"

# ─── Set common env vars ──────────────────────────────────────────────────────
export JWT_SECRET="ViBe_Super_Secret_2026_Africa_TchangoNoudouJoseph"
export REDIS_PASSWORD="vibe_redis"
export KAFKA_SERVERS="localhost:9092"
export KAFKA_ENABLED="true"
export CORS_ORIGINS="http://localhost:3000"
export CLAUDE_API_KEY="${CLAUDE_API_KEY:-}"

mkdir -p logs

# ─── Start services ───────────────────────────────────────────────────────────
declare -A SERVICES=(
  [auth-service]=8081
  [messaging-service]=8082
  [feed-service]=8083
  [rooms-service]=8084
  [rewards-service]=8085
  [notification-service]=8086
  [ai-service]=8087
  [media-service]=8088
)

for svc in auth-service messaging-service feed-service rooms-service rewards-service notification-service ai-service media-service; do
  port=${SERVICES[$svc]}
  echo "► Starting $svc (port $port)..."
  cd $svc
  mvn spring-boot:run \
    -DKAFKA_ENABLED=true \
    -DKAFKA_SERVERS=localhost:9092 \
    -DREDIS_PASSWORD=vibe_redis \
    -DJWT_SECRET="${JWT_SECRET}" \
    -DCLAUDE_API_KEY="${CLAUDE_API_KEY}" \
    > ../logs/${svc}.log 2>&1 &
  echo "  PID $! → logs/${svc}.log"
  cd ..
  sleep 8
done

echo ""
echo "► Waiting 60s for all services to start..."
sleep 60

echo ""
echo "► Starting api-gateway (port 8090)..."
cd api-gateway
mvn spring-boot:run \
  -DREDIS_PASSWORD=vibe_redis \
  -DJWT_SECRET="${JWT_SECRET}" \
  > ../logs/api-gateway.log 2>&1 &
echo "  PID $! → logs/api-gateway.log"
cd ..

echo ""
echo "══════════════════════════════════════════════════════════"
echo "✅  All VIBE services started!"
echo ""
echo "  API Gateway:     http://localhost:8090"
echo "  Auth Swagger:    http://localhost:8081/swagger-ui.html"
echo "  Feed Swagger:    http://localhost:8083/swagger-ui.html"
echo "  Kafka UI:        http://localhost:8080"
echo "  Prometheus:      http://localhost:9090"
echo "  Grafana:         http://localhost:3001  (admin/vibe_grafana_2026)"
echo ""
echo "  Start frontend:  cd ../vibe-frontend-v2 && npm install && npm run dev"
echo "══════════════════════════════════════════════════════════"
