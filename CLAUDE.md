# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

CarMarket (frontend name "avtovo") is a Spring Boot 3 / Java 17 microservices car marketplace, plus a React 19 + TypeScript + Vite frontend. Services discover each other via Eureka and talk synchronously through the API Gateway, and asynchronously via Kafka events.

## Build & run

Each service module has its **own standalone `pom.xml`** (`parent: spring-boot-starter-parent`), independent of the root `pom.xml`. The root `pom.xml` is only a convenience aggregator (`<modules>`) for building everything at once — it is *not* the parent POM of the services. This matters because each service's `Dockerfile` builds from that service's own `pom.xml` in isolation (`context: ./<service>`), so a module must compile standalone, not rely on state only defined by a sibling module.

```bash
# Build everything (from repo root)
mvn clean install -DskipTests

# Build/test one service (from repo root or the service dir)
mvn -pl car-service clean verify
cd car-service && mvn test

# Run a single test class / method
mvn -pl car-service test -Dtest=CarListingServiceTest
mvn -pl car-service test -Dtest=CarListingServiceTest#shouldCreateListing

# Frontend (avtovo-frontend/)
npm run dev       # vite dev server, expects gateway at :8080 (see .env.example)
npm run build      # tsc -b && vite build
npm run lint
```

### Local stack (Docker Compose)

```bash
cp .env.example .env                              # fill OAuth2 creds, etc.
docker compose up -d                               # everything
docker compose up -d --build --no-deps car-service  # rebuild+restart ONE service
docker compose down -v                              # wipe all data/volumes
```
Each service is independently buildable/rebuildable in compose — see the inline comments in `docker-compose.yml` above each service block. Note `car-service` requires `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` and `auction-import-service` requires `APIBARA_API_KEY` (compose fails fast with `:?` if unset).

Health/monitoring UIs when the stack is up: Eureka `:8761` (admin/admin), Kafka UI `:9080`, Kibana `:5601`, MailHog `:8025` (catches all outbound email locally).

## Service map

| Service | Port | Owns | Role |
|---|---|---|---|
| eureka-server | 8761 | — | Service registry (all services register here) |
| api-gateway | 8080 | — | Single client entry point: JWT validation, routing (`lb://`), rate limiting (Redis), circuit breaking |
| auth-service | 8081 | postgres-auth, Redis | Google/Facebook OAuth2, JWT issue/refresh/revoke |
| user-service | 8082 | postgres-user | User profiles, auto-created from `user.registered` |
| car-service | 8083 | postgres-car, S3 | Car listings CRUD, favorites, publishes `car.*` events |
| search-service | 8084 | Elasticsearch | Full-text/filter search, indexed from Kafka |
| chat-service | 8085 | postgres-chat | Buyer↔seller WebSocket/STOMP chat, publishes `chat.*` events |
| notification-service | 8086 | postgres-notification | Consumes `user.*`/`chat.*` events, sends email (via MailHog locally) |
| auction-import-service | 8086* | postgres-auction | US auto-auction (Copart/Apibara) import-cost calculator, currency & shipping rates, analytics |

\* `auction-import-service` and `notification-service` both default to container port 8086 in `docker-compose.yml` — check the compose file if running both together.

Every backend service module lives at `<service>/src/main/java/com/carmarket/<name>/...`.

## Request flow & cross-cutting security

All client traffic enters through **api-gateway**. Two layers of trust are chained:

1. **Client → Gateway (JWT)**: `JwtAuthenticationFilter` (gateway) validates the JWT (HMAC-SHA256, stateless, no DB call) on every non-public route (`/api/auth/**`, `/api/search/**`, `/actuator/**`, `/eureka/**` are public). It extracts `userId`/`roles` from the token and injects `X-User-Id` / `X-User-Roles` headers downstream.
2. **Gateway → Service (HMAC gateway signature)**: the gateway additionally signs the request with `GatewaySignatureService`, producing an `X-Gateway-Signature: <timestamp>:<hmac>` header from `gateway.internal-secret`. Each downstream service (`car-service`, `user-service`, `auction-import-service`, ...) has its own `GatewaySignatureFilter` (`OncePerRequestFilter`) that recomputes the HMAC and rejects the request if it doesn't match or is older than 30s (replay protection). **This means a service's REST endpoints only trust `X-User-Id`/`X-User-Roles` if the request also carries a valid, fresh gateway signature — services must never trust those headers if called directly, bypassing the gateway.** When adding a new backend service that needs authenticated identity, copy this `GatewaySignatureFilter` pattern rather than trusting `X-User-Id` directly.

