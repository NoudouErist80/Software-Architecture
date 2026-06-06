# Complete Setup Guide

## System Requirements

| Tool | Version | Check |
|------|---------|-------|
| Docker Desktop | Latest | `docker --version` |
| Docker Compose | V2 (built-in) | `docker compose version` |
| Node.js | 18+ | `node --version` |
| npm | 9+ | `npm --version` |

Java and Maven are **not required** — services compile and run inside Docker.

## First-Time Setup

### 1. Extract the zip
```bash
unzip VIBE_WebApp_COMPLETE.zip
cd VIBE_WebApp
```

### 2. (Optional) Add your Claude API key for AI features
```bash
# Edit VIBE_Backend_UPDATED/.env
# Set: CLAUDE_API_KEY=sk-ant-api03-...
```

### 3. Start everything
```bash
cd VIBE_Backend_UPDATED
docker-compose up --build -d
```

First build takes 5–10 minutes (downloads base images, compiles Java). Subsequent starts take ~30 seconds.

### 4. Wait for health
```bash
# Watch containers come up
docker-compose ps

# Confirm gateway is ready
curl http://localhost:8090/actuator/health
```

### 5. Start frontend
```bash
cd ../vibe-frontend-v2
npm install
npm run dev
```

### 6. Open the app
Visit **http://localhost:3000**, register an account, and start using VIBE.

## Features Available

- ✅ Register / Login / Logout
- ✅ Forgot Password (OTP logged to console in dev)
- ✅ Phone Verification (OTP logged to console in dev)
- ✅ TikTok-style Video Feed (real posts from backend)
- ✅ Discover page with live hashtag search
- ✅ WhatsApp-style Messenger with real conversations
- ✅ Image, video, and voice note sending in chat
- ✅ Real-time typing indicator (WebSocket/STOMP)
- ✅ Live audio Rooms
- ✅ Token Wallet (real balance, real cashout)
- ✅ Weekly Leaderboard (auto-refreshes every 60s)
- ✅ Follow / Unfollow other users
- ✅ Status / Stories with 24h TTL
- ✅ AI Translation (11 African languages + major world languages)
- ✅ AI Chat Summarisation
- ✅ Real-time Notifications (WebSocket push)
- ✅ Media upload (images, videos, voice notes)

## Monitoring

| Dashboard | URL | Login |
|-----------|-----|-------|
| Kafka UI | http://localhost:8080 | (none) |
| Mongo Express | http://localhost:8081 | admin / vibe2026 |
| Prometheus | http://localhost:9090 | (none) |
| Grafana | http://localhost:3001 | admin / vibe_grafana_2026 |

## Troubleshooting

**Services won't start?**
```bash
docker-compose logs auth-service   # see specific service logs
docker-compose restart auth-service
```

**Database connection errors?**
```bash
# Check databases are healthy
docker-compose ps | grep -E "postgres|mongo|redis"
# Restart just the databases
docker-compose restart postgres-auth postgres-rewards mongo redis
```

**Port already in use?**
```bash
# Find what's using port 8090
lsof -i :8090   # macOS/Linux
netstat -ano | findstr 8090   # Windows
```

**Full reset:**
```bash
docker-compose down
docker volume ls | grep vibe | awk '{print $2}' | xargs docker volume rm 2>/dev/null || true
docker-compose up --build -d
```
