# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cyface Android Backend SDK -- a multi-module Android library providing sensor data capturing, local persistence, and synchronization to a Cyface Collector server. Apps integrate this SDK to capture accelerometer, gyroscope, GPS, and pressure data via a foreground service, store it locally in Room/SQLite + binary files, and upload it over WiFi.

## Build Commands

Requires `gradle.properties` with GitHub Packages credentials (copy from `gradle.properties.template` and fill in `githubUser`/`githubToken`).

```bash
./gradlew build                    # Build all modules + run unit tests
./gradlew assemble                 # Build without tests
./gradlew :persistence:test        # Unit tests for one module
./gradlew :persistence:test --tests "de.cyface.persistence.SomeTest"           # Single test class
./gradlew :persistence:test --tests "de.cyface.persistence.SomeTest.someMethod" # Single test method

# Connected/instrumented tests (require emulator or device)
./gradlew :persistence:connectedDebugAndroidTest
./gradlew :datacapturing:connectedDebugAndroidTest
./gradlew :synchronization:connectedDebugAndroidTest
```

CI uses JDK 21 (Temurin) and Android API 28 emulator for connected tests. Tests annotated `@FlakyTest` are excluded in CI.

## Modules

| Module | Purpose |
|--------|---------|
| `persistence` | Room database (SQLite) + binary file storage for sensor data. No dependencies on other modules. |
| `datacapturing` | Foreground service controlling sensor/location capture lifecycle. Depends on persistence and synchronization. |
| `synchronization` | SyncAdapter-based upload to Cyface Collector API with OAuth2 (AppAuth). Depends on persistence. |
| `testutils` | Shared test fixtures used across module test suites. |

## Architecture

### Multi-Process Design

The SDK runs across isolated Android processes to prevent crashes in one component from affecting others:
- `:capturing_process` -- `DataCapturingBackgroundService` (sensor capture)
- `:persistence_process` -- `StubProvider` ContentProvider for database access
- Sync process -- `CyfaceSyncService` (upload, must be declared by integrating app's manifest)

Communication between processes uses Android `Messenger` IPC.

### Data Flow

1. `DataCapturingService` (client API) starts/stops `DataCapturingBackgroundService`
2. Background service uses `CapturingProcess` implementations (e.g., `GeoLocationCapturingProcess`) to collect sensor data
3. Data is persisted via `PersistenceLayer` -> Room DAOs + binary `.cyfa`/`.cyfr`/`.cyfd` files (Protobuf format)
4. `SyncAdapter` serializes measurements into `.ccyf` compressed format and uploads via `DefaultUploader`

### Key Abstractions

- **`PersistenceLayer<B>`** -- Generic persistence interface; the type parameter `B` allows injecting custom `PersistenceBehaviour` (strategy pattern)
- **`EventHandlingStrategy`** -- Customizes notifications and event behavior per integrating app
- **`LocationCleaningStrategy`** / **`DistanceCalculationStrategy`** -- Pluggable algorithms for GPS filtering and distance computation

### Room Database

- Database name: `measures`, currently at **version 20**
- Entities: `Identifier`, `Measurement`, `Event`, `GeoLocation`, `Pressure`, `Attachment`
- Schemas exported to `<module>/schemas/` directories (checked into git, used as test assets for migration testing)
- Migrations defined in `DatabaseMigrator.kt`

### Sensor Data Storage

Sensor data is stored as Protobuf binary files alongside the Room database:
- `*.cyfa` -- accelerometer
- `*.cyfr` -- gyroscope
- `*.cyfd` -- magnetometer

## Key Version Constraints

- **Kotlin 2.0.21** must stay in sync with KSP version (`2.0.21-1.0.28`)
- **Room 2.6.1** is declared in three places that must stay synchronized: `buildscript.ext`, `plugins {}`, and `ext {}` in root `build.gradle`
- **Java 21** target compatibility across all modules
- **Min SDK 26**, compile/target SDK 35

## Cyface Library Dependencies

- `android-utils` (5.0.1), `serialization` (4.1.12), `uploader` (1.6.2) -- pulled from GitHub Packages
- These versions should be kept in sync with `android-app` and `camera-service` when used as submodules
- Collector compatibility: version 5

## Publishing

Version is automatically set from git tags by CI. Tag format: `X.Y.Z` (append `test`/`alpha`/`beta` for pre-releases). `./gradlew publishAll` publishes all modules to GitHub Packages.

## Commit Style

`[TICKET-ID] Short imperative summary`. Ticket prefixes vary: `LEIP-###` (current primary), `CY-####`, `STAD-###`.
