# VIBE Platform — Complete Setup & Run Guide
## Facebook/TikTok-grade African Social Media Platform

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 21+ | https://adoptium.net |
| Maven | 3.9+ | `brew install maven` / `apt install maven` |
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| Node.js | 18+ | For frontend |
| Claude API Key | - | https://console.anthropic.com (for AI features) |

---

## Step 1 — Environment variables (create `.env` in project root)

```bash
# Copy this as .env file in VIBE_Backend_UPDATED/
JWT_SECRET=ViBe_Super_Secret_2026_Africa_TchangoNoudouJoseph
REDIS_PASSWORD=vibe_redis
KAFKA_SERVERS=localhost:9092
KAFKA_ENABLED=true
CLAUDE_API_KEY=your-claude-api-key-here
MEDIA_UPLOAD_DIR=./media-uploads
CORS_ORIGINS=http://localhost:3000
```

---

## Step 2 — Start Docker infrastructure

```bash
cd VIBE_Backend_UPDATED
docker-compose up -d

# Wait ~60 seconds, then verify all containers are healthy:
docker-compose ps

# Expected: All containers show "healthy" or "running"
# ✓ vibe-postgres-auth    (port 5432)
# ✓ vibe-postgres-rewards (port 5433)
# ✓ vibe-mongodb          (port 27017)
# ✓ vibe-redis            (port 6379)
# ✓ vibe-zookeeper        (port 2181)
# ✓ vibe-kafka            (port 9092)
# ✓ vibe-kafka-ui         (port 8080)
# ✓ vibe-mongo-express    (port 8081 → UI)
# ✓ vibe-prometheus       (port 9090)
# ✓ vibe-grafana          (port 3001)
```

---

## Step 3 — Build the shared module

```bash
cd shared/vibe-common
mvn clean install -DskipTests
cd ../..
```

---

## Step 4 — Start each service (8 terminals or use start-all-services.sh)

```bash
# Option A — All at once (background):
chmod +x start-all-services.sh && ./start-all-services.sh

# Option B — One by one (foreground, easier for debugging):

# Terminal 1 — Auth Service (port 8081)
cd auth-service
mvn spring-boot:run -DKAFKA_ENABLED=true -DREDIS_PASSWORD=vibe_redis

# Terminal 2 — Messaging Service (port 8082)
cd messaging-service
mvn spring-boot:run -DKAFKA_ENABLED=true -DREDIS_PASSWORD=vibe_redis

# Terminal 3 — Feed Service (port 8083)
cd feed-service
mvn spring-boot:run -DKAFKA_ENABLED=true -DREDIS_PASSWORD=vibe_redis

# Terminal 4 — Rooms Service (port 8084)
cd rooms-service
mvn spring-boot:run -DKAFKA_ENABLED=true -DREDIS_PASSWORD=vibe_redis

# Terminal 5 — Rewards Service (port 8085)
cd rewards-service
mvn spring-boot:run -DKAFKA_ENABLED=true -DREDIS_PASSWORD=vibe_redis

# Terminal 6 — Notification Service (port 8086)
cd notification-service
mvn spring-boot:run -DKAFKA_ENABLED=true

# Terminal 7 — AI Service (port 8087)
cd ai-service
mvn spring-boot:run -DCLAUDE_API_KEY=your-key-here

# Terminal 8 — Media Service (port 8088)
cd media-service
mvn spring-boot:run

# Terminal 9 — API Gateway (port 8090) — start LAST
cd api-gateway
mvn spring-boot:run -DREDIS_PASSWORD=vibe_redis
```

---

## Step 5 — Start the Frontend

```bash
cd VIBE_Frontend_v2
npm install
npm start
# Opens http://localhost:3000
```

**All API calls from the frontend go through:** `http://localhost:8090`

---

## Service Ports & Dashboards

| Service | Port | URL |
|---------|------|-----|
| API Gateway | 8090 | All frontend traffic routes here |
| Auth Service | 8081 | `/swagger-ui.html` |
| Messaging Service | 8082 | `/swagger-ui.html` |
| Feed Service | 8083 | `/swagger-ui.html` |
| Rooms Service | 8084 | `/swagger-ui.html` |
| Rewards Service | 8085 | `/swagger-ui.html` |
| Notification Service | 8086 | `/swagger-ui.html` |
| AI Service | 8087 | `/swagger-ui.html` |
| Media Service | 8088 | `/swagger-ui.html` |
| Kafka UI | 8080 | Monitor Kafka topics/consumers |
| MongoDB Express | 8081* | admin / vibe2026 |
| Prometheus | 9090 | Metrics dashboard |
| Grafana | 3001 | admin / vibe_grafana_2026 |

