# VIBE — African Social Media Platform

VIBE is a full-stack social media and communication platform built for Africa.  
It features a TikTok-style video feed, WhatsApp-style messenger, live audio rooms, a token rewards system, and AI-powered features (translation, summarisation).

## Architecture

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + Vite, Zustand, TailwindCSS |
| API Gateway | Spring Cloud Gateway (port 8090) |
| Microservices | 8 × Spring Boot 3.2 services (ports 8081–8088) |
| Databases | PostgreSQL (auth, rewards), MongoDB (feed, messaging, rooms, notifications, AI logs), Redis (cache, OTPs, sessions) |
| Messaging | Apache Kafka (event-driven between services) |
| Real-time | STOMP over WebSocket (SockJS) |
| Infrastructure | Docker + Docker Compose (everything in containers) |

## Services

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| api-gateway | 8090 | — | JWT validation, routing, CORS |
| auth-service | 8081 | PostgreSQL :5432 | Register, login, contacts, OTPs |
| messaging-service | 8082 | MongoDB | Conversations, messages, WebSocket |
| feed-service | 8083 | MongoDB | Posts, statuses, follow, search |
| rooms-service | 8084 | MongoDB | Live audio rooms |
| rewards-service | 8085 | PostgreSQL :5433 | Wallet, tokens, cashout, leaderboard |
| notification-service | 8086 | MongoDB | Push notifications via Kafka |
| ai-service | 8087 | MongoDB | Translation, summarisation (Claude API) |
| media-service | 8088 | local disk | File upload and serving |

## Quick Start (Single Command)

```bash
# 1. Start all infrastructure + all 9 backend services
cd VIBE_Backend_UPDATED
docker-compose up --build -d

# 2. Wait ~90 seconds for all services to become healthy
docker-compose ps   # all should show "healthy" or "running"

# 3. Start the frontend (new terminal)
cd ../vibe-frontend-v2
npm install && npm run dev

# 4. Open http://localhost:3000
```

## Credentials

| Service | Username | Password |
|---------|----------|----------|
| PostgreSQL (auth) | vibe | vibe_local_2026 |
| PostgreSQL (rewards) | vibe | vibe_local_2026 |
| MongoDB | vibe | vibe_local_2026 |
| Redis | — | vibe_redis |
| Grafana | admin | vibe_grafana_2026 |
| Mongo Express | admin | vibe2026 |

## Environment Variables

Copy `.env` and set your Claude API key for AI features:

```
CLAUDE_API_KEY=sk-ant-api03-...
```

If `CLAUDE_API_KEY` is empty, AI features return a graceful fallback message instead of crashing.

## Dashboard URLs (after docker-compose up)

| Tool | URL |
|------|-----|
| Kafka UI | http://localhost:8080 |
| Mongo Express | http://localhost:8081 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |
| API Docs (Gateway) | http://localhost:8090/swagger-ui.html |

## Fresh Start (Delete Old Data)

```bash
docker-compose down
docker volume ls | grep vibe | awk '{print $2}' | xargs docker volume rm 2>/dev/null || true
docker-compose up --build -d
```
