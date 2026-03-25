Step 1 → eureka-server        (service registry — everything registers here)
Step 2 → api-gateway          (single entry point — routes to services)
Step 3 → auth-service         (login, JWT — needed before any protected endpoint)
Step 4 → user-service         (profiles — created automatically after login)
Step 5 → car-service          (core business logic — CRUD listings)
Step 6 → search-service       (reads from Kafka — depends on car-service events)

Step 7 → avtovo-frontend       (npm.cmd run dev --cmd or npm run dev --powershell)

# 🚗 CarMarket — Java Microservices

A production-grade car marketplace backend built with Spring Boot 3 microservices architecture.

---

## Architecture Overview

```
Client (Browser / Mobile App)
        │
        │  HTTPS
        ▼
┌─────────────────────────────┐
│       API Gateway :8080     │  ← JWT validation (stateless)
│   Spring Cloud Gateway      │  ← Rate limiting (Redis)
│                             │  ← Circuit breaker (Resilience4j)
│                             │  ← Request logging
└────────────┬────────────────┘
             │  Eureka service discovery (lb://)
    ┌────────┴──────────────────────────┐
    │                                   │
    ▼                                   ▼
┌──────────────┐              ┌──────────────────┐
│ auth-service │              │   user-service   │
│   :8081      │              │     :8082        │
│              │              │                  │
│ Google OAuth2│              │ User profiles    │
│ Facebook     │              │ PostgreSQL       │
│ JWT issue    │              └──────────────────┘
│ Refresh/     │
│ Revoke       │              ┌──────────────────┐
│ PostgreSQL   │              │   car-service    │
│ Redis        │              │     :8083        │
└──────┬───────┘              │                  │
       │                      │ Car listings CRUD│
       │ Kafka                │ PostgreSQL       │
       │ user.registered      └────────┬─────────┘
       ▼                               │
┌─────────────────────────────┐        │ Kafka
│     user-service            │        │ car.created
│  Kafka consumer             │        │ car.updated
│  Auto-creates profile       │        │ car.deleted
└─────────────────────────────┘        ▼
                              ┌──────────────────┐
                              │  search-service  │
                              │     :8084        │
                              │                  │
                              │ Elasticsearch    │
                              │ Full-text search │
                              │ Filter/facets    │
                              └──────────────────┘

Infrastructure:
  Eureka Server    :8761   — Service registry
  PostgreSQL x3    :5433-35 — One DB per service
  Redis            :6379   — Refresh token store + rate limiter
  Kafka            :9092   — Async event bus
  Elasticsearch    :9200   — Search index
  Kafka UI         :9080   — Dev monitoring
  Kibana           :5601   — ES monitoring
```

---

## Services

| Service | Port | Description |
|---|---|---|
| **eureka-server** | 8761 | Service discovery (Eureka) |
| **api-gateway** | 8080 | Single entry point — JWT, routing, rate limit |
| **auth-service** | 8081 | OAuth2 (Google+Facebook), JWT issue/refresh |
| **user-service** | 8082 | User profiles, auto-created via Kafka |
| **car-service** | 8083 | Car listings CRUD, publishes Kafka events |
| **search-service** | 8084 | Elasticsearch full-text + filter search |

---

## Authentication Flow

```
1. Client opens browser:
   GET http://localhost:8080/api/auth/oauth2/authorization/google

2. Gateway routes to auth-service
   → Spring Security redirects to Google OAuth2 consent screen

3. User grants permission → Google redirects to:
   GET /api/auth/oauth2/callback/google?code=xxx

4. auth-service exchanges code for Google user info
   → Find or create user in PostgreSQL
   → If new user: publish user.registered to Kafka
   → Issue access_token (15min JWT) + refresh_token (7 days, stored in Redis)

5. Redirect to frontend with tokens:
   http://localhost:3000/auth/callback?access_token=xxx&refresh_token=yyy

6. Frontend stores tokens, attaches access_token as:
   Authorization: Bearer <access_token>

7. API Gateway validates JWT on every request:
   - Signature verification (no auth-service call)
   - Expiry check
   - Injects X-User-Id, X-User-Email, X-User-Roles headers
   - Downstream services read X-User-Id for identity
```