---

## Frontend ↔ Backend API Mapping

| Frontend Feature | Gateway Route | Backend Service |
|----------------|---------------|-----------------|
| Register / Login | `/auth/register`, `/auth/login` | auth-service:8081 |
| Get Profile | `/auth/me` | auth-service:8081 |
| Contacts / Search | `/auth/contacts` | auth-service:8081 |
| Block User | `/auth/contacts/{id}/block` | auth-service:8081 |
| Meet Friends | `/auth/contacts/suggestions` | auth-service:8081 |
| Feed / Posts | `/feed/posts` | feed-service:8083 |
| Statuses | `/feed/statuses` | feed-service:8083 |
| Status Like | `/feed/statuses/{id}/like` | feed-service:8083 |
| Status Comment | `/feed/statuses/{id}/comments` | feed-service:8083 |
| Set Mood | `/feed/mood` | feed-service:8083 |
| Conversations | `/messaging/conversations` | messaging-service:8082 |
| Send Message | `/messaging/conversations/{id}/messages` | messaging-service:8082 |
| Edit Message | `/messaging/messages/{id}` (PATCH) | messaging-service:8082 |
| Delete Message | `/messaging/messages/{id}` (DELETE) | messaging-service:8082 |
| AI Summary | `/messaging/conversations/{id}/ai-summary/unread` | messaging-service:8082 |
| Translate Message | `/messaging/messages/{id}/translate` | messaging-service:8082 |
| Rooms | `/rooms` | rooms-service:8084 |
| Token Wallet | `/rewards/wallet` | rewards-service:8085 |
| Cashout | `/rewards/cashout` | rewards-service:8085 |
| Notifications | `/notifications` | notification-service:8086 |
| AI Translate | `/ai/translate` | ai-service:8087 |
| Upload Media | `/media/upload` | media-service:8088 |
| WebSocket | `ws://localhost:8090/ws/messaging` | messaging-service:8082 |

---

## Stopping Everything

```bash
# Stop all Java services (if using start-all-services.sh):
pkill -f "spring-boot:run"

# Stop Docker:
docker-compose down

# Stop Docker AND delete all data (full reset):
docker-compose down -v
```

---

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | `ViBe_Super_Secret_2026...` | JWT signing key — change in prod! |
| `REDIS_PASSWORD` | `vibe_redis` | Redis password |
| `KAFKA_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `KAFKA_ENABLED` | `true` | Set `false` to disable Kafka locally |
| `CLAUDE_API_KEY` | *(empty)* | Your Anthropic API key for AI features |
| `MONGO_HOST` | `localhost` | MongoDB host |
| `REDIS_HOST` | `localhost` | Redis host |
| `CORS_ORIGINS` | `http://localhost:3000` | Allowed frontend origin |
| `MEDIA_UPLOAD_DIR` | `./media-uploads` | Local media storage dir |

---

## Token Earning Rules

| Action | Tokens | Daily Cap |
|--------|--------|-----------|
| Watch 30s+ of video | +2 | ✓ |
| Send a message | +1 | ✓ |
| React to a post | +1 | ✓ |
| Join room (per 10 min) | +1 | ✓ |
| Host room (per person per 10 min) | +2 | ✓ |
| Post a status | +2 | ✓ |
| Daily streak Day 1 | +5 | - |
| Daily streak Day 7 | +50 | - |
| Daily streak Day 30 | +300 | - |
| Refer a friend | +50 | - |
| Viral post (10K views in 24h) | +500 | - |
| **Daily earning cap** | **150** | |
| Min cashout | 500 tokens = 1,000 XAF | MTN MoMo / Orange Money |

---

## Kubernetes Setup (Next Steps)

After verifying everything works locally with Docker Compose, run:

```bash
# Install minikube (local Kubernetes)
brew install minikube  # macOS
# or: https://minikube.sigs.k8s.io/docs/start/

minikube start --memory=8192 --cpus=4

# Install Helm
brew install helm

# Deploy VIBE with Helm charts (provided separately)
helm install vibe ./infrastructure/helm/
```

The Kubernetes Helm charts will be provided in the next phase and will include:
- **HPA** (Horizontal Pod Autoscaler) — auto-scale each service from 2 to 50 replicas based on CPU/RPS
- **PodDisruptionBudget** — minimum 1 pod always available during deployments
- **Ingress + TLS** — NGINX ingress with Let's Encrypt cert
- **Kafka cluster** — 3-broker Kafka with replication factor 3
- **Redis Sentinel** — 3-node Redis with automatic failover

