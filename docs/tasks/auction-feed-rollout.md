# Task: auction feed for the Apibara Basic plan, a "today" listing and the lot page

> **How to use this file.** It was written outside your session, against commit `92c9bc0` (2026-10-24).
> Before relying on any statement about the code below, check it against the current repo; if the code has
> moved on, trust the code and tell me what differs. Work one stage at a time, one commit per stage. After a
> stage, tick it in **Progress** at the bottom and add one line on what was done.

Scope: `auction-import-service`, `avtovo-frontend`, `docker-compose.yml`, `.env.example`, `CLAUDE.md` and the task files.
No new microservice, no api-gateway code changes.

**This file replaces** Stages 2 and 3 of `docs/tasks/auction-feed-sync.md` and Stage 4 of `docs/tasks/auction-lots-sync.md`.
Mark them there as "superseded by docs/tasks/auction-feed-rollout.md". Stage 5 of `auction-lots-sync.md` (Redis) stays for later.
`auction-feed-sync.md`'s **Findings** (F1–F7) are the reference for everything Apibara-specific below.

## Decisions already made

- **Plan:** from 1 November we're on Apibara **Basic**: 30,000 requests a month, `per_page` 20. Until then the free plan, with about 75 requests left in October.
- **Split** (`apibara.vehicle-types.include`): SEDAN, AUTOMOBILE, COUPE, SUV, VAN, MOTORCYCLE, PICKUP.
- **F7 is approved:**
  - Open lots are fetched by **auction-day buckets** (`upcoming=only` + `auction_date_from/to`), not by an `updated_within_minutes` watermark.
  - Outcomes come from **`GET /changes?terminal=true`** (if our plan has it, see Stage A).
  - One request covers both platforms (`auction_type=0`, F6).
- **The sync only runs during active hours** (default 07:00–23:00 Europe/Warsaw). No requests at night. Results that arrive overnight are caught up in the morning: `/changes` keeps 14 days of events.
- **Fresh bids:** the listing shows the bid from the last sync, with its time. A fresh bid is fetched only on the lot page, for logged-in users, shared for 60 s. Live bidding happens on Copart/IAAI; we link there.
- **Monthly budget on Basic** (confirmed or adjusted in Stage A):

  | Consumer | Requests/month |
  |---|---|
  | Sync | 22,000 (~730 a day) |
  | Anonymous live lookups (gateway `APIBARA_ANON_GLOBAL_PER_MONTH`) | 3,000 |
  | Lot-page refreshes (100 a day) | 3,000 |
  | Logged-in live lookups and headroom | 2,000 |

  `keep-remaining`: 1,000.

## Stage A. Measure, then size the sync (≤ 6 real requests; stop for my OK)

Free requests are scarce until 1 November. Use `per_page=1&include_total=true`; read `meta.total` and the quota headers.

