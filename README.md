# ShoppingApp

ShoppingApp is a native Android application with a single product search screen. It loads products from a remote API, displays results in a paginated grid, and lets users save favorite product IDs locally.

## Features

- Submit a product search using the search button or keyboard action.
- Browse a two-column grid with automatic loading of additional result pages.
- View product images, names, prices, discounts when available, ratings, and review counts.
- Add or remove favorites, with selections persisted across app launches.
- See loading indicators, empty results, error messages, and retry actions for failed page loads.
- Receive a snackbar message when a favorite update fails.

Product search requires an internet connection. Room stores favorite product IDs locally; product details are loaded from the API.

## Tech stack

| Technology | Version / configuration | Purpose |
| --- | --- | --- |
| Kotlin | 2.2.10 Kotlin JVM / Compose plugins | Application code and Kotlin build tooling |
| Jetpack Compose | BOM 2025.12.00 | Declarative UI, layouts, and previews |
| Material 3 | Managed by Compose BOM | UI components and theming |
| AndroidX Lifecycle / ViewModel | 2.11.0 | Screen state and lifecycle-aware Flow collection |
| Kotlin Coroutines / Flow | 1.10.2 | Asynchronous operations and reactive state |
| Paging 3 | 3.4.2 | Paginated API results and Compose integration |
| Retrofit / Gson converter | 3.0.0 | HTTP API declarations and JSON conversion |
| OkHttp | 4.12.0 | HTTP transport and timeouts |
| Room | 2.8.5 | Local SQLite persistence for favorite IDs |
| Hilt | 2.59.2 | Dependency injection for the app, ViewModel, and data layer |
| Coil | 3.3.0 | Product image loading with placeholders |
| KSP | 2.3.12 | Code generation for Room and Hilt |
| Android Gradle Plugin | 9.4.1 | Android builds |
| Gradle | 9.6.0 | Build automation through the checked-in wrapper |
| ktlint | 1.5.0, Gradle plugin 14.2.0 | Kotlin code style checks |

Dependency versions are centralized in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Architecture

The project separates presentation, domain contracts, and data access into four Gradle modules:

| Module | Responsibility |
| --- | --- |
| `app` | Application entry point, Hilt initialization, main activity, and app theme |
| `feature:search` | Compose search UI, product cards, ViewModel, UI state, events, and effects |
| `domain` | Product model, repository interfaces, and search error types; a Kotlin/JVM module |
| `data` | Repository implementations, Retrofit API, DTO mapping, PagingSource, Room database, and Hilt bindings |

The app wires the modules together. The search feature depends on domain contracts, and the data module implements those contracts.

UI events reach `SearchViewModel`, which exposes screen state through `StateFlow`, paginated products through `Flow<PagingData<...>>`, and one-time effects through `SharedFlow`. The UI collects these with lifecycle awareness. Product results come from the remote repository, while favorite status is combined with locally observed Room data.

The data layer includes simulated 300 ms delays for page loading, initial favorite observation, and favorite writes to make progress indicators visible.

## Getting started

### Requirements

- Android Studio with support for the configured Android Gradle Plugin.
- Android SDK Platform 37. The app uses compile SDK 37 and target SDK 37.
- JDK 25 for the configured Gradle daemon toolchain. Toolchain download URLs are provided in `gradle/gradle-daemon-jvm.properties`.
- An emulator or device running Android 7.0 (API 24) or later.
- Internet access for dependency downloads and product searches.

The modules target Java 11 bytecode; the Gradle daemon uses the separately configured JDK 25 toolchain.

### Run the app

1. Open the repository root in Android Studio.
2. Configure the Android SDK location through Android Studio or an untracked `local.properties` file containing `sdk.dir`.
3. Sync the project with Gradle.
4. Select the `app` run configuration and a compatible emulator or device.
5. Run the app and submit a product query.

The product API base URL is configured in [ProductRetrofit.kt](data/src/main/java/com/standroid/shoppingapp/data/di/ProductRetrofit.kt). The app calls `search` with the `que` and `page` query parameters.

To build a debug APK from PowerShell at the repository root:

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Testing and code style

The project uses JUnit 4, Coroutines Test, Paging Test, and MockWebServer for local tests. Android instrumentation tests use AndroidX Test, Compose UI Test, Espresso, and Room Test.

Existing tests cover product mapping, pagination, search failures, repositories, ViewModel behavior, Room persistence, and the search UI.

Run Android module unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run focused data and search feature tests:

```powershell
.\gradlew.bat :data:testDebugUnitTest :feature:search:testDebugUnitTest
```

Run instrumentation tests with an emulator or device connected:

```powershell
.\gradlew.bat :data:connectedDebugAndroidTest :feature:search:connectedDebugAndroidTest
```

Check Kotlin formatting:

```powershell
.\gradlew.bat ktlintCheck
```

On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`.
