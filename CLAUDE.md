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
| car-service | 8083 | postgres-car, S3 | Car listings CRUD, favorites, paid promotions; publishes `car.*`, consumes `payment.*` |
| search-service | 8084 | Elasticsearch | Full-text/filter search, indexed from Kafka |
| chat-service | 8085 | postgres-chat | Buyer↔seller WebSocket/STOMP chat, publishes `chat.*` events; REST needs the gateway signature, `/ws` checks the JWT itself |
| notification-service | 8087 | postgres-notification | Consumes `user.*`/`chat.*`/`listing.moderated`/`import-order.*` events, sends email (via MailHog locally); its only HTTP API is `/notifications/admin/**` (ADMIN, everything else denied) |
| auction-import-service | 8086 | postgres-auction | US auto-auction (Copart/Apibara) import-cost calculator, currency & shipping rates, analytics |
| payment-service | 8088 | postgres-payment | Przelewy24 payments: register → P24 page → `urlStatus` webhook → verify; publishes `payment.*` events |
| import-order-service | 8089 | postgres-import-order, S3 (private bucket) | Dealer import orders for US-auction cars: status timeline, documents/photos, password-protected client page `/zlecenie/<code>` |
| admin-service | 8090 | postgres-admin (5441) | Admin panel backend: audit log (consumes `admin.audit`), system health of every Eureka instance; everything ADMIN only |

Every backend service module lives at `<service>/src/main/java/com/carmarket/<name>/...`.

## Request flow & cross-cutting security

All client traffic enters through **api-gateway**. Two layers of trust are chained:

1. **Client → Gateway (JWT)**: `JwtAuthenticationFilter` (gateway) validates the JWT (HMAC-SHA256, stateless, no DB call) on every non-public route (`/api/auth/**`, `/api/search/**`, `/actuator/**`, `/eureka/**` are public, plus a few method-specific ones in `isPublicRoute`, e.g. GET/POST `/api/import-orders/public/**`, which import-order-service secures itself, and GET `/api/users/dealers/**`, the public dealer pages). **The first rule in `isPublicRoute` makes `/api/admin/**` and `/api/*/admin/**` non-public whatever the method**, before `GET /api/cars/**` and `/api/auth/**`; `/api/auth/logout-all`, `/api/auth/me` and `/api/cars/my/**` are non-public too. It extracts `userId`/`roles` from the token and injects `X-User-Id` / `X-User-Roles` headers downstream.
2. **Gateway → Service (HMAC gateway signature)**: the gateway additionally signs the request with `GatewaySignatureService`, producing an `X-Gateway-Signature: <timestamp>:<hmac>` header from `gateway.internal-secret`. Each downstream service (`car-service`, `user-service`, `auction-import-service`, ...) has its own `GatewaySignatureFilter` (`OncePerRequestFilter`) that recomputes the HMAC and rejects the request if it doesn't match or is older than 30s (replay protection). **This means a service's REST endpoints only trust `X-User-Id`/`X-User-Roles` if the request also carries a valid, fresh gateway signature — services must never trust those headers if called directly, bypassing the gateway.** When adding a new backend service that needs authenticated identity, copy this `GatewaySignatureFilter` pattern rather than trusting `X-User-Id` directly.

auth-service has a `GatewaySignatureFilter` as well: OAuth2, refresh, logout and dealer-upgrade (which checks the bearer token itself) stay public, while `/auth/logout-all`, `/auth/me` and `/auth/admin/**` need a signed caller. Note the signature covers only `userId:timestamp`, not the roles header.

Both secrets (`JWT_SECRET`, `gateway.internal-secret`) are shared across services via env vars — see `.env.example` / `docker-compose.yml`.

