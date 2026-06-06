#!/bin/bash
# VIBE Platform — Quick Start Script for Local Development
# Author: TCHANGO NOUDOU JOSEPH
# Usage: ./start-dev.sh

echo "🚀 Starting VIBE Platform local development environment..."
echo ""

# Check Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi

# Start infrastructure
echo "📦 Starting infrastructure containers (PostgreSQL, MongoDB, Redis, Kafka)..."
docker compose up -d postgres-auth postgres-rewards mongodb redis kafka zookeeper

# Wait for databases to be ready
echo "⏳ Waiting for databases to be ready (30 seconds)..."
sleep 30

# Check all containers are healthy
echo ""
echo "📊 Container status:"
docker compose ps

echo ""
echo "✅ Infrastructure ready! Now start your services:"
echo ""
echo "  Terminal 1 (Auth):      cd auth-service && mvn spring-boot:run"
echo "  Terminal 2 (Messaging): cd messaging-service && mvn spring-boot:run"
echo "  Terminal 3 (Rewards):   cd rewards-service && mvn spring-boot:run"
echo ""
echo "📖 Swagger UIs:"
echo "  Auth:      http://localhost:8081/swagger-ui.html"
echo "  Messaging: http://localhost:8082/swagger-ui.html"
echo "  Rewards:   http://localhost:8085/swagger-ui.html"
echo ""
echo "🗄️  Connect DBeaver to:"
echo "  PostgreSQL Auth:    localhost:5432 / vibe_auth"
echo "  PostgreSQL Rewards: localhost:5433 / vibe_rewards"
echo "  MongoDB:            localhost:27017 / vibe_messaging"
