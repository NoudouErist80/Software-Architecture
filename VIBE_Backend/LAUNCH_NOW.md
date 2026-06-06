# VIBE — Launch Now (TL;DR)

```bash
# Terminal 1 — Backend
cd VIBE_Backend_UPDATED
docker-compose up --build -d
# Wait ~90 seconds

# Terminal 2 — Frontend
cd vibe-frontend-v2
npm install && npm run dev
```

Open **http://localhost:3000** — register an account and start using VIBE.

**Passwords:** PostgreSQL & MongoDB = `vibe_local_2026` | Redis = `vibe_redis`

**Add Claude AI key** (optional): edit `VIBE_Backend_UPDATED/.env` → set `CLAUDE_API_KEY`
