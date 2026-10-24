Read docs/tasks/auction-lots-sync.md and do Stage 1 only.# Task: fresh auction lots on `/aukcje` without calling Apibara on every request

> **How to use this file.** It was written outside your session, against commit `12730b4` (2026-10-18).
> Before relying on any statement about the code below, check it against the current repo; if the code has
> moved on, trust the code and tell me what differs. Work one stage at a time. After finishing a stage, tick it
> in **Progress** at the bottom of this file and add one line on what was done, so the next session can pick up
> from there.

Work only in `auction-import-service` and `avtovo-frontend`. **Do not create a new microservice.**
Read `CLAUDE.md` first (especially the auction-import-service section), then the files listed under "What already exists".

## Goal

The `/aukcje` page shows upcoming Copart/IAAI lots from our own database only (`auction_lots`).
Only the background sync calls Apibara, and it stays within the monthly request budget on its own.
Leave the live lookups `/api/auctions/apibara/**` ("Szybkie sprawdzenie", `/aukcje/pojazd/:identifier`) and the gateway's `ApibaraRateLimiter` unchanged.

## What already exists (extend it, don't rewrite it)

- `scheduler/LotSyncScheduler`: syncs every `parser.sync.interval` (currently 1 h), manual run via `POST /api/auctions/admin/sync`, each run recorded in `sync_runs`.
- `parser/ApibaraAuctionParser`: one `GET /vehicles` per `apibara.targets` entry, Copart only, no cursor; the year range is filtered after the request.
- `client/ApibaraClient`: swallows every error and returns `null`; doesn't read the quota headers.
- `service/AuctionLotService.saveOrUpdateLots`: upsert by `(lot_number, source)`, publishes `auction.lot.updated` to Kafka (no consumers).
- `GET /api/auctions/lots` (`AuctionLotController` → `AuctionLotRepository.searchLiveLots`): public, read by `AuctionActions.tsx`.

Important: with the current 1 h interval, any non-empty `apibara.targets` spends 720+ requests a month against a quota of 100.

## Apibara facts: verify against https://apibara.tech/openapi/v1.json and the docs before implementing

- Free plan: 100 requests a month. The gateway already lets anonymous live lookups use up to 50 of them (`APIBARA_ANON_GLOBAL_PER_MONTH`); logged-in users and the sync share the rest.
- `per_page`: the maximum is 20/30/40 depending on the plan; the enforced value comes back in the `X-Per-Page-Limit` header. Our config says 50.
- Cursor pagination: `meta.next_cursor`, passed back as-is, never decoded.
- Incremental sync: `updated_within_minutes` (1..525600) combined with the cursor. The docs recommend overlapping windows and idempotent upserts.
- Quota headers: `X-Subscription-Limit/Used/Remaining/Period-Start/Period-End`, `X-RateLimit-Remaining`. When exhausted: 429 + `Retry-After`.
- Apibara refreshes its data about every 15 minutes, so syncing more often than that is pointless even on a paid plan.
- Status semantics: All/Timed/Buy Now is the auction mode, Open/Live/Ended/upcoming is the state (https://apibara.tech/en/vehicle-auction-status-api). Check `mapStatus`: it currently maps `timed` → `UNSOLD`, which looks wrong.
- `auction_type` and `platform` can't be sent in the same request.
- Check which filters the API applies server-side: year (`year_from`/`year_to`), upcoming auctions or state, arrays (`make[]`, `model[]`). Filter on the API side wherever possible so no requests are wasted. If arrays are supported, combine targets into one request.
- If the docs contradict any of this, stop and ask me. Don't guess.

## Stage 1. Bugs in the current sync (own commit)

1. `@Transactional` in `AuctionLotService` sits on the nested record `SyncCounts`, not on the method, so `saveOrUpdateLots` isn't transactional. Make the upsert transactional: one transaction per response page.
2. Kafka currently receives the JPA entity, and before commit. Publish a small `AuctionLotChangedEvent` DTO (`id`, `source`, `lotNumber`, `status`, `auctionDate`, `changeType`) only after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)` or a `TransactionSynchronization`). Keep the topic.
3. `hasSignificantChanges` and `updateLot` ignore `auctionDate`, `primaryDamage`, `vin` and other fields, so a rescheduled auction never gets its new date. Compare a hash of the normalized fields (`content_hash`) and copy every mapped field.
4. `lot_number` is globally unique (V1 and `unique = true` on the entity), but Copart and IAAI lot numbers can collide. Replace it with a unique `(source, lot_number)`.
5. `auctionDate`: `OffsetDateTime.toLocalDateTime()` drops the offset. Normalize to UTC. Preferably migrate the column to `TIMESTAMPTZ` + `Instant` if that doesn't break the calculators; otherwise keep `LocalDateTime` strictly in UTC with a comment. The frontend displays times in Europe/Warsaw.
6. `raw_data` is never filled. Store the lot's JSON from the response there, and hide it from API responses (`@JsonIgnore`); the frontend doesn't read it.
7. `apibara.per-page: 50`: use the minimum of the config value and `X-Per-Page-Limit`.

## Stage 2. A quota-aware client

- Add a separate sync-only method to `ApibaraClient` (e.g. `searchVehiclesPage(params)`). It returns the response body plus an `ApibaraQuota` (the header values) and **throws** typed exceptions: `ApibaraQuotaExceededException(retryAfter)` on 429, `ApibaraUnavailableException` on any other failure. Don't change the existing live-lookup methods: their `null` → 502 in `ApibaraController` stays as is.
- Add `meta` (`next_cursor`, `per_page`) to `ApibaraResponse`.
- Keep the latest quota snapshot in memory (`ApibaraQuotaTracker`) and persist it in `sync_runs`. `GET /api/auctions/admin/status` shows that snapshot. Check in the docs whether `GET /usage` spends quota; if it does, call it only on `?refresh=true`.
- Never log the API key.

## Stage 3. Incremental sync within a budget (superseded by docs/tasks/auction-feed-sync.md)

Config in `application.yml`, with comments explaining the math. Remove `parser.sync.interval` and the commented-out `parser:` block.

```yaml
apibara:
  platforms: [copart, iaai]
  per-page: 40
  sync:
    cron: "0 0 6,18 * * *"     # Europe/Warsaw; runs with no budget left are simply skipped
    zone: Europe/Warsaw
    monthly-budget: 30         # requests per month the sync may spend
    start-burst: 3             # so the first run of a period doesn't have to wait
    keep-remaining: 15         # never push the remaining quota below this; reserve for live lookups
    max-pages-per-target: 3
    overlap-minutes: 30
    full-resync-days: 7        # every N days a full pass without updated_within_minutes
