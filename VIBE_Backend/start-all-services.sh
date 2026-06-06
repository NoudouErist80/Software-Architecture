#!/bin/bash
set -e

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║     VIBE Platform — Starting All Services            ║"
echo "║     African Social Media — Zero Single Point Fail    ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# Navigate to project root
cd "$(dirname "$0")"

# ─── 1. Start Docker infrastructure ─────────────────────────────────────────
echo "► Starting Docker infrastructure (Postgres, MongoDB, Redis, Kafka)..."
docker-compose up -d

echo "► Waiting for all infrastructure to be healthy (60s)..."
sleep 60

echo "► Docker containers status:"
docker-compose ps

# ─── 2. Build shared common module ──────────────────────────────────────────
echo ""
echo "► Building vibe-common..."
cd shared/vibe-common
mvn clean install -q -DskipTests
cd ../..
echo "  ✓ vibe-common built"

# ─── 3. Start each service in background ────────────────────────────────────
SERVICES=(
  "auth-service:8081"
  "messaging-service:8082"
  "feed-service:8083"
  "rooms-service:8084"
  "rewards-service:8085"
  "notification-service:8086"
  "ai-service:8087"
  "media-service:8088"
  "api-gateway:8090"
)

for entry in "${SERVICES[@]}"; do
  SERVICE="${entry%%:*}"
  PORT="${entry##*:}"

  echo ""
  echo "► Starting $SERVICE on port $PORT..."
  cd "$SERVICE"
  mvn spring-boot:run -Dspring-boot.run.profiles=default \
      -DKAFKA_ENABLED=true \
      -DKAFKA_SERVERS=localhost:9092 \
      -DREDIS_PASSWORD=vibe_redis \
      -DJWT_SECRET=ViBe_Super_Secret_2026_Africa_TchangoNoudouJoseph \
      > "../logs/${SERVICE}.log" 2>&1 &
  echo "  PID $! → logs/${SERVICE}.log"
  cd ..
  sleep 5
done

echo ""
echo "══════════════════════════════════════════════════════"
echo "All services starting! Access at:"
echo "  Gateway:       http://localhost:8090"
echo "  Auth Swagger:  http://localhost:8081/swagger-ui.html"
echo "  Kafka UI:      http://localhost:8080"
echo "  MongoDB UI:    http://localhost:8081  (admin/vibe2026)"
echo "  Prometheus:    http://localhost:9090"
echo "  Grafana:       http://localhost:3001  (admin/vibe_grafana_2026)"
echo "══════════════════════════════════════════════════════"
