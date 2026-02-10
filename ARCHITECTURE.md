# Architecture Notes — Rider Delivery App

## 1. High-Level Architecture

The app follows **MVVM + Clean Architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│                   PRESENTATION LAYER                     │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │  Composables  │  │  ViewModels  │  │  Navigation   │ │
│  └──────┬───────┘  └──────┬───────┘  └───────────────┘ │
│         │                  │                             │
├─────────┼──────────────────┼─────────────────────────────┤
│         │    DOMAIN LAYER  │                             │
│  ┌──────┴───────┐  ┌──────┴───────┐  ┌───────────────┐ │
│  │   Use Cases   │  │   Models     │  │  Repository   │ │
│  │               │  │   (Enums)    │  │  (Interface)  │ │
│  └──────┬───────┘  └──────────────┘  └──────┬────────┘ │
│         │                                    │          │
├─────────┼────────────────────────────────────┼──────────┤
│         │         DATA LAYER                 │          │
│  ┌──────┴───────────────────────────────────┴────────┐  │
│  │              Repository Implementation             │  │
│  │  ┌──────────┐  ┌───────────┐  ┌────────────────┐ │  │
│  │  │ Room DB  │  │ Retrofit  │  │  WorkManager   │ │  │
│  │  │ (DAOs)   │  │ (API)     │  │  (SyncWorker)  │ │  │
│  │  └──────────┘  └───────────┘  └────────────────┘ │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  SyncConfig (configurable batch + backoff)   │ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │          DI (Hilt Modules)                         │  │
│  └───────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │          Monitoring / Observability                 │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 2. Key Design Decisions

### Offline-First Architecture
- **Room DB is the single source of truth.** All UI reads come from Room via Kotlin Flows.
- **Actions are always saved locally first**, then synced to the server asynchronously.
- The app is fully functional without any network connectivity.

### Sync Strategy
- **Push then Pull**: SyncWorker first pushes unsynced actions to the server, then pulls the latest task state.
- **Server Wins with Pending Protection**: On sync, server data overwrites local data — *except* for tasks that have unsynced local changes (`syncStatus != SYNCED`). These are protected until their actions are confirmed by the server.
- **Idempotent actions**: Each action has a UUID. The server can deduplicate if the same action is submitted twice.

### Batched Sync Engine
All unsynced actions are processed in a **configurable batch loop** within a single sync cycle:

```
syncActions() loop:
  Batch 1: actions 1–50   → send to API → process response → next
  Batch 2: actions 51–100 → send to API → process response → next
  ...
  Batch N: remaining      → send to API → done
```

- Batch size, retry count, and backoff are all controlled by `SyncConfig` (injected via Hilt)
- If an entire batch fails with zero successes, the loop stops to avoid hammering a down server
- Results are aggregated across all batches into a single `SyncResult`

### Two-Tier Retry with Configurable Exponential Backoff

```
┌─────────────────────────────────────────────────────────────┐
│ TIER 1: Per-Batch Retry (inside syncActions)                │
│                                                             │
│   Batch → API error → wait 1s → retry → wait 2s → retry    │
│   Configurable: initialBackoffMs, multiplier, maxBackoffMs  │
│   Default: 1s → 2s → 4s (3 retries per batch)              │
├─────────────────────────────────────────────────────────────┤
│ TIER 2: WorkManager Retry (across sync cycles)              │
│                                                             │
│   SyncWorker fails → system waits 30s → retry → 60s → ...  │
│   Configurable: workerInitialBackoffMs, maxWorkerRetries    │
│   Default: 30s exponential (5 retries)                      │
├─────────────────────────────────────────────────────────────┤
│ TIER 3: Per-Action Budget (across all cycles)               │
│                                                             │
│   Each action tracks retryCount in Room                     │
│   Excluded from sync after maxRetriesPerAction (default: 5) │
└─────────────────────────────────────────────────────────────┘
```

All parameters configurable via `SyncConfig`:
```kotlin
SyncConfig(
    batchSize              = 50,      // Actions per API call
    maxRetriesPerBatch     = 3,       // Retries within one batch
    initialBackoffMs       = 1_000,   // 1s initial delay
    backoffMultiplier      = 2.0,     // Exponential factor
    maxBackoffMs           = 60_000,  // 60s cap
    maxRetriesPerAction    = 5,       // Per-action lifetime budget
    periodicSyncIntervalMinutes = 15, // Background sync interval
    workerInitialBackoffMs = 30_000,  // WorkManager retry delay
    maxWorkerRetries       = 5        // Worker-level retries
)
```

