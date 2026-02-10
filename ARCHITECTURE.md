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
│  │  │ (DAOs)   │  │ (Mock API)│  │  (SyncWorker)  │ │  │
│  │  └──────────┘  └───────────┘  └────────────────┘ │  │
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
- **Server Wins**: On conflict, server data overwrites local data. This is appropriate for logistics where the server is the authoritative source (e.g., dispatch reassignments, cancellations).
- **Idempotent actions**: Each action has a UUID. The server can deduplicate if the same action is submitted twice.

### Failure Handling
1. **WorkManager Crashes**: WorkManager automatically restarts crashed workers. We use exponential backoff with a max of 5 retries. After exhaustion, a critical alert is fired.
2. **API Failures**: Each action tracks `retryCount` and `lastError`. Failed actions are retried in subsequent sync cycles up to the max retry limit.
3. **Partial Sync Success**: The `SyncResult.PartialSuccess` type tracks which actions succeeded and which failed, allowing fine-grained retry logic.
4. **Network Monitoring**: `NetworkMonitor` uses Android's `ConnectivityManager` callbacks to detect connectivity changes. When the network returns, an immediate sync is triggered.

### Scale Considerations (1000+ records)
- **Paginated API**: Mock API returns 50 records per page (20 pages for 1000 tasks). This prevents memory pressure during initial data load.
- **Room + Flow**: LazyColumn only renders visible items. Room returns reactive Flows so the UI updates incrementally.
- **Batched Sync**: Unsynced actions are synced in batches of 50 to avoid overwhelming the server.
- **WorkManager Constraints**: Sync only runs when network is available, with exponential backoff to prevent thundering herd after outages.

### Monitoring & Observability
- `MonitoringService` simulates Sentry (error tracking) and Datadog (metrics/events).
- `SyncMonitor` tracks consecutive failures, sync health status, and triggers critical alerts when thresholds are exceeded.
- All sync operations, API calls, and user actions are logged with structured context for debugging.

## 3. Task State Machine

```
PICKUP Task:
  ASSIGNED ──[REACH]──> REACHED ──[PICK_UP]──> PICKED_UP (terminal)
                                 ──[FAIL_PICKUP]──> FAILED_PICKUP (terminal)

DROP Task:
  ASSIGNED ──[REACH]──> REACHED ──[DELIVER]──> DELIVERED (terminal)
                                 ──[FAIL_DELIVERY]──> FAILED_DELIVERY ──[RETURN]──> RETURNED (terminal)
```

Each transition creates a `TaskAction` record (saved locally, synced later) and updates the task's status.

## 4. Data Flow

```
User Action → ViewModel → UseCase → Repository
                                        │
                               ┌────────┴────────┐
                               │                  │
                         Room (local)       API (remote)
                         ↓ (Flow)           ↓ (async)
                      UI updates         WorkManager sync
```

1. User performs action (e.g., "Reach Location")
2. ViewModel calls `PerformTaskActionUseCase`
3. Repository creates `TaskActionEntity` (unsynced) in Room
4. Repository updates `TaskEntity` status locally
5. Room Flow emits → UI updates immediately
6. WorkManager picks up unsynced actions → pushes to API
7. On success: marks action as synced, refreshes from server

## 5. Tradeoffs

| Decision | Pro | Con |
|----------|-----|-----|
| Server wins conflict | Simple, consistent | May lose offline edits if server changes same task |
| Single rider (hardcoded) | Simpler prototype | No multi-user considerations |
| KAPT over KSP | More stable with Hilt | Slower build times |
| Room as single source | Offline-first, reactive UI | Requires careful sync logic |
| WorkManager for sync | Survives process death, respects constraints | Minimum 15min interval for periodic work |
| Mock interceptor vs MockWebServer | No server process needed | Less realistic network simulation |

## 6. AI-Assisted Development

This implementation was built collaboratively with AI assistance:
- **Architecture design**: AI helped structure the Clean Architecture layers and identify key patterns.
- **Boilerplate generation**: Entity/DTO/Mapper files were generated from specifications.
- **State machine design**: The task flow transitions were designed collaboratively.
- **Error handling patterns**: AI suggested comprehensive failure handling for WorkManager, partial sync, and monitoring.
- **Mock data generation**: 1000 realistic Indian logistics addresses and names were generated for testing.
- **Review and fixes**: AI identified and fixed method name mismatches, incorrect ID formatting, and missing annotations.
