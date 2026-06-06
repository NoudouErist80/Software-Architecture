# Docker & Databases Explained

## Why Docker?

VIBE uses Docker so every database, broker, and service runs identically on every machine — no manual installation, no "works on my machine" issues.

## What `docker-compose up --build -d` Does

1. Builds all 9 Spring Boot services into Docker images (using their `Dockerfile`s)
2. Starts all database containers (Postgres ×2, MongoDB, Redis)
3. Starts Kafka + Zookeeper
4. Starts monitoring tools (Kafka UI, Mongo Express, Prometheus, Grafana)
5. Starts all 9 Spring Boot service containers
6. Creates a Docker network `vibe-network` so all containers can talk to each other by hostname

## Container Names & Hostnames

| Container | Hostname (inside Docker) | External Port |
|-----------|--------------------------|---------------|
| vibe-postgres-auth | postgres-auth | 5432 |
| vibe-postgres-rewards | postgres-rewards | 5433 |
| vibe-mongo | mongo | 27017 |
| vibe-redis | redis | 6379 |
| vibe-kafka | kafka | 9092 |
| vibe-api-gateway | api-gateway | 8090 |
| vibe-auth-service | auth-service | 8081 |
| ... | ... | ... |

## Environment Variables

All configuration is in `VIBE_Backend_UPDATED/.env`. The `docker-compose.yml` passes these to each container. Key variables:

```
POSTGRES_PASSWORD=vibe_local_2026
MONGO_INITDB_ROOT_PASSWORD=vibe_local_2026
REDIS_PASSWORD=vibe_redis
JWT_SECRET=ViBe_Super_Secret_2026_Africa_TchangoNoudouJoseph
CLAUDE_API_KEY=           ← set this for AI features
```

## Health Checks

Each service exposes `/actuator/health`. Docker Compose waits for dependent services to be healthy before starting services that need them (e.g., auth-service waits for postgres-auth to be healthy).

## Volumes

Docker volumes persist database data between restarts:
- `vibe-postgres-auth-data`
- `vibe-postgres-rewards-data`
- `vibe-mongo-data`
- `vibe-redis-data`

To wipe all data: `docker volume ls | grep vibe | awk '{print $2}' | xargs docker volume rm`