---

## Quick Start (Docker Compose)

### 1. Setup environment
```bash
cp .env.example .env
# Edit .env with your Google + Facebook OAuth2 credentials
```

### 2. Get OAuth2 credentials

**Google:**
1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Create OAuth2 Client ID (Web application)
3. Add Authorized redirect URI: `http://localhost:8081/auth/oauth2/callback/google`
4. Copy Client ID + Secret to `.env`

**Facebook:**
1. Go to [Facebook Developers](https://developers.facebook.com/apps)
2. Create App → Add Facebook Login product
3. Add Valid OAuth Redirect URI: `http://localhost:8081/auth/oauth2/callback/facebook`
4. Copy App ID + Secret to `.env`

### 3. Start the stack
```bash
docker compose up -d
```

### 4. Check health
```bash
# Eureka dashboard
open http://localhost:8761   # admin / admin

# Kafka UI (monitor topics + messages)
open http://localhost:9080

# Kibana (Elasticsearch UI)
open http://localhost:5601
```

---

## API Reference

All requests go through `http://localhost:8080` (API Gateway).

### Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/auth/oauth2/authorization/google` | ❌ | Start Google login |
| GET | `/api/auth/oauth2/authorization/facebook` | ❌ | Start Facebook login |
| POST | `/api/auth/refresh` | ❌ | Refresh access token |
| POST | `/api/auth/logout` | ❌ | Revoke refresh token |
| POST | `/api/auth/logout-all` | ✅ | Revoke all sessions |
| GET | `/api/auth/me` | ✅ | Current user info |

**Refresh Token Request:**
```json
POST /api/auth/refresh
{ "refresh_token": "eyJ..." }
```

**Logout Request:**
```json
POST /api/auth/logout
{ "refresh_token": "eyJ..." }
```

---

### Cars

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/cars?page=0&size=20` | ❌ | List active listings |
| GET | `/api/cars/{id}` | ❌ | Get single listing |
| GET | `/api/cars/my` | ✅ | My listings |
| POST | `/api/cars` | ✅ | Create listing |
| PUT | `/api/cars/{id}` | ✅ | Update listing |
| PATCH | `/api/cars/{id}/sold` | ✅ | Mark as sold |
| DELETE | `/api/cars/{id}` | ✅ | Remove listing |

**Create Car Request:**
```json
POST /api/cars
Authorization: Bearer <access_token>

{
  "make": "Toyota",
  "model": "Corolla",
  "year": 2020,
  "price": 15000.00,
  "mileage": 45000,
  "fuelType": "PETROL",
  "transmission": "AUTOMATIC",
  "color": "White",
  "description": "Well maintained, one owner, full service history",
  "city": "Warsaw",
  "country": "Poland"
}
```

---

### Search

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/search` | ❌ | Full-text + filter search |
| GET | `/api/search/{id}` | ❌ | Get car from ES index |

**Search Examples:**
```bash
# Full-text search
GET /api/search?query=toyota corolla

# Filter by make + price range
GET /api/search?make=Toyota&priceFrom=5000&priceTo=20000

# Combined filters
GET /api/search?make=BMW&yearFrom=2018&fuelType=DIESEL&city=Warsaw&sort=price,asc

# Pagination
GET /api/search?query=sedan&page=0&size=20
```

---

### Users

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/users/me` | ✅ | Get my profile |
| PATCH | `/api/users/me` | ✅ | Update my profile |
| GET | `/api/users/{id}` | ❌ | Get public profile |

---

## Kafka Topics

| Topic | Producer | Consumer | Payload |
|---|---|---|---|
| `user.registered` | auth-service | user-service | userId, email, displayName, role |
| `car.created` | car-service | search-service | carId, make, model, price, … |
| `car.updated` | car-service | search-service | same as created |
| `car.deleted` | car-service | search-service | carId |

---

## Project Structure

```
carmarket/
├── pom.xml                  # Parent POM — version management
├── docker-compose.yml       # Full local dev stack
├── .env.example             # Environment variable template
│
├── eureka-server/           # Service discovery
├── api-gateway/             # Gateway: JWT + routing + rate limiting
│   └── filter/
│       └── JwtAuthenticationFilter.java   ← core JWT logic
│
├── auth-service/            # OAuth2 + JWT issuance
│   ├── security/
│   │   ├── JwtTokenProvider.java          ← token generation
│   │   └── OAuth2SuccessHandler.java      ← post-login redirect
│   ├── service/
│   │   ├── AuthService.java               ← OAuth2 + token logic
│   │   └── RefreshTokenService.java       ← Redis refresh tokens
│   └── entity/
│       ├── User.java
│       └── OAuthProvider.java             ← multi-provider linking
│
├── user-service/            # User profiles
│   └── kafka/UserEventConsumer.java       ← auto-create profile
│
├── car-service/             # Listings CRUD
│   └── kafka/CarEventProducer.java        ← publish to search
│
├── search-service/          # Elasticsearch search
│   ├── kafka/CarIndexingConsumer.java     ← consume + index
│   └── service/CarSearchService.java      ← dynamic criteria query
│
└── k8s/                     # Kubernetes manifests
    ├── infra/
    │   ├── namespace.yml
    │   └── ingress.yml
    ├── services/
    │   ├── eureka-server.yml
    │   ├── api-gateway.yml
    │   ├── auth-service.yml
    │   ├── car-service.yml
    │   └── search-service.yml
    └── configmaps/
        ├── common-config.yml
        └── secrets-template.yml
```

---

## Security Design

### JWT Token Strategy
- **Access token**: 15 minutes, contains userId + email + roles
- **Refresh token**: 7 days, contains userId + JTI (unique ID)
- **Storage**: Refresh tokens stored in Redis with TTL = expiry
- **Rotation**: Each refresh call revokes old token and issues new pair
- **Revocation**: Logout deletes key from Redis; gateway can't see revoked tokens until access token expires (15min max exposure)

### Gateway JWT Validation (Stateless)
```
Request → Gateway reads Authorization: Bearer <token>
         → Verify HMAC-SHA256 signature (shared secret, no DB call)
         → Check expiry
         → Extract userId, roles
         → Inject X-User-Id, X-User-Roles headers
         → Forward to downstream service
```

### Multi-Provider Account Linking
- User logs in with Google → account created
- Same user logs in with Facebook (same email) → providers linked to same account
- Stored in `oauth_providers` table with `(provider, provider_user_id)` unique constraint

---

## Kubernetes Deployment

```bash
# Create namespace
kubectl apply -f k8s/infra/namespace.yml

# Apply secrets (fill in real values first!)
kubectl apply -f k8s/configmaps/secrets-template.yml
kubectl apply -f k8s/configmaps/common-config.yml

# Deploy services (in order)
kubectl apply -f k8s/services/eureka-server.yml
kubectl apply -f k8s/services/api-gateway.yml
kubectl apply -f k8s/services/auth-service.yml
kubectl apply -f k8s/services/car-service.yml
kubectl apply -f k8s/services/search-service.yml

# Apply ingress
kubectl apply -f k8s/infra/ingress.yml

# Check pods
kubectl get pods -n carmarket
```

---

## Next Steps / Roadmap

- [ ] **Flyway migrations** — replace `ddl-auto: update` with versioned migrations
- [ ] **Image upload** — S3/MinIO for car photos (car-service)
- [ ] **Notifications** — email/push on sale, new message (notification-service)
- [ ] **Messaging** — buyer ↔ seller chat (WebSocket)
- [ ] **CI/CD** — GitHub Actions: test → build Docker images → push to registry → deploy
- [ ] **Distributed tracing** — Micrometer + Zipkin/Jaeger
- [ ] **Metrics** — Prometheus + Grafana dashboards
- [ ] **API versioning** — `/api/v1/cars`
- [ ] **Admin service** — moderation, user management