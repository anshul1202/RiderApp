# Rider Delivery App

An offline-capable rider delivery application built with **Kotlin**, **Jetpack Compose**, and **Clean Architecture**. Riders can view tasks, perform actions, and create pickups — all while offline — with reliable background sync when connectivity returns.

---

## Architecture

**MVVM + Clean Architecture** with Hilt dependency injection.

```
┌─────────────────────────────────────────────────────────┐
│                   PRESENTATION LAYER                     │
│    Jetpack Compose  •  ViewModels  •  Navigation         │
├─────────────────────────────────────────────────────────┤
│                     DOMAIN LAYER                         │
│    Use Cases  •  Models / Enums  •  Repository Interface │
├─────────────────────────────────────────────────────────┤
│                      DATA LAYER                          │
│    Room DB  •  Retrofit + OkHttp  •  WorkManager Sync    │
├─────────────────────────────────────────────────────────┤
│    Hilt DI Modules  •  Monitoring / Observability        │
└─────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Room as single source of truth** | All UI reads from Room via Kotlin Flows — works fully offline |
| **Offline-first actions** | Actions saved locally first, synced asynchronously via WorkManager |
| **Batched sync (50/batch)** | Scales to 1000+ records without overwhelming server or memory |
| **Exponential backoff retry** | Per-batch retry (1s → 2s → 4s) + WorkManager-level retry — all configurable via `SyncConfig` |
| **Server-wins conflict resolution** | On sync, server data overwrites local — appropriate for logistics dispatch |

---

## Task State Machine

```
PICKUP Task:
  ASSIGNED ──[Reach]──▶ REACHED ──[Pick Up]──▶ PICKED_UP ✓
                                  ──[Fail Pickup]──▶ FAILED_PICKUP ✗

DROP Task:
  ASSIGNED ──[Reach]──▶ REACHED ──[Deliver]──▶ DELIVERED ✓
                                  ──[Fail Delivery]──▶ FAILED_DELIVERY ──[Return]──▶ RETURNED ↩
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| Database | Room (SQLite) |
| Networking | Retrofit + OkHttp |
| Background Sync | WorkManager |
| Async | Kotlin Coroutines + Flow |
| Testing | JUnit + MockK + Coroutines Test + Turbine |
| Monitoring | Mock Sentry / Datadog logging |

---

## How to Run

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 34
- Android device or emulator running API 26+

### Build & Run

```bash
# Clone
git clone https://github.com/anshul1202/RiderApp.git
cd RiderApp

# Build
./gradlew assembleDebug

# Run tests (80 test cases)
./gradlew testDebugUnitTest
```

Or open in Android Studio → Sync Gradle → Run on device/emulator.

### First Launch

The app fetches tasks from the mock API on first launch, stores them in Room, and is fully usable offline thereafter.

---

## Scenarios to Try

### 1. View & Search Tasks
- Browse the task list with **1000 tasks** (500 Pickup + 500 Drop)
- **Filter** by type using chips: All / Pickup / Drop
- **Search** by customer name, address, task ID, or description
- Newly created tasks appear at the top (sorted by most recent)

### 2. Perform Task Actions
- Tap any task → view details → tap action buttons
- **Pickup flow**: Reach → Pick Up (or Fail Pickup)
- **Drop flow**: Reach → Deliver (or Fail Delivery → Return)
- Failure actions prompt for notes/reason

### 3. Offline Mode
- Turn on **Airplane Mode**
- Red "You're offline" banner appears
- **All actions still work** — saved locally with "Pending Sync" indicator
- Create new pickup tasks via the **+** button

### 4. Sync When Back Online
- Turn off Airplane Mode
- Amber "X actions pending sync" bar appears
- Tap to sync manually, or WorkManager syncs automatically
- Actions transition from pending → synced (green checkmarks)

### 5. Observability
- Check **Logcat** with these tags to see monitoring output:
  - `SENTRY` — Error tracking
  - `DATADOG` — Events and metrics
  - `METRICS` — Sync health metrics
  - `SyncWorker` — Background sync logs
  - `SyncMonitor` — Health status and alerts

---

## Failure Handling