```

One run:

1. **How much it may spend.** Even pacing: by time `t` the sync may have spent `floor(monthly-budget × elapsed fraction of the period + start-burst)`; subtract what it already spent. Cap the result at `X-Subscription-Remaining − keep-remaining`, floor at 0. "Already spent" is the sum of `api_requests` in `sync_runs` since the period start (`X-Subscription-Period-Start` when known, otherwise a rolling 30 days). If the allowance is 0, record the run with `stop_reason = BUDGET` and make no request at all.
2. **Mode.** No successful run yet, or `full-resync-days` have passed → FULL (no `updated_within_minutes`). Otherwise INCREMENTAL with a window of minutes since the start of the last successful run + `overlap-minutes`, capped at 525600.
3. **Traversal.** For each target × platform: request, upsert the page, follow `next_cursor`. Continue while there's a cursor, the run's allowance isn't used up and `max-pages-per-target` isn't exceeded. Rotate the starting target between runs (round-robin) so the last targets don't starve on a small budget.
4. **429.** Stop the run with `stop_reason = QUOTA_EXHAUSTED`; the next run waits at least until `Retry-After`.
5. **Locking.** Besides the `AtomicBoolean`, hold `pg_try_advisory_lock` for the run so two instances never sync at the same time.
6. **Manual run.** `POST /api/auctions/admin/sync` obeys the same budget. It may accept `force=true` to run in FULL mode, but never bypasses `keep-remaining`.

Migration `V6__...` (don't touch V1–V5; `ddl-auto: validate`):

- `auction_lots`: `last_seen_at TIMESTAMPTZ`, `content_hash VARCHAR(64)`, unique `(source, lot_number)` replacing unique `lot_number`, index on `(status, auction_date)`.
- `sync_runs`: `mode`, `api_requests`, `quota_remaining`, `stop_reason`, `window_minutes`.

## Stage 4. The listing never calls the API (superseded by docs/tasks/auction-feed-rollout.md)

- `searchLiveLots`: only `status = LIVE` and (`auction_date IS NULL` or `auction_date > now()`). Put the date rule into `AuctionLotRepository.LISTED`, the fragment `searchLiveLots` and `findFacetRows` (`GET /lots/facets`) share, so the dropdown counts keep matching the listing (`AuctionLotFacetsRepositoryTest`); `now()` must be the same in both. Default sort `auction_date ASC NULLS LAST`. Allowed sorts, by whitelist: `auctionDate`, `createdAt`, `auctionPrice`, `year`.
- `LotExpiryJob` (no API calls, every 15 minutes): LIVE with `auction_date < now() − 2h` → `EXPIRED`; LIVE with no date and `last_seen_at` older than 14 days → `EXPIRED`. Publish events for changed rows after commit, as in stage 1. It's only a fallback for lots Apibara never reported as ended: touch only `status = 'LIVE' AND ended_at IS NULL`, never SOLD/UNSOLD/REMOVED, and any status from the feed replaces `EXPIRED` later (`AuctionLotService.saveOrUpdateLots`, `docs/tasks/auction-feed-sync.md` Stage 1).
- `GET /api/auctions/lots/meta` (public, like `/lots/**`): `{ lastSuccessfulSyncAt, liveLots }`.
- Frontend `AuctionActions.tsx`: on page load call only `searchLots` and `meta`. Show "Zaktualizowano: …", the auction date on each card in Europe/Warsaw ("za 5 godz."), and a sort selector. The "Szybkie sprawdzenie w Apibara" block no longer renders on `/aukcje` (moved, unrendered, to `src/components/auctions/ApibaraQuickTools.tsx` by `docs/tasks/ui-cleanup.md`); don't bring it or `W8TransportCalculator` back to this page.

## Stage 5. Redis cache for the listing

- `auction-import-service` has no Redis and no `@EnableCaching` yet. Connect it to the shared `redis` from `docker-compose.yml`: `spring-boot-starter-data-redis`, `REDIS_HOST`/`REDIS_PASSWORD` in the service's compose block, like api-gateway and auth-service.
- Cache only the responses of `GET /api/auctions/lots`, `/lots/meta` and `/lots/facets`, and evict all three together. Key: the normalized request parameters; TTL 10 minutes. `Page<T>` doesn't deserialize well from Redis, so cache and return your own record with the fields the frontend reads: `content`, `totalPages`, `totalElements`, `number`, `size`.
- Invalidation: after a sync run or `LotExpiryJob` commits with at least one changed row, evict the whole listing cache. No need to keep Redis constantly refreshed.
- Fail open: if Redis is down, a `CacheErrorHandler` logs a WARN and the request goes to the database (like the gateway's rate limiter). Flag `auction.lots-cache.enabled`, default `true`.

## Tests

- Unit tests with a mocked client: cursor pagination, stopping on the budget, on `keep-remaining` and on 429, the window and allowance math, round-robin over targets. Quota header parsing. `mapStatus` and mapping against the fixtures `src/test/java/com/carmarket/auction/apibara.tech_*.json`.
- Testcontainers (like `SyncRunRepositoryTest`): idempotent upsert, `content_hash`, unique `(source, lot_number)`, `searchLiveLots` hides past auctions, `LotExpiryJob`, migration V6 on an empty database.
- Update `LotSyncSchedulerTest`. `FrontendJsonContractTest` must pass; add `/lots/meta` and the cached page record to it.
- Run `mvn -pl auction-import-service clean verify`, and in `avtovo-frontend` run `npm run build && npm run lint`.

## How to work

- One stage, one commit. After each stage, briefly: what was done, what was verified, what's left.
- Don't touch: api-gateway and `ApibaraRateLimiter`, the live endpoints `/api/auctions/apibara/**`, the calculators and W8, `../avtovo`.
- At the end, update the auction-import-service section of `CLAUDE.md` (sync, budget, config, cache) and list what of this should be ported to `../avtovo`.

## Progress

- [x] Stage 1. Bugs in the current sync — upsert @Transactional per page (`AuctionParser.parseLiveLotPages`), `AuctionLotChangedEvent` after commit (`AuctionLotEventPublisher`), `LotContentHash` + full field copy, migration **V6** (unique `(source, lot_number)`, `content_hash`, `auction_date` → TIMESTAMPTZ/`Instant`, V1 `image_url` → `image_urls`), `raw_data` filled + `@JsonIgnore`, per-page = min(config 40, `X-Per-Page-Limit`). Stage 3's migration is therefore **V7**, and only needs `last_seen_at`, the `(status, auction_date)` index and the `sync_runs` columns.
- [x] Stage 2. Quota-aware client — `ApibaraClient.searchVehiclesPage` (sync only; `ApibaraVehiclesPage` = raw vehicles + `meta` + `ApibaraQuota`; throws `ApibaraQuotaExceededException(retryAfter)` / `ApibaraUnavailableException`), `ApibaraResponse.meta`, `ApibaraQuotaTracker` (in memory, falls back to the last snapshot in `sync_runs`; keeps `retryNotBefore` from the last 429 for stage 3), **V7** adds `api_requests` + `quota_*` to `sync_runs`. The parser now hands pages over as they arrive (`AuctionParser.forEachLiveLotPage`), so a 429 mid-run keeps the saved pages. `/admin/status` returns `apibaraQuota` and calls `/usage` only on `?refresh=true` (Apibara does not document that `/usage` is free); the admin page has a button for it. Stage 3 migration: **V8** (`last_seen_at`, index, `mode`, `stop_reason`, `window_minutes`).
- [ ] Stage 3. Incremental sync within a budget — superseded by docs/tasks/auction-feed-sync.md (its budget rules are reused there)
- [ ] Stage 4. Listing without API calls — superseded by docs/tasks/auction-feed-rollout.md
- [ ] Stage 5. Redis cache for the listing
- [ ] `CLAUDE.md` updated, list for `../avtovo`