1. Open lots of the included types (`auction_type=0`, `type[]=…`, `upcoming=only`):
   - for **today**;
   - for **tomorrow**;
   - for **today … today+14** in one request;
   - for **today with `year_from=2012`**, if a year filter exists in the spec (check the spec first; it's free).
2. From the docs (free):
   - Is `auction_date_from/to` a UTC date or the yard's local date?
   - What order does `/vehicles` return without `updated_within_minutes`?
   - Is a cursor still valid after 30 minutes?
3. One request to `GET /changes?terminal=true` (smallest page):
   - Is it available on our plan?
   - What does a `lot.sold` / `lot.not_sold` event carry in `after` (price, date)?
   - What are `sequence` and `latest_available_sequence`?
4. **Proposal**, with the math, appended to this file as **Sizing**:
   - how many days ahead Basic can keep fresh;
   - refresh intervals per bucket (today / tomorrow / later);
   - whether a `year_from` filter is needed to fit;
   - what the free plan does until 1 November.

   Example: if today has 12,000 lots, one pass is 600 requests, which already uses most of a 730-request day. Then either fewer days ahead or a year filter.

**Stop and wait for my OK on the sizing before Stage B.**

## Stage B. Feed sync within the active window

Replace `LotSyncScheduler`'s `fixedDelay` sync, `parser.sync.interval`, `apibara.targets` and `ParserTargetsConfig.Target`.

**Config** (`application.yml`, values via env so switching to Basic needs no code change, commented with the math):

```yaml
apibara:
  per-page: 20
  sync:
    enabled: ${APIBARA_SYNC_ENABLED:false}       # off by default: IntelliJ restarts must not spend quota
    zone: Europe/Warsaw
    active-hours: "07:00-23:00"
    run-every-minutes: 30
    monthly-budget: ${APIBARA_SYNC_MONTHLY_BUDGET:30}
    keep-remaining: ${APIBARA_KEEP_REMAINING:15}
    days-ahead: 2                                 # from Stage A
    refresh-hours: { today: 4, tomorrow: 12, later: 24 }   # from Stage A
    year-from:                                    # from Stage A; empty = no filter
```

**Pacing.** Day allowance = `floor((monthly-budget − spent this period) / active days left in the period, today included) − spent today`.
- The period comes from `X-Subscription-Period-Start/End`, else the calendar month.
- "Spent" is the sum of `sync_runs.api_requests`.
- A run may spend whatever is left of today's allowance, capped by `X-Subscription-Remaining − keep-remaining`.
- Outside active hours, or with `enabled: false`, no run starts. The admin's manual sync ignores active hours but never the budget.

**Work order in every run** (stop when the allowance is used up):
1. **Outcomes:** `/changes?terminal=true` from the stored `sequence` until caught up.
   - Apply events only to lots we store, matched by `(platform, lot_number)`; ignore the rest.
   - Advance `sequence` only after commit.
   - On HTTP 410 `cursor_expired`, jump to `latest_available_sequence`, log a WARN and let the expiry job cover the gap.
   - If Stage A showed `/changes` isn't on our plan, skip this step and tell me.
2. **Today's bucket**, if its last complete pass is older than `refresh-hours.today`.
3. **Tomorrow … `days-ahead`**, closest first, by the same rule.

A bucket pass is a cursor scan of `auction_type=0`, `type[]=…`, `upcoming=only`, `auction_date_from=D`, `auction_date_to=D`, plus `year_from` when set.
- Store the cursor so the next run continues an unfinished pass the same day. Restart the bucket if the cursor is rejected or the day changed.
- The order isn't stable (lots move when re-crawled), so a pass can miss a few lots. The next pass picks them up; don't try to be exact.

**Bookkeeping.** Add a migration `V9` for:
- `sync_streams.cursor TEXT`, `last_total INT`, `pages_done INT`;
- `sync_streams.sequence BIGINT` for the outcomes stream;
- deleting bucket rows older than yesterday.

Stream keys: `outcomes`, `open:2026-11-03`, and so on.

Keep from earlier stages:
- 429 + `Retry-After`;
- `pg_try_advisory_lock`;
- `ApibaraQuotaTracker`;
- a `sync_runs` row per run, with `mode`, `api_requests`, `stop_reason` (BUDGET / QUOTA_EXHAUSTED / OUTSIDE_HOURS / DISABLED / DONE).

**`GET /api/auctions/admin/status`:**
- today's allowance and how much of it is spent;
- per bucket: the last complete pass, `last_total` and pages done;
- the outcomes lag (`latest_available_sequence − sequence`);
- a one-line verdict ("Dziś i jutro aktualne" / "Budżet nie wystarcza na N dni").

**Tests** (mocked client, plus Testcontainers where SQL matters):
- pacing across days and at the end of the period;
- the active-hours gate and the `enabled` flag;
- work order;
- resuming a pass;
- cursor restart;
- outcomes applied only to stored lots, with `sequence` advanced after commit, and 410;
- 429.

Update `LotSyncSchedulerTest`, keep `FrontendJsonContractTest` green, and run `mvn -pl auction-import-service clean verify`.

## Stage C. The listing: "Dziś / Jutro / Wszystkie"

**Backend:**
- **Visibility.** The shared `AuctionLotRepository.LISTED` becomes `status = 'LIVE'` and (`auction_date IS NULL` or `auction_date > now − live-grace-minutes`), with `live-grace-minutes` = 60. A lot whose auction started less than an hour ago stays visible as "Licytacja trwa". `searchLiveLots` and `/lots/facets` share it with the same `now`, from the `Clock` bean.
- **`day=today|tomorrow` filter.** Add it to `/lots` and `/lots/facets`, as the Europe/Warsaw day turned into an `auction_date` range, so dropdown counts match the tab.
- **Sort.** Default `auction_date ASC NULLS LAST`. Allowed, by whitelist: `auctionDate`, `createdAt`, `auctionPrice`, `year`.
- **`LotExpiryJob`.** Exactly as in `auction-lots-sync.md` Stage 4: a fallback only, touching only `LIVE AND ended_at IS NULL`, so a later outcome replaces `EXPIRED`.
- **`GET /lots/meta`** returns `{ lastSuccessfulSyncAt, liveLots, todayLots, tomorrowLots }`.

**Frontend (`AuctionActions.tsx`, `AuctionLotCard.tsx`):**
- **Tabs** "Dziś (N)", "Jutro (N)", "Wszystkie". Default "Dziś"; the tab is kept in the URL (`?dzien=dzis|jutro`).
- **Page header:** "Zaktualizowano o HH:MM".
- **Card:**
  - the auction time in Europe/Warsaw ("dziś 17:30, za 2 godz.");
  - "Licytacja trwa" once the time has passed;
  - the bid as "Oferta $4 200 · 14:30", the time being `last_seen_at`;
  - Copart/IAAI.
- **Sort selector.**
- **Empty-state copy per tab.**

`npm run build && npm run lint`.

## Stage D. The lot page (`/aukcje/lot/:id`)

1. **Show the synced bid with its time** ("Oferta z 14:30").
2. **"Odśwież ofertę"** button, logged-in users only:
   - It calls `POST /api/auctions/lots/{id}/refresh` (non-public at the gateway, since only GET `/lots/**` is public).
   - The backend makes **one** Apibara request for that lot: the most precise single lookup available (a slug or identifier from `raw_data`; else `s=<lot_number>` with the platform). Copart VINs can be masked, so don't rely on the VIN.
   - The result is upserted and the lot returned.
   - The answer is shared per lot for `live-refresh.cache-seconds` (60, in memory for now) and capped by `live-refresh.daily-cap` (env `APIBARA_REFRESH_DAILY_CAP`, default 5). It respects `keep-remaining` and 429.
   - When capped or limited, the page keeps the synced bid and says why.
   - The button is disabled for 60 s after a click.
3. **"Licytuj na Copart" / "Licytuj na IAAI"**: use the record's source URL if Apibara provides one. Otherwise Copart is `https://www.copart.com/lot/{lot}`; for IAAI, hide the button.
4. **Ended lots:**
   - show "Sprzedano za $X (data)", "Nie sprzedano" or "Aukcja zakończona";
   - hide the max-bid calculator;
   - keep the import-cost calculator.
5. Tests for the refresh endpoint (cache, cap, auth, 429). `npm run build && npm run lint`.

## Stage E. Retention, local database, switch checklist, docs

1. **`LotRetentionJob`**, as in `auction-feed-sync.md` Stage 3:
   - delete ended lots older than `retention.ended-lots-days` (365; 0 = keep forever), with their images and `import_calculations`;
   - null `raw_data` after `raw-data-days` (30);
   - batch it.
2. **The service's own database.**
   - Today `application.yml` defaults to `localhost:5435/car_db` (postgres-car), and the compose block passes neither `DB_PORT` nor `DB_NAME` and uses `DB_PASS` instead of `DB_PASSWORD`. So the container can't reach `postgres-auction`, and from IntelliJ the service writes into car-service's database.
   - Make the defaults `localhost:5437/carmarket_auctions`, and pass `DB_PORT: 5432`, `DB_NAME: carmarket_auctions`, `DB_PASSWORD` in compose.
   - Check whether car-service's separate `flyway_schema_history_car` is still needed and say so (don't change car-service).
   - Tell me what to do with the old tables in `car_db`.