Client IP behind proxies: `GATEWAY_TRUSTED_PROXIES` (gateway's IP-keyed rate limits, via `ClientIpResolver`) and `IMPORT_ORDER_TRUSTED_PROXIES` (import-order access log, `ClientRequestInfo`) are the number of reverse proxies in front of the gateway: 0 locally (direct remote address, client-sent `X-Forwarded-For` ignored), and in a deployed environment the actual proxy count, otherwise every client shares the load balancer's IP and rate-limit bucket.

## Async event flow (Kafka)

Topics are named `<domain>.<event>`, auto-created (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` in dev).

| Topic | Producer | Consumer(s) |
|---|---|---|
| `user.registered` | auth-service | user-service (creates profile), notification-service (welcome email) |
| `user.dealer-upgrade.requested` | auth-service | notification-service (emails the USER→DEALER confirmation link) |
| `user.role-changed` | auth-service (dealer upgrade, admin panel, `ADMIN_EMAILS`) | user-service (updates profile role) |
| `user.blocked` / `user.unblocked` | auth-service (admin panel) | user-service (`profile.active`), car-service (ACTIVE ↔ SUSPENDED listings), chat-service (`blocked_users`) |
| `car.created` / `car.updated` / `car.deleted` | car-service | search-service (indexes/removes from Elasticsearch) |
| `chat.message.sent` | chat-service | notification-service (delayed "you have a new message" email, see `CHAT_MESSAGE_EMAIL_DELAY`) |
| `chat.conversation.read` | chat-service | notification-service (cancels/suppresses pending email) |
| `payment.completed` / `payment.refunded` | payment-service | car-service (applies/reverts `LISTING_PROMOTION` / `LISTING_HIGHLIGHT`, see below) |
| `import-order.client-update` | import-order-service | notification-service (batched "new information on your order" email to the order's client, see below) |
| `listing.moderated` | car-service (admin block/unblock) | notification-service (`LISTING_BLOCKED` / `LISTING_RESTORED` email to the seller; skipped with a WARN when the seller has no `Recipient` row) |
| `admin.audit` | every service with admin writes (`AdminAuditPublisher`) | admin-service (`admin_audit_log`, idempotent by `eventId`) |

## Admin panel

- Admin endpoints live in the service that owns the data, under `/<prefix>/admin/**` (`/auth/admin/**`, `/cars/admin/**`, `/payments/admin/**`, `/api/auctions/admin/**`, ...), each `hasRole('ADMIN')` in that service's `SecurityConfig` (above any broader public rule). admin-service itself only holds the audit log and system health (`/api/admin/**` at the gateway → `/admin/**`).
- Every admin write publishes `admin.audit` after commit via the service's own `AdminAuditPublisher` copy (`eventId`, `occurredAt`, `actorId`, `actorEmail` from `X-User-Email`, `service`, `action`, `targetType`, `targetId`, `reason`, `details`). `details` is a small map: never secrets, tokens or whole documents. Automatic actions use actor `system`.
- Destructive actions need a reason of at least 5 characters, in the API and in the UI's `ConfirmDialog`.
- First admins: `ADMIN_EMAILS` (auth-service `app.admin.bootstrap-emails`, comma-separated) are promoted to ADMIN on login (`AdminBootstrapService`, publishes `user.role-changed`, audited as `system`). Never demotes.
- admin-service's `GET /admin/system/health` asks every Eureka instance for `/actuator/health` (2 s timeout, in parallel); services in `admin.system.expected-services` without an instance are reported DOWN. Every service therefore needs `spring-boot-starter-actuator` and `/actuator/**` permitted.
- No impersonation and no reading of private chats: chat-service only exposes `GET /chat/admin/stats` (counts).
- Per-service admin APIs: payment-service `/payments/admin/payments` (+ `.csv`: `;`-separated, UTF-8 with BOM, decimal comma, ≤ 10,000 rows, `X-Export-Truncated`; the export is audited as `PAYMENTS_EXPORTED`) and `/payments/admin/stats`; refunds (`POST /payments/{id}/refund`) need a reason. import-order-service: admins filter `GET /import-orders?dealerId=` and read `/import-orders/admin/stats`; they can still only change their own orders, and the dealer cabinet page is read-only for someone else's. auction-import-service `/api/auctions/admin/status` (Apibara usage, last `sync_runs` per parser, last exchange rate) and `POST /api/auctions/admin/sync` (async, 409 while a sync runs; the scheduled sync is skipped meanwhile). notification-service `/notifications/admin/notifications` (+ `/{id}/retry`: FAILED → PENDING, attempts reset) and `/notifications/admin/stats`; the gateway routes only `/api/notifications/admin/**` there.
- The dashboard (Pulpit) loads one tile per service in parallel; a service that is down only blanks its own tile.
- Users (auth-service `/auth/admin/**`): role change, block/unblock, logout everywhere, dealer requests, stats. An admin can't change their own role or block themselves, and the last active ADMIN can't be demoted or blocked (409, checked under a row lock on the active admins). Blocked accounts can't log in (OAuth redirects to `/auth/callback?error=blocked`) or refresh (403).
- **Immediate effect:** access tokens live 15 min, so auth-service writes two keys into the shared Redis after commit: `auth:blocked:{userId}` (no TTL, set on block, deleted on unblock) and `auth:min-iat:{userId}` (epoch seconds, TTL = access-token TTL + 1 min; set on role change, block and admin logout). The gateway's `AccessRevocationChecker` reads both with one MGET per authenticated request: blocked → 401 `Account blocked`, token `iat` < min-iat → 401 `Token revoked` (the frontend's refresh interceptor then gets a token with the new role, or 403 → "Konto zablokowane"). Redis errors or > 500 ms **fail open** with a WARN, like the rate limiter; blocked users still can't refresh because auth-service checks the database. `iat` has second precision, so a token issued in the same second as the change survives until it expires.
- user-service `GET /users/admin/users/{id}` returns the full profile (also of deactivated accounts) and audits every read as `USER_PII_VIEWED`; the UI fetches it only on an explicit click.
- Frontend: `/admin/*` is `React.lazy` (`src/admin/AdminApp.tsx`) behind `AdminRoute`, so admin code and charts stay out of the main bundle. Shared pieces: `DataTable` (server-side paging/sort), `useQueryState` (filters in the URL), `SearchInput` (debounced), `ConfirmDialog` (required reason), toasts.

## payment-service (Przelewy24) specifics

- Credentials (`P24_MERCHANT_ID`, `P24_POS_ID`, `P24_CRC`, `P24_API_KEY`, `P24_SANDBOX`) are optional: without them the service starts and payment endpoints answer 503.
- Prices live in `application.yml` under `payment.products`; the client sends only `productCode` + `referenceId`, never an amount.
- Products with `requires-own-listing: true` (the two listing products) only accept a `referenceId` that is the UUID of the buyer's own ACTIVE listing; `create` checks it via car-service `GET /cars/{id}` (`CarListingClient`, load-balanced through Eureka). `duration-days` is returned by `/payments/products` and sent as `durationDays` in `payment.*` events.
- A payment is `PAID` only after `transaction/verify` succeeds. That happens either in the `POST /payments/p24/notify` webhook (public at the gateway, authenticated by the SHA-384 `sign`) or in `POST /payments/{id}/sync`, which the frontend return page `/platnosc/:id` calls. On localhost P24 can't reach the webhook, so sync is what completes the payment there.
- The sign is SHA-384 over JSON with a fixed field order (`Przelewy24SignCalculator`), and the order differs between register, verify and notification.

## user-service profile privacy

- `GET /users/{id}` (public, `permitAll`, port 8082 is published) returns `PublicUserProfileResponse`, an explicit allow-list (`id`, `displayName`, `avatarUrl`, `city`, `bio`, `role`, `memberSince`) built by a hand-written `from()`, not MapStruct, so new entity fields never leak. Missing or deactivated profiles are 404. Only `/users/me` returns the full `UserProfileResponse` (email, phone, address).
- A seller's phone is only available via `GET /users/{id}/phone?carId=` (gateway signature required): `SellerPhoneService` asks car-service (`CarListingClient`, load-balanced like payment-service's) that the listing exists, is `ACTIVE` and belongs to `{id}`. Every failed check is the same bare 404; car-service down → 503. Requests are audit-logged without the number.
- The gateway routes `/api/users/*/phone` separately (above the generic user routes) with `phoneRateLimiter` (10 burst, then 2/min per user) under its own `phone:<userId>` key: `RedisRateLimiter` keys buckets by the resolved key only, not per route, so sharing the user-id key would share the standard limiter's bucket. Limiters are injected by `@Qualifier` because `@Primary` beats parameter-name matching.

## Dealer pages (otomoto-style)

- Public `/dealer/:slug` (frontend) with tabs **O nas** (`/dealer/:slug`, only when the dealer wrote something; otherwise that path shows the offers), **Oferta** (`/oferta`) and **Kontakt** (`/kontakt`), modeled on otomoto's dealer pages. The dealer edits it at `/panel-dealera/strona`; listing pages show a dealer box (`DealerBox`) linking to it.
- user-service `DealerPage` (`dealer_pages`, ddl-auto, JSON columns for gallery, opening hours, services and team; `db/migration/V3` is documentation only). `GET /users/dealers/{slug}` (allow-listed `DealerPageView`, no phone, only `hasPhone`), `GET /users/dealers/{slug}/phone` ("Wyświetl numer"), `GET /users/dealers/by-user/{userId}` (the listing-page box), `GET|PUT /users/me/dealer-page`. A page is visible only while `published`, the profile is active and its role is DEALER or ADMIN — read from the profile, never from `X-User-Roles`, which the gateway signature doesn't cover. Slugs: `[a-z0-9-]{3,40}`, unique (409), a few reserved. `PublicUserProfileResponse.dealerSlug` is set for published pages.
- The gateway makes GET `/api/users/dealers/**` public and routes `/api/users/dealers/*/phone` through `dealerPhoneRateLimiter` keyed `dealer-phone:<ip>` (same rate as the seller phone, own bucket).
- Images (logo, banner, gallery ≤ 12) are uploaded through car-service `POST /cars/dealer-media` (DEALER/ADMIN, type from magic bytes, ≤ 5 MB) into the listing bucket under `dealers/{userId}/`; user-service only accepts URLs under the caller's own prefix, and `DELETE /cars/dealer-media?url=` only deletes the caller's own. The editor deletes images the saved page no longer uses.
- "Oferta" lists the dealer's ACTIVE listings from car-service `GET /cars/seller/{sellerId}` (public, sort limited to `createdAt`/`price`/`mileage`/`year`, page size ≤ 48).

## import-order-service specifics

- A DEALER ("autokomis") creates an import order for a lot won at Copart/IAAI; it belongs to that dealer (`dealer_id` = `X-User-Id`). Every dealer query goes through `OrderLookup`: someone else's order is a 404; ADMIN may read all orders but only change their own. Plain USER → 403.
- Create (and `POST /{id}/access/reset`) return the client link `<FRONTEND_URL>/zlecenie/<code>` and a password **once**: only the BCrypt hash (cost 12) is stored and the password is never logged. The dealer sends both to the client; the system never does.
- Client endpoints `/import-orders/public/{code}/**` are public at the gateway. `POST …/access` with the password returns an HS256 `X-Order-Token` signed with `IMPORT_ORDER_TOKEN_SECRET` (claims `sub` = order id, `ver` = `access_version`, `jti`, 12 h). Wrong password, unknown code, disabled or archived order all give the same 401 (an unknown code still runs a dummy BCrypt check); 10 failures lock the order for 15 min (429 + `Retry-After`, also `retryAfterSeconds` in the body because cross-origin JS only sees exposed headers). A reset bumps `access_version`, which kills existing tokens.
- `auction_url` (V2) is the lot page the dealer pasted, dealer-only (never in `ClientOrderView`), http(s) only; the frontend falls back to `copart.com/lot/{lot}` for Copart. Document category `CONTRACT` ("Umowa") needs no migration (`category` is a plain VARCHAR). The list (`OrderSummary`) carries `thumbnailUrl` (5-minute presigned URL of the first AUCTION-stage photo, else the first photo; one query per page) and `auctionPhotoUrl` (first snapshot photo, which the frontend loads through the auction image proxy).
- `ClientOrderView` is a hand-built allow-list (like `PublicUserProfileResponse`): never internal notes, internal event notes, hidden files, dealer id, access fields, purchase price or the client's contact data. `GET /{id}/client-preview` returns the same view to the dealer.
- `import_orders.status` is denormalized: the status of the event with the latest `occurred_at` (ties: latest `created_at`), recomputed whenever an event is added or deleted. Any status may be set (corrections).
- PATCH takes a JSON object: absent = unchanged, `null` = clear, unknown or server-owned field = 400 (`OrderFields`). Container numbers are normalized and must pass ISO 6346; a VIN with a wrong North-American check digit is saved with a warning.
- Storage: `import-order.s3.*` ← `IMPORT_ORDER_S3_BUCKET`, `IMPORT_ORDER_S3_REGION`, `IMPORT_ORDER_AWS_ACCESS_KEY_ID`, `IMPORT_ORDER_AWS_SECRET_ACCESS_KEY` (in the git-ignored `.env`, never in yml defaults). `S3Client`/`S3Presigner` use `StaticCredentialsProvider` only — never the default chain, which could pick up `~/.aws` or car-service's broader `AWS_*` keys. If any value is empty the beans aren't created and file endpoints answer 503 "Storage not configured"; everything else works. The bucket is private: downloads only via 5-minute presigned GET URLs (documents as attachment with the original name, photos inline), never public URLs.
- IAM scope: the IAM user may only Put/Get/Delete objects under `import-orders/*` and `ListBucket` with prefix `import-orders/`. Keys are `import-orders/{orderId}/{documents|photos}/{uuid}.{ext}`; deleting an order deletes the stored keys plus anything listed under its prefix (S3 first, then rows).
- Uploads are checked by magic bytes (PDF/JPEG/PNG/WEBP documents ≤ 20 MB, max 10 per upload; JPEG/PNG/WEBP photos ≤ 15 MB, max 30 per upload). The gateway routes `POST /api/import-orders/*/files` without a circuit breaker (its time limit would cut off large uploads) and `POST /api/import-orders/public/*/access` with `importOrderAccessRateLimiter` keyed `import-access:<ip>` (own bucket, see the phone limiter note above).
- Client emails: after commit, `ImportOrderEventProducer` publishes `import-order.client-update` (`orderId`, `clientEmail`, `clientName`, `dealerName`, `carTitle`, `link` — never the password) when a status event has a client note or changes the current status, or a document becomes client-visible (upload or toggle), and only if `notify_client` is set, there is a client email and access is open. notification-service upserts a synthetic `Recipient` (`UUID.nameUUIDFromBytes("import-order-client:" + orderId)`) and calls `NotificationService.scheduleBatched` (`IMPORT_ORDER_UPDATE`, dedup key = order id, `IMPORT_ORDER_UPDATE_EMAIL_DELAY`, 10 min): updates within the window fold into one email with the latest payload, and a SENT email is acknowledged on the next update so later bursts get their own email (plain `schedule` would block forever, since nothing "reads" these).
- Client uploads: `POST /import-orders/public/{code}/files` (X-Order-Token) lets the client send documents, e.g. a payment confirmation for the dealer's invoice. Categories `PAYMENT_CONFIRMATION`/`OTHER` only (`DocumentCategory.CLIENT_UPLOADABLE`), ≤ 5 per upload, ≤ 30 client files per order, always `client_visible`, `uploaded_by` null + `uploaded_by_client` true (V3), logged as UPLOAD, no client email. The gateway route has no circuit breaker and uses `importOrderAccessRateLimiter` keyed `import-upload:<ip>`.
- Access log: LOGIN_OK / LOGIN_FAILED / VIEW (at most once per token per hour) / UPLOAD, IP truncated to /24 (IPv4) or /48 (IPv6), user agent ≤ 200 chars.
- Tests boot the service against Postgres via Testcontainers (Docker must be running; `testcontainers.version` is pinned to 1.21.4 because 1.19.x can't talk to current Docker Desktop) with S3 mocked.

## car-service listing promotion

- `car_listings.promoted_until` / `highlighted_until` (V6, `CarListing.promotedUntil` / `highlightedUntil`) are set only by `ListingPromotionService` from `payment.completed`; `CarListingMapper` ignores them, so owners can't set them via `PUT /cars/{id}`. They're in `CarListingResponse` and `CarUpdatedEvent`.
- Purchases stack (`max(now, current) + durationDays`). Every processed payment is a row in `listing_promotion_payments` (idempotent redelivery; refunds subtract exactly `days_applied`). A payment for a missing / non-ACTIVE / someone else's listing is recorded with `days_applied = 0` and not applied — refund it manually.
- `PromotionExpiryJob` (`promotion.expiry.fixed-delay-ms`, 10 min) clears expired dates and re-publishes `car.updated`; search-service stores `promoted`/`highlighted` booleans computed at indexing time and ranks `promoted` first only for the default sort (none or `createdAt,desc`).

## car-service moderation

- Statuses: `SUSPENDED` (owner's account blocked, follows `user.blocked`/`user.unblocked`) and `BLOCKED` (moderator, with `moderation_reason`, `moderated_by`, `moderated_at`). `ListingStatus.isPubliclyVisible()` hides both (and REMOVED) from the public `GET /cars/{id}`; the owner reads any non-REMOVED listing via `GET /cars/my/{id}` (with the reason), which the frontend falls back to on a 404. Search only shows ACTIVE, so `car.updated` with either status removes the listing from results. A BLOCKED/SUSPENDED listing can't be marked SOLD (SOLD is public). Unblocking a listing restores ACTIVE; blocking/unblocking an account only moves ACTIVE ↔ SUSPENDED, never BLOCKED.
- Reports (`listing_reports`): `POST /cars/{id}/reports` (logged in, not the owner, publicly visible listing only), one OPEN report per user and listing (partial unique index), at most 10 per user per 24 h (429). Blocking a listing resolves its open reports; `POST /cars/admin/reports/listing/{carId}/dismiss` dismisses them.
- `/cars/admin/listings|reports|stats` use native SQL (`NamedParameterJdbcTemplate`) because `@SQLRestriction` would hide REMOVED listings. Photos removed by an admin are deleted from S3 after commit.

When changing an event's payload shape, update the DTO in **both** the producing and consuming service — there's no shared event-schema module; each service has its own copy of the event DTO (e.g. `car-service/.../dto/CarUpdatedEvent.java` and `search-service/.../dto/CarUpdatedEvent.java`).

## Database migrations

Services use Flyway (`src/main/resources/db/migration/V<N>__<Name>.sql`). `car-service` and `auth-service` run Flyway alongside Hibernate `ddl-auto: update` (dev convenience). Flyway was only added to them later, so existing databases (built by ddl-auto, no history table) are baselined (`baseline-on-migrate`, auth at V2, car at V6) and only newer migrations run there; car-service uses its own history table `flyway_schema_history_car` because auction-import-service run from the IDE defaults to `car_db` too. ddl-auto creates CHECK constraints for enum columns and never updates them, so a new enum value needs a migration that recreates the constraint (see car V7). `user-service` has no Flyway dependency: its `db/migration` files are documentation and ddl-auto owns its schema. `chat-service` runs Flyway with ddl-auto `update`; `notification-service`, `auction-import-service`, `payment-service`, `import-order-service` and `admin-service` use `ddl-auto: validate`, meaning **schema changes must go through a new Flyway migration** or the app fails to start. When adding/changing a JPA entity in any service, add a new `V<N+1>__...sql` migration file rather than relying on Hibernate to auto-update — follow the existing numbering per service (each service's migrations are independently numbered, one Postgres DB per service).

One Postgres instance per service (`postgres-auth`, `postgres-user`, `postgres-car`, `postgres-chat`, `postgres-notification`, `postgres-auction`, `postgres-payment`, `postgres-import-order`, `postgres-admin`) — there is no shared database or cross-service SQL join; cross-service data access always goes through REST (via gateway) or Kafka.

## Frontend (avtovo-frontend/)

React 19 + TypeScript + Vite + Tailwind, talking to the API Gateway and directly to chat-service's WebSocket.

- `src/api/` — one axios client module per domain (`cars.ts`, `chat.ts`, `auctions.ts`, `importOrders.ts`, `payments.ts`, ...), `client.ts` is the shared axios instance (base URL from `VITE_API_URL`).
- Chat uses STOMP over WebSocket (`@stomp/stompjs`) directly against `VITE_CHAT_WS_URL` (chat-service `:8085`), not through the gateway.
- One STOMP connection per logged-in user, owned by `context/ChatContext.tsx` (`ChatProvider` in `App.tsx`); components use `useChat()` → `subscribe(handler)` / `sendMessage`. Realtime messages carry `carId` (from the conversation). The open conversation is in the URL (`/messages/:conversationId`); a listing's own chat is the collapsible `ListingChatSection` at the bottom of `CarDetail`.
- Env vars are `VITE_*`, templated in `avtovo-frontend/.env.example`.

## auction-import-service specifics

This is the most complex module — a US-auction (Copart, via the Apibara API) import-cost estimator, not a simple CRUD service:
- `parser/` — `AuctionParser` implementations (`CopartParser`, `ApibaraAuctionParser`) plus `ProxyRotationService` and a Playwright-backed config for scraping.
- `service/calculator/` — `ImportCostCalculator` and `MaxBidCalculator` compute landed cost / max recommended bid from `CalculationInput`, using `ExciseRateResolver`, `CurrencyExchangeService`, and cached USA-to-port shipping rates (`UsaShippingRateService`/`UsaShippingRateCacheWriter`).
- `analytics/` — separate aggregation of `VehicleStats` for reporting, distinct from the calculator flow.
- `scheduler/LotSyncScheduler` periodically pulls/refreshes auction lots in the background.

## ../avtovo — monolith variant (sibling directory, not in this repo)

`avtovo` lives next to this repo (`../avtovo`, its own directory outside this git repo). It is a separate, standalone Spring Boot app (own `pom.xml`, own `docker-compose.yml`, own single Postgres DB) that folds api-gateway + auth-service + the profile part of user-service + auction-import-service + the frontend (`avtovo/frontend/`, embedded into the jar as `classpath:/static`) into one process. It has no listings, chat, search, payments, Eureka, Kafka or Redis. Package root is `com.carmarket.avtovo` (`auth/`, `user/`, `auction/`, `security/`, `config/`).

- Security is one `SecurityFilterChain` (`config/SecurityConfig`): `security/JwtAuthenticationFilter` replaces the gateway JWT filter and there is no `GatewaySignatureFilter` — controllers read the user id from `Authentication#getName()`, not `X-User-Id`. Both filters are built with `new` inside `SecurityConfig`, not `@Component`, so Boot doesn't also register them as global servlet filters.
- All endpoints live under `/api/**` (OAuth2 at `/api/auth/oauth2/authorization|callback/*`); anything else is the SPA (`config/SpaWebConfig` falls back to `index.html`).
- Apibara sync covers Copart **and** IAAI: `apibara.platforms` × `apibara.targets`, lots unique per `(source, lot_number)`.
- Flyway + `ddl-auto: validate`, migrations independent of the microservices'. Tests (`mvn test` in `../avtovo`) boot the app against Postgres via Testcontainers, so Docker must be running.
- Changes to the auction/auth logic in the microservices are **not** shared with `../avtovo` (the code was copied) — port fixes to both places when they apply to both.

## Kubernetes

`README.md` documents a `k8s/` manifest layout (`infra/`, `services/`, `configmaps/`) and apply order, but that directory does not currently exist in the repo — treat it as aspirational/roadmap, not present state.
