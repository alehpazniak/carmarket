# ─────────────────────────────────────────────────────────────────────────────
# user-service — standalone build
# Build:   docker build -t carmarket/user-service .
# Run:     docker run -p 8082:8082 carmarket/user-service
# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:17-jdk-alpine AS deps
WORKDIR /build
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q

FROM deps AS builder
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]