# ByaHero: Project Standards & Architecture

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Backend/DB:** Supabase (Auth, Realtime, PostgreSQL)
- **Architecture:** MVVM + Clean Architecture
- **DI:** Hilt (Recommended for modularity)

## IDE Choice: Android Studio
**Recommendation: Use Android Studio.**
While VS Code is lightweight, Android Studio provides:
- First-class Compose support (Live Edit, Layout Inspector).
- Deep Gradle integration for multi-module projects.
- Native Hilt/Dagger support and refactoring tools.
- Integrated Profilers (essential for optimizing for slower phones).

## Backend: Kotlin & Supabase
- **Kotlin Backend:** Kotlin does not have a "built-in" backend in the standard library, but **Ktor** is the official Jetpack-style framework for building asynchronous servers in Kotlin.
- **Supabase:** Excellent choice. Its **Realtime** capabilities are perfect for tracking Jeepneys. The Kotlin SDK is mature.

## Scalable Modular Architecture
The project will follow a **Feature-based Multi-module** structure:
- `:app`: Navigation, DI setup, and app entry point.
- `:core:common`: Shared utilities, constants, and extensions.
- `:core:ui`: Theme, reusable Compose components (Design System).
- `:core:data`: Supabase client, local Room DB, and shared repositories.
- `:feature:map`: Tracking logic, Google Maps integration.
- `:feature:search`: Searching for routes/jeepneys.
- `:feature:auth`: User login/signup with Supabase.

## Performance Optimization (Lower-end Devices)
- **Baseline Profiles:** Mandatory for reducing startup time.
- **R8/ProGuard:** Aggressive shrinking to keep the APK small.
- **Compose Stability:** Avoid unnecessary recompositions using `@Stable` and `@Immutable`.
- **Image Loading:** Use Coil with low-memory configurations.
- **Network:** Use Supabase's offline-first capabilities where possible.
