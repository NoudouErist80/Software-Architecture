# Database Setup

> **All databases run in Docker. You do NOT need to install anything locally.**

The `docker-compose.yml` in `VIBE_Backend_UPDATED/` spins up every database automatically.

## Databases Used

| Database | Port | Credentials | Used By |
|----------|------|-------------|---------|
| PostgreSQL (auth) | 5432 | vibe / vibe_local_2026 | auth-service |
| PostgreSQL (rewards) | 5433 | vibe / vibe_local_2026 | rewards-service |
| MongoDB | 27017 | vibe / vibe_local_2026 | feed, messaging, rooms, notifications, ai |
| Redis | 6379 | password: vibe_redis | auth (sessions/OTPs), rewards (leaderboard) |
| Kafka | 9092 | no auth | inter-service events |

## Schema Initialisation

- **PostgreSQL**: Spring Boot auto-creates tables on first start via JPA `ddl-auto: update`.
- **MongoDB**: Collections are created automatically on first document insert. `infrastructure/mongo/init.js` seeds initial indexes.
- **Redis**: No schema — keys are created at runtime with TTLs.

## Connecting Manually

```bash
# PostgreSQL (auth)
docker exec -it vibe-postgres-auth psql -U vibe -d vibe_auth

# PostgreSQL (rewards)
docker exec -it vibe-postgres-rewards psql -U vibe -d vibe_rewards

# MongoDB
docker exec -it vibe-mongo mongosh -u vibe -p vibe_local_2026

# Redis
docker exec -it vibe-redis redis-cli -a vibe_redis
```

## Mongo Express UI
Browse MongoDB at **http://localhost:8081** (admin / vibe2026)