### Failure Handling

| Failure Scenario | How It's Handled |
|-----------------|------------------|
| **No network** | Actions saved offline, synced when connectivity returns |
| **API returns error** | Per-batch exponential backoff retry (Tier 1) |
| **Partial sync success** | Successful actions marked synced, failed ones increment retryCount |
| **Entire batch fails** | Batch loop stops, WorkManager retries later (Tier 2) |
| **Action exhausts retries** | Excluded from future syncs (Tier 3), critical alert fired |
| **WorkManager crash** | System restarts worker automatically with exponential backoff |
| **Server overwrites local** | Tasks with `syncStatus != SYNCED` are protected from server fetch |
| **Duplicate button taps** | `actionInProgress` guard set synchronously before coroutine launch |
| **Duplicate API calls** | `isLoadingData` flag prevents concurrent `fetchTasksFromServer()` calls |

### Scale Considerations (1000+ records)
- **Paginated API**: API returns records in pages (configurable size). Early exit when `data.size < pageSize`.
- **Room + Flow**: LazyColumn only renders visible items. Room returns reactive Flows so the UI updates incrementally.
- **Batched Sync**: All unsynced actions processed in batches of 50 within a single sync cycle — 1000 actions = 20 batches, not 20 separate WorkManager runs.
- **WorkManager Constraints**: Sync only runs when network is available, with exponential backoff to prevent thundering herd after outages.
- **Search with debounce**: SQL LIKE queries on indexed fields with 300ms debounce to avoid query-per-keystroke.

### Monitoring & Observability
- `MonitoringService` simulates Sentry (error tracking) and Datadog (metrics/events).
- `SyncMonitor` tracks consecutive failures, sync health status (HEALTHY/DEGRADED/STALE/CRITICAL), and triggers alerts when thresholds are exceeded.
- All sync operations, API calls, and user actions are logged with structured context.

### Debug Logging

| Logcat Tag | Component | What it logs |
|------------|-----------|-------------|
| `TaskRepo` | TaskRepositoryImpl | CRUD operations, fetch pages, sync batch loop, pending protection |
| `TaskListVM` | TaskListViewModel | State changes, filter/search, network, load guards, task creation |
| `TaskDetailVM` | TaskDetailViewModel | Task emissions, status transitions, action execution, tap guards |
| `NetworkMonitor` | NetworkMonitor | onAvailable, onLost, initial state |
| `SyncWorker` | SyncWorker | Worker lifecycle, attempt count, sync results, crash recovery |
| `SyncManager` | SyncManager | Periodic/immediate scheduling, backoff config |
| `SENTRY` | MonitoringService | Error capture with stack traces |
| `DATADOG` | MonitoringService | Events with structured properties |
| `METRICS` | MonitoringService | Sync metrics (synced/failed/total) |

## 3. Task State Machine

```
PICKUP Task:
  ASSIGNED ──[REACH]──▶ REACHED ──[PICK_UP]──▶ PICKED_UP (terminal)
                                  ──[FAIL_PICKUP]──▶ FAILED_PICKUP (terminal)

DROP Task:
  ASSIGNED ──[REACH]──▶ REACHED ──[DELIVER]──▶ DELIVERED (terminal)
                                  ──[FAIL_DELIVERY]──▶ FAILED_DELIVERY ──[RETURN]──▶ RETURNED (terminal)
```

Each transition:
1. Creates a `TaskAction` record in Room (`isSynced = false`)
2. Updates the task's `status` and sets `syncStatus = PENDING`
3. Triggers immediate sync via WorkManager
4. Room Flow emits → UI updates instantly (before sync completes)

Available actions are computed dynamically from `ActionType.getAvailableActions(taskType, currentStatus)` — terminal states return an empty list.

## 4. Data Flow

```
User taps "Deliver"
    │
    ▼
ViewModel ──[guard: actionInProgress?]──▶ PerformTaskActionUseCase ──▶ Repository
    │                                                                       │
    │                                                          ┌────────────┴────────────┐
    │                                                          ▼                          ▼
    │                                                   Room DB (local)            WorkManager
    │                                                   • Insert TaskAction        • Batched sync
    │                                                     (isSynced=false)         • Per-batch retry
    │                                                   • Update Task status       • Exponential backoff
    │                                                     (syncStatus=PENDING)     • Server refresh
    │                                                          │                    (skips PENDING tasks)
    │                                                          ▼
    └──────────────────────────────────────────── UI updates via Flow
```