Both secrets (`JWT_SECRET`, `gateway.internal-secret`) are shared across services via env vars — see `.env.example` / `docker-compose.yml`.

## Async event flow (Kafka)

Topics are named `<domain>.<event>`, auto-created (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` in dev).

| Topic | Producer | Consumer(s) |
|---|---|---|
| `user.registered` | auth-service | user-service (creates profile), notification-service (welcome email) |
| `car.created` / `car.updated` / `car.deleted` | car-service | search-service (indexes/removes from Elasticsearch) |
| `chat.message.sent` | chat-service | notification-service (delayed "you have a new message" email, see `CHAT_MESSAGE_EMAIL_DELAY`) |
| `chat.conversation.read` | chat-service | notification-service (cancels/suppresses pending email) |

When changing an event's payload shape, update the DTO in **both** the producing and consuming service — there's no shared event-schema module; each service has its own copy of the event DTO (e.g. `car-service/.../dto/CarUpdatedEvent.java` and `search-service/.../dto/CarUpdatedEvent.java`).

## Database migrations

Services use Flyway (`src/main/resources/db/migration/V<N>__<Name>.sql`). `car-service`, `auth-service`, and `user-service` run Flyway alongside Hibernate `ddl-auto: update` (dev convenience); `notification-service` and `auction-import-service` use `ddl-auto: validate`, meaning **schema changes must go through a new Flyway migration** or the app fails to start. When adding/changing a JPA entity in any service, add a new `V<N+1>__...sql` migration file rather than relying on Hibernate to auto-update — follow the existing numbering per service (each service's migrations are independently numbered, one Postgres DB per service).

One Postgres instance per service (`postgres-auth`, `postgres-user`, `postgres-car`, `postgres-chat`, `postgres-notification`, `postgres-auction`) — there is no shared database or cross-service SQL join; cross-service data access always goes through REST (via gateway) or Kafka.

## Frontend (avtovo-frontend/)

React 19 + TypeScript + Vite + Tailwind, talking to the API Gateway and directly to chat-service's WebSocket.

- `src/api/` — axios clients per domain (`cars.ts`, `chat.ts`, `auctions.ts`), `client.ts` is the shared axios instance (base URL from `VITE_API_URL`).
- Chat uses STOMP over WebSocket (`@stomp/stompjs`) directly against `VITE_CHAT_WS_URL` (chat-service `:8085`), not through the gateway.
- Env vars are `VITE_*`, templated in `avtovo-frontend/.env.example`.

## auction-import-service specifics

This is the most complex module — a US-auction (Copart, via the Apibara API) import-cost estimator, not a simple CRUD service:
- `parser/` — `AuctionParser` implementations (`CopartParser`, `ApibaraAuctionParser`) plus `ProxyRotationService` and a Playwright-backed config for scraping.
- `service/calculator/` — `ImportCostCalculator` and `MaxBidCalculator` compute landed cost / max recommended bid from `CalculationInput`, using `ExciseRateResolver`, `CurrencyExchangeService`, and cached USA-to-port shipping rates (`UsaShippingRateService`/`UsaShippingRateCacheWriter`).
- `analytics/` — separate aggregation of `VehicleStats` for reporting, distinct from the calculator flow.
- `scheduler/LotSyncScheduler` periodically pulls/refreshes auction lots in the background.

## Kubernetes

`README.md` documents a `k8s/` manifest layout (`infra/`, `services/`, `configmaps/`) and apply order, but that directory does not currently exist in the repo — treat it as aspirational/roadmap, not present state.
