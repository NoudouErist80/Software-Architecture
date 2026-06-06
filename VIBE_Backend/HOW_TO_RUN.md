# How to Run VIBE

## Prerequisites

- Docker Desktop (running) — **all databases run in Docker, do NOT install them locally**
- Java 17+ (only needed if running services outside Docker)
- Node.js 18+ (for the frontend)
- Maven 3.9+ (only needed if building outside Docker)

## Step 1 — Configure AI (optional)

Edit `VIBE_Backend_UPDATED/.env` and set your Anthropic Claude API key:

```
CLAUDE_API_KEY=sk-ant-api03-...
```

If left blank, translation and summarisation return a friendly fallback instead of an error.

## Step 2 — Start Backend (all services + all databases)

```bash
cd VIBE_Backend_UPDATED
docker-compose up --build -d
```

This starts:
- PostgreSQL ×2 (ports 5432, 5433)
- MongoDB (port 27017)
- Redis (port 6379)
- Kafka + Zookeeper (port 9092)
- Kafka UI (port 8080)
- Mongo Express (port 8081)
- Prometheus (port 9090)
- Grafana (port 3001)
- All 9 Spring Boot microservices

## Step 3 — Verify Health

```bash
# Check all containers are up
docker-compose ps

# Check gateway health
curl http://localhost:8090/actuator/health
```

Expected: `{"status":"UP",...}`

## Step 4 — Start Frontend

```bash
cd ../vibe-frontend-v2
npm install
npm run dev
```

Open **http://localhost:3000**

## Stopping

```bash
cd VIBE_Backend_UPDATED
docker-compose down
```

## Resetting All Data

```bash
docker-compose down
docker volume ls | grep vibe | awk '{print $2}' | xargs docker volume rm 2>/dev/null || true
docker-compose up --build -d
```
