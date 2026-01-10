# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Distance Reporter is an Android app for F-Droid that tracks and audibly reports distance traveled. Target users are walkers, cyclists, and others who move throughout the day.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install to connected device
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests
./gradlew lint                   # Run lint checks
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Navigation**: Compose Navigation
- **Persistence**: Room (daily distances), DataStore (preferences)
- **Location**: Android LocationManager (no Google Play Services for F-Droid compatibility)
- **Text-to-Speech**: Android TTS engine

## Architecture

### Package Structure

- `com.example.distancereporter` - Application class, MainActivity
- `com.example.distancereporter.ui.screens` - Composable screens (Main, Config, Calendar)
- `com.example.distancereporter.ui.navigation` - NavHost and route definitions
- `com.example.distancereporter.ui.theme` - Material 3 theme
- `com.example.distancereporter.data` - Room database, DataStore preferences, data models
- `com.example.distancereporter.service` - Foreground service for location tracking

### Key Components

- **LocationTrackingService**: Foreground service that tracks GPS location, calculates distance, triggers TTS announcements
- **UserPreferencesRepository**: DataStore wrapper for settings (unit, interval, volume, mute/pause state, current distance)
- **DistanceDatabase**: Room database storing daily distance totals for calendar view
- **MidnightResetReceiver**: AlarmManager-triggered broadcast receiver for daily distance reset

### Data Flow

1. `LocationTrackingService` receives location updates from GPS/Network providers
2. Distance increments stored via `UserPreferencesRepository` (DataStore)
3. When interval threshold crossed, TTS announces distance (unless muted)
4. Daily totals persisted to Room database at midnight and on updates
5. UI observes DataStore/Room flows via Compose `collectAsState`

## F-Droid Requirements

- No proprietary dependencies (uses Android LocationManager, not Google Play Services)
- No tracking or analytics
- Fully open source