| Failure Scenario | How It's Handled |
|-----------------|------------------|
| **No network** | Actions saved offline, synced when online |
| **API returns error** | Per-batch exponential backoff retry (configurable) |
| **Partial sync success** | Successful actions marked synced, failed ones retry next cycle |
| **WorkManager crash** | System restarts worker automatically with backoff |
| **Exhausted retries** | Critical alert fired via monitoring, doesn't block future syncs |
| **Server conflict** | Server wins — local data overwritten on next pull |

### Two-Tier Retry Strategy

```
Per-Batch Retry (inside syncActions):
  Batch → API error → wait 1s → retry → API error → wait 2s → retry → give up

WorkManager Retry (across sync cycles):
  SyncWorker fails → system waits 30s → retries → fails → waits 60s → retries
```

All parameters configurable via `SyncConfig`:

```kotlin
SyncConfig(
    batchSize              = 50,        // Actions per API call
    maxRetriesPerBatch     = 3,         // Retries within one batch
    initialBackoffMs       = 1_000,     // 1s initial delay
    backoffMultiplier      = 2.0,       // Exponential factor
    maxBackoffMs           = 60_000,    // 60s cap
    maxRetriesPerAction    = 5,         // Per-action lifetime budget
    periodicSyncIntervalMinutes = 15,   // Background sync interval
    workerInitialBackoffMs = 30_000,    // WorkManager retry delay
    maxWorkerRetries       = 5          // Worker-level retries
)
```

---

## Project Structure

```
app/src/main/java/com/example/riderapp/
├── RiderApplication.kt              # Hilt Application + WorkManager config
├── MainActivity.kt                   # Entry point with Compose Navigation
│
├── domain/                            # ── Domain Layer (pure Kotlin) ──
│   ├── model/                         # Task, TaskAction, ActionType, TaskStatus, SyncResult
│   ├── repository/                    # TaskRepository interface
│   └── usecase/                       # GetTasks, PerformAction, CreateTask, FetchTasks
│
├── data/                              # ── Data Layer ──
│   ├── local/                         # Room DB, DAOs, Entities
│   ├── remote/                        # Retrofit API, DTOs, Mock Interceptor
│   ├── mapper/                        # Entity ↔ Domain ↔ DTO mappers
│   ├── repository/                    # TaskRepositoryImpl (batched sync)
│   └── sync/                          # SyncWorker, SyncManager, SyncConfig
│
├── di/                                # Hilt modules (DB, Network, Repository, App)
├── monitoring/                        # MonitoringService, SyncMonitor
├── util/                              # NetworkMonitor, Constants
│
├── presentation/                      # ── Presentation Layer ──
│   ├── navigation/                    # NavGraph (taskList ↔ taskDetail)
│   ├── screen/tasklist/               # TaskListScreen + ViewModel
│   ├── screen/taskdetail/             # TaskDetailScreen + ViewModel
│   └── components/                    # TaskCard, SyncStatusBar, CreateTaskDialog
│
└── ui/theme/                          # Material3 Theme, Colors, Typography

app/src/test/java/com/example/riderapp/
├── domain/model/ActionTypeTest.kt                    # 16 tests — state machine
├── data/mapper/TaskMapperTest.kt                     # 10 tests — mapping
├── data/sync/SyncConfigTest.kt                       # 12 tests — backoff math
├── data/repository/TaskRepositoryImplTest.kt         # 16 tests — batched sync
├── presentation/screen/tasklist/TaskListViewModelTest.kt   # 14 tests
└── presentation/screen/taskdetail/TaskDetailViewModelTest.kt # 12 tests
                                                        Total: 80 tests
```

---

## API Endpoints

The app uses real mock APIs hosted on [mockerapi.com](https://free.mockerapi.com):

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/mock/f8ecd9ce-...` | Fetch paginated tasks (real API) |
| `POST` | `/mock/04ea364c-...` | Batch sync actions (real API) |
| `POST` | `/api/tasks` | Create task (local mock) |
| `POST` | `/api/tasks/{id}/actions` | Submit action (local mock) |

---

## Data Flow

```
User taps "Deliver"
    │
    ▼
ViewModel → PerformTaskActionUseCase → TaskRepository
    │                                        │
    │                              ┌─────────┴──────────┐
    │                              ▼                     ▼
    │                     Room DB (local)          WorkManager
    │                     • Insert TaskAction      • Batched sync
    │                     • Update Task status     • Exponential backoff
    │                              │               • Server refresh
    │                              ▼
    └──────────────────── UI updates via Flow
```

---

## License

This project is a technical assessment prototype.