3. **Compose and `.env.example`:** add `APIBARA_SYNC_ENABLED`, `APIBARA_SYNC_MONTHLY_BUDGET`, `APIBARA_KEEP_REMAINING` and `APIBARA_REFRESH_DAILY_CAP` to the service's compose block and to `.env.example`, with free-plan values and the Basic values in comments.
4. **`CLAUDE.md`:**
   - rewrite the auction-import-service sync part (buckets, outcomes, active hours, pacing, refresh, retention, database);
   - remove `apibara.targets`;
   - add a short **"Switching to Basic"** checklist:
     - set the four env values plus `APIBARA_ANON_GLOBAL_PER_MONTH=3000` for the gateway;
     - restart;
     - check `/api/auctions/admin/status` after the first run.
5. List what should be ported to `../avtovo`.

## Out of scope

- api-gateway code (only its env value changes, at the switch).
- Redis (Stage 5 of `auction-lots-sync.md`, later).
- The home page.
- `/dealerzy`.
- The W8 calculators' logic.

## Progress

- [x] Stage A. Measure and size: see **Sizing** below; 6 counted requests (24 → 30). Not waited for: on 24 Oct you asked to start syncing on the free plan at once (one run a day at 08:00, days-ahead 0); the Basic proposals (days-ahead 1, `year-from: 2012`, tomorrow before today) are still open.
- [x] Stage B. Feed sync within the active window
  - `FeedSyncService` (outcomes via `/changes?terminal=1` from the newest event, then today … days-ahead by cursor passes, resumable), `SyncBudget` (pacing), `SyncLock` (`pg_try_advisory_lock`), `LotSyncScheduler` (minute check, one SCHEDULED run per slot from `daily-start` 08:00, `run-every-minutes: 0` = once a day; restarts don't repeat it, a late start catches it up), **V9**, `apibara.targets`/`parser.sync.interval` removed, Sizing fixes (approval price kept, sold beats removed), admin status `feed`. Free plan: `APIBARA_SYNC_*` in compose and `.env.example`.
  - **Run and Drive + title filter** (added 24 Oct):
    - `apibara.filters.run-and-drive-only`: sent as `run_cond=RUNS AND DRIVES` and re-checked per record;
    - `title-exclude`: case-insensitive substring, applied on our side, because the API filters titles only by one exact value;
    - **V10** (V9 had already run on the dev database): `run_condition` / `title_type` on `auction_lots`, `skipped_type/condition/title` on `sync_runs`;
    - shown on the lot card and the lot page.
    - **Open:** after the first syncs, list the distinct values (see **Filters** below).
- [ ] Stage C. Listing: Dziś / Jutro / Wszystkie
- [ ] Stage D. Lot page: synced bid, refresh, Copart/IAAI link, ended lots
- [ ] Stage E. Retention, local database, switch checklist, docs

## Sizing (Stage A, 2026-10-24)

### Measurements: 6 counted requests (`X-Subscription-Used` 24 → 30, 70 left)

All `/vehicles` requests used `auction_type=0`, the seven included types, `upcoming=only`, `per_page=1` and `include_total=true`. The time was Saturday 22:05 Warsaw.

| # | Request | `meta.total` |
|---|---|---|
| 1 | today, Sat 24 Oct | **0** |
| 2 | tomorrow, Sun 25 Oct | **0** |
| 3 | 24 Oct … 7 Nov (today … +14) | **65,137** |
| 4 | same with `year_from=2012` | **52,485** (80.6 %) |
| 5 | Mon 26 Oct, the first auction day. Asked instead of "today with `year_from`", because the weekend gives 0 and says nothing. | **18,437** |
| 6 | `GET /changes?terminal=1&limit=10` | see below |

- `year_from` exists (spec, F1). A first `/changes?terminal=true` answered **422 "The terminal field must be true or false"**: it takes `1`/`0`, not `true`. A 422 is **not counted**.
- `limit=10` instead of 1: the cost is per call, so this showed several event types for the same price.

### Answers from the docs and the responses

- **Date filter timezone:** not documented. Inferred from the responses: Apibara stores local times in **Kyiv time (UTC+3 until 25 Oct, UTC+2 after)**.
  - The cursor's `vehicles.ad` is `2026-10-26 09:00:00` for `auction_at` `07:00Z`.
  - The Stage 0 cursor's `updated_at` was `13:03:41` for `10:03:41Z`.
  - `/changes` reports `vehicle_updated_at` with `+03:00` and `after` times without an offset.

  So `auction_date_from/to` most likely compares Kyiv dates, not UTC or the yard's. Effect: a lot at 23:00–24:00 Warsaw lands in the next day's bucket. The buckets use Apibara's date; Stage C's tabs use Warsaw days. That's harmless as long as the neighbouring bucket is synced too.
- **Order without `updated_within_minutes`:** auction date ascending, then id. The cursor is a keyset `{"vehicles.ad", "vehicles.id"}`. A pass therefore reads the soonest auctions first.
- **Cursor after 30 minutes:** not documented, not tested (no request spent on it). The cursor is a stateless keyset (base64 JSON of `ad` and `id`), not a server-side session, so it most likely stays valid as long as the filters are identical. The docs only say not to reuse it after changing a filter. Stage B's "restart the bucket if the cursor is rejected" covers the rest.
- **`/changes` is available on our (free) plan**, and each call counts as 1 request.
  - `meta`: `schema_version 2026-10-04.beta2` (it was `beta1` in Stage 0: still beta and changing), `oldest_available_sequence 11,818,114` (10 Oct), `latest_available_sequence 34,414,178`, `retention_days 14`, `next_cursor` (int), `has_more`.
  - Events: `lot.sold` / `lot.removed`, with `vehicle {id, platform, platform_id, lot_number, vin}` and `reason: source_outcome | source_removed`.
  - `after`/`before` are **flat dotted keys holding only what changed**, with loose types:
    - `"auction.lot_sold": "1"`, `"auction.last_sold_status": "Sold"`, `"auction.last_sold_day": "2026-10-09"`;
    - `"pricing.last_sold_price_usd": 875` (a number);
    - `"auction.auction_at": "2026-10-10 04:45:58"` (Kyiv time, no offset).
  - There's no `outcome_status` and no `sale_price_usd` in `after`.
  - **The price appears only when it changed.** Most sampled `lot.sold` events are "Sold on Approval → Sold" with no price at all.
  - One `lot.removed` event is a **Buy Now sale** (`lot_removed: 1` together with `last_sold_status: Sold`, `sold_buy_now: 1`, a price).

### Consequences for Stage B (need your OK)

1. **Price of a confirmed sale.** Our Stage 1 mapping stores "Sold on Approval" as `EXPIRED` without a price, and the confirming `lot.sold` usually carries no price. Proposal: keep the approval price in `sold_price` while `EXPIRED`; `lot.sold` then turns it into `SOLD` with that price. Use the event's price whenever it's present. A sale with no price ever seen stays `SOLD` with a null price.
2. **Sold beats removed.** When `last_sold_status` is Sold, the lot is `SOLD` even if `lot_removed` is set, both in the event handling and in the Stage 1 mapping (which currently checks `lot_removed` first and would call a Buy Now sale `REMOVED`).
3. **Outcomes start at `latest_available_sequence`**, without replaying the 14 retained days. Catching up 22.6M sequence numbers would cost an unknown number of requests, only for the few lots we store now.
4. **Work order:** outcomes → **tomorrow** → today (see below), not outcomes → today → tomorrow.
5. Send `terminal=1`, not `true`.

### Volume

- **Monday 26 Oct:** 18,437 lots, so one full pass = **922 requests** at 20 per page, or ~743 with `year_from=2012` (×0.806).
- **The other days in the 15-day window:** 46,700 lots over 9 weekdays and 2 Saturdays, about 5,200 a day. The far days are certainly incomplete (lots get a date only days before the sale), so a full weekday is likely **12,000–18,500** lots.
- **Weekends:** Saturday and Sunday buckets are empty: 1 request each, or skip them.
- **Outcomes:** the sample had 10 terminal events in 164 s at 01:45 UTC (US night), i.e. ≥ 5,000 a day, probably 10,000–30,000 across all types by day. At `limit=500` that's **10–60 requests a day, ~300–1,800 a month**. Exact numbers will come from Stage B's admin status.

### Proposal for Basic (22,000 sync requests a month ≈ 733 a day)

| | Without `year_from` | With `year_from=2012` |
|---|---|---|
| One pass per weekday (12,000–18,500 lots) | 600–925 req | 484–745 req |
| × 21 weekdays (November 2026) | 12,600–19,400 | 10,200–15,600 |
| + outcomes | 300–1,800 | 300–1,800 |
| **Total of 22,000** | 12,900–21,200 (no reliable margin) | **10,500–17,400** (4,600–11,500 left) |

- **Days ahead: 1.** Basic keeps **one full pass per auction day, made the day before**: the "Jutro" tab is ≤ 24 h old, and on the day itself "Dziś" shows yesterday's snapshot with its time. Two days ahead, or two full passes per day, don't fit.
- **`refresh-hours`:** `tomorrow: 24` (one pass); `today: 24`, but **after** tomorrow in the work order and only with what's left of the day's allowance. Since a pass runs soonest-auction-first, a partial today-refresh updates exactly the next auctions. `later`: unused while `days-ahead: 1`.
- **`year-from: 2012`: recommended.**
  - Without it, a heavy Monday (922 requests) doesn't fit in one day's allowance: Sunday gets ~733 plus a small share of Saturday's leftover, since the pacing formula spreads unspent budget over all remaining days. A month of heavy days would not fit at all.
  - With it, the Monday pass (~743) just fits and an average month leaves 4,600–11,500 for today-refreshes.
  - It drops 19.4 % of lots, all older than 2012. That's a product choice, so your call.
- **Active hours 07:00–23:00, every 30 min:** the first runs of the day finish tomorrow's pass (soonest auctions first). The later runs only fetch outcomes and today's top-up. No change needed.
- **After two weeks on Basic,** re-read the admin status (`last_total` per bucket, outcomes lag, daily spend). With slack, add `days-ahead: 2` or drop `year_from`.

### Free plan until 1 November

- **70 requests left**, shared with anonymous live lookups (gateway default `APIBARA_ANON_GLOBAL_PER_MONTH=50`) and the lot pages.
- Keep `APIBARA_SYNC_ENABLED=false`. For the end-to-end tests of Stages B–D, run the admin's manual sync with `APIBARA_SYNC_MONTHLY_BUDGET=20`: 1–2 outcome requests plus up to ~18 pages (~360 lots) of the next auction day, enough to fill and check the listing.
- Keep the rest for the lot-page refresh tests (Stage D) and live lookups.
- Note: Stage 0's headers said `X-Subscription-Period-End: 2026-10-24 10:03`, yet the counter didn't reset. Use the calendar month as the fallback period (Stage B already does).

## Filters (Run and Drive, title), 24 Oct

- **Run condition:** `condition.run_condition` on every record (`value`, `label`, `class_hint`), already normalized by Apibara for both platforms:
  - IAAI's start code "Run & Drive" and Copart's highlight both come out as `RUNS AND DRIVES`;
  - a Copart fixture has `Run & Drive Verified`;
  - `/vehicles/filters` lists `RUNS AND DRIVES`, `ENGINE START PROGRAM` ("Vehicle starts") and `STATIONARY / NO INFORMATION`.
  - **Server-side:** `run_cond` takes one value (`RUNS AND DRIVES`, `ENGINE START PROGRAM`, `STATIONARY`, `NO INFORMATION`). The sync sends `run_cond=RUNS AND DRIVES` and re-checks each record (the value contains RUN and DRIVE).
  - **Unverified:** whether `run_cond=RUNS AND DRIVES` also returns "Run & Drive Verified" lots. If the distinct values below never show "Verified", the server filter drops them.
- **Title:** `sale_document` (`name`, `type`, `export`, `registration`, `page_id`, `sale_document_group`).
  - Seen: `CERTIFICATE OF DESTRUCTION` (`registration: false`), `SALVAGE - REBUILT` and `SALVAGE CERTIFICATE` (`registration: true`).
  - `registration` is Apibara's US flag, not a Polish rule; it's kept in `raw_data` only.
  - **Server-side:** `sale_document_type` takes one exact string, so neither an exclude list nor several allowed values work there. `title-exclude` therefore runs in the mapping (`skipped_title`).
- **Not measured beforehand:** Stage A was finished, so no extra request was spent. `sync_runs.skipped_condition` / `skipped_title` and the per-page log line (`kept N of 20 (...)`) show the share after the first runs.
- **After the first syncs**, list the values for the logistics partner:

  ```sql
  SELECT title_type, count(*) FROM auction_lots WHERE title_type IS NOT NULL GROUP BY 1 ORDER BY 2 DESC;
  SELECT run_condition, count(*) FROM auction_lots WHERE run_condition IS NOT NULL GROUP BY 1 ORDER BY 2 DESC;
  SELECT started_at, lots_fetched, skipped_type, skipped_condition, skipped_title FROM sync_runs
   WHERE parser = 'FeedSync' ORDER BY started_at DESC;
  ```

  Lots dropped by the title rule aren't stored, so their titles only appear in the log.
