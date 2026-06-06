# Running Services Individually (Advanced)

> **Recommended: use `docker-compose up --build -d` to start everything at once.**  
> This guide is only for debugging a single service outside Docker.

## Prerequisites

All databases must still be running in Docker:
```bash
docker-compose up -d postgres-auth postgres-rewards mongo redis kafka
```

## Running a Service with Maven

```bash
cd VIBE_Backend_UPDATED/<service-name>
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or build and run the JAR:
```bash
mvn clean package -DskipTests
java -jar target/<service-name>-*.jar
```

## Service Ports

| Service | Command | Port |
|---------|---------|------|
| api-gateway | `cd api-gateway && mvn spring-boot:run` | 8090 |
| auth-service | `cd auth-service && mvn spring-boot:run` | 8081 |
| messaging-service | `cd messaging-service && mvn spring-boot:run` | 8082 |
| feed-service | `cd feed-service && mvn spring-boot:run` | 8083 |
| rooms-service | `cd rooms-service && mvn spring-boot:run` | 8084 |
| rewards-service | `cd rewards-service && mvn spring-boot:run` | 8085 |
| notification-service | `cd notification-service && mvn spring-boot:run` | 8086 |
| ai-service | `cd ai-service && mvn spring-boot:run` | 8087 |
| media-service | `cd media-service && mvn spring-boot:run` | 8088 |

## Environment Variables (running outside Docker)

When running outside Docker, override the hostnames:
```bash
export SPRING_DATA_MONGODB_URI="mongodb://vibe:vibe_local_2026@localhost:27017/vibe_feed?authSource=admin"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/vibe_auth"
export SPRING_REDIS_HOST=localhost
```