1. User taps action button → ViewModel checks `actionInProgress` guard
2. Guard passes → flag set synchronously → coroutine launched
3. Repository inserts `TaskActionEntity` (unsynced) into Room
4. Repository updates `TaskEntity` status locally (`syncStatus = PENDING`)
5. Room Flow emits → UI updates immediately (new status, new action in timeline)
6. WorkManager picks up unsynced actions → batched sync with exponential backoff
7. On success: marks actions as synced → updates `syncStatus = SYNCED`
8. Server refresh: pulls latest tasks but **skips** tasks with `syncStatus != SYNCED`

## 5. Database Schema

```
┌──────────────────┐              ┌──────────────────────┐
│      tasks       │              │     task_actions      │
├──────────────────┤              ├──────────────────────┤
│ PK  id (TEXT)    │◄─────────────│     taskId (IDX)     │
│     type         │     1:N      │ PK  id (TEXT/UUID)   │
│     status       │              │     actionType       │
│     riderId      │              │     timestamp        │
│     customerName │              │     latitude?        │
│     customerPhone│              │     longitude?       │
│     address      │              │     notes?           │
│     description  │              │     isSynced (0/1)   │
│     createdAt    │              │     retryCount       │
│     updatedAt    │              │     lastError?       │
│     syncStatus   │              └──────────────────────┘
└──────────────────┘
```

- **1 task → N actions** (one-to-many via `taskId` with index)
- Actions created locally with `isSynced = 0`, marked `1` after successful API push
- Tasks with `syncStatus != SYNCED` are protected from server overwrite during fetch
- `retryCount` per action ensures poison-pill actions don't block the sync queue

## 6. Tradeoffs

| Decision | Pro | Con |
|----------|-----|-----|
| Server wins + pending protection | Simple conflict resolution, protects in-flight local changes | Server updates for pending tasks are delayed until sync completes |
| Batched sync in single cycle | 1000 actions = 20 batches in one run, not 20 WorkManager cycles | Long-running sync cycle if many batches |
| Configurable SyncConfig via DI | All retry/backoff params tunable without code changes | Slight complexity overhead |
| Room as single source of truth | Offline-first, reactive UI, consistent state | Requires careful sync status management |
| WorkManager for background sync | Survives process death, respects battery/network constraints | Minimum 15min interval for periodic work |
| Hybrid mock interceptor | Real API for GET/sync, local mock for create/action | Less control over mock responses |
| SQL LIKE search with debounce | Simple, works offline, no extra dependency | Not fuzzy — exact substring match only |
| Synchronous actionInProgress guard | Prevents duplicate taps before coroutine launch | N/A — strictly better than async guard |

## 7. AI-Assisted Development

This implementation was built collaboratively with AI assistance:

| Phase | Designed by Me | Generated by AI |
|-------|---------------|-----------------|
| **Architecture** | MVVM + Clean Architecture, offline-first, server-wins with pending protection | Layer separation, package layout, DI module wiring |
| **State machine** | PICKUP/DROP flows, terminal states, action→status mapping | `ActionType` enum with helper functions |
| **Sync strategy** | Batched sync, two-tier retry, configurable backoff, pending task protection | `SyncConfig`, batch loop, `SyncWorker`, `SyncManager`, `fetchTasksFromServer` pending filter |
| **Data models** | Field names, types, relationships, sync status tracking | Entity, DTO, Domain classes, mapper functions |
| **API design** | 5 endpoints, JSON shapes, pagination, partial success responses | Retrofit interface, `MockInterceptor`, mock data |
| **UI/UX** | Screen layouts, search with debounce, filter chips, status colors | Compose screens, components, Material3 theme |
| **Failure handling** | Failure scenarios, guard patterns, retry budgets | Implementation: retry logic, health tracking, monitoring |
| **Testing** | What to test, edge cases, mock API null handling | 81 test cases using MockK |
| **Debugging** | Log tag strategy, what to log at each layer | Log statements across 6 components |

**Key insight**: AI was most valuable for boilerplate-heavy layers (entities, DTOs, mappers, DI, Compose scaffolding) and test generation. Critical thinking — architecture decisions, failure mode analysis, sync strategy, race condition fixes — was done by me.
