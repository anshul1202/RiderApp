# Rider Delivery App — Project README

## How to Run

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 34
- An Android device/emulator running API 26+

### Steps
1. Open the project in Android Studio
2. Sync Gradle (should happen automatically)
3. Select a device/emulator
4. Click **Run** (or `./gradlew assembleDebug` from terminal)

### First Launch
On first launch, the app fetches 1000 mock tasks from the mock API (paginated at 50/page). This takes a few seconds as it simulates network delay. The data is stored in Room DB and is available offline thereafter.

---

## Scenarios to Try

### 1. View Tasks
- Launch the app → you see a list of 1000 tasks (500 Pickup + 500 Drop)
- Use filter chips at the top to filter by **All**, **Pickup**, or **Drop**
- Each card shows task ID, type badge, customer name, address, status, and relative time

### 2. Perform Task Actions (Online)
- Tap any task to view details
- Tap **"Reach Location"** → task status changes to "Reached"
- For Pickup tasks: you can then **Pick Up** or **Fail Pickup**
- For Drop tasks: you can then **Deliver** or **Fail Delivery**
- If delivery fails: you can then **Return** the package
- Failure actions prompt for notes/reason

### 3. Offline Mode
- **Turn on Airplane Mode** on your device
- The red "You're offline" bar appears at the top
- Perform any task actions — they work normally!
- Actions are saved locally with "Pending Sync" indicator
- Task status updates are reflected immediately in the UI
- Create a new pickup task via the **+** button — it saves locally

### 4. Sync When Back Online
- **Turn off Airplane Mode**
- The amber "X actions pending sync" bar shows briefly
- Tap it (or the sync icon) to trigger immediate sync
- WorkManager automatically syncs in the background
- Watch the sync status change from pending to synced (green checkmarks)

### 5. Create New Pickup Task
- Tap the **+** floating action button
- Fill in Customer Name, Phone, Address, Description
- Tap **Create** → task appears in the list with "Pending" sync status
- When online, it syncs to the server

### 6. Error/Failure Scenarios
- The mock API always succeeds, but the architecture handles:
  - API failures → retry with exponential backoff
  - Partial sync → successfully synced actions are marked, failed ones retry
  - WorkManager crashes → automatically restarted by the system
  - Check **Logcat** with tags: `SENTRY`, `DATADOG`, `METRICS`, `SyncWorker`, `SyncMonitor` to see monitoring output

### 7. Scale Test
- 1000 tasks are loaded and scrollable in a LazyColumn
- Scroll rapidly through the list to verify smooth performance
- Each page of 50 tasks is fetched and stored incrementally

---

## Project Structure

```
app/src/main/java/com/example/riderapp/
├── RiderApplication.kt          # Hilt Application + WorkManager config
├── MainActivity.kt               # Entry point with Navigation
├── domain/                        # Domain layer (pure Kotlin)
│   ├── model/                     # Task, TaskAction, enums, SyncResult
│   ├── repository/                # TaskRepository interface
│   └── usecase/                   # GetTasks, PerformAction, CreateTask, etc.
├── data/                          # Data layer
│   ├── local/                     # Room DB, DAOs, Entities
│   ├── remote/                    # Retrofit API, DTOs, Mock Interceptor
│   ├── mapper/                    # Entity <-> Domain <-> DTO mappers
│   ├── repository/                # TaskRepositoryImpl
│   └── sync/                      # SyncWorker, SyncManager
├── di/                            # Hilt DI modules
├── monitoring/                    # MonitoringService, SyncMonitor
├── util/                          # NetworkMonitor, Constants
├── presentation/                  # UI layer
│   ├── navigation/                # NavGraph
│   ├── screen/tasklist/           # TaskListScreen + ViewModel
│   ├── screen/taskdetail/         # TaskDetailScreen + ViewModel
│   └── components/                # TaskCard, SyncStatusBar, CreateTaskDialog
└── ui/theme/                      # Material3 Theme, Colors, Typography
```

## Key Technologies
- **Kotlin** + **Jetpack Compose** (UI)
- **Hilt** (Dependency Injection)
- **Room** (Offline SQLite database)
- **Retrofit + OkHttp** (Networking with Mock Interceptor)
- **WorkManager** (Background sync that survives process death)
- **Kotlin Coroutines + Flow** (Reactive async programming)
- **Material3** (Modern Android design system)
