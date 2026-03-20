# ─────────────────────────────────────────────────────────────────────────────
# eureka-server — standalone build
# Build:   docker build -t carmarket/eureka-server .
# Run:     docker run -p 8761:8761 carmarket/eureka-server
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Download dependencies (cached layer) ────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS deps
WORKDIR /build

# Copy only pom.xml first — Maven deps layer is rebuilt only when pom.xml changes
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q

# ── Stage 2: Build ────────────────────────────────────────────────────────────
FROM deps AS builder
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 3: Runtime (minimal JRE image) ─────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]