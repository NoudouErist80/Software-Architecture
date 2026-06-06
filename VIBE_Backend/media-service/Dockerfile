# ── Stage 1: Build ─────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy root pom
COPY pom.xml /build/pom.xml
# Copy shared common module
COPY shared/vibe-common /build/shared/vibe-common
# Copy this service
COPY media-service /build/media-service

# Build shared module first, then the service
RUN mvn -f /build/shared/vibe-common/pom.xml clean install -DskipTests -q && \
    mvn -f /build/media-service/pom.xml clean package -DskipTests -q

# ── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S vibe && adduser -S vibe -G vibe
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /build/media-service/target/*.jar app.jar
RUN chown vibe:vibe app.jar
USER vibe
EXPOSE 8088
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
