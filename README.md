# TechFix Smart App

A native Android application for a tech-repair shop chain. Customers browse services and spare parts, book repair appointments, attach photos of device faults, and track repair status. Staff manage the service queue, catalogue, inventory, and users from an admin console.

The app is offline-first: all data is stored in a local SQLite database and synced to Firebase when connectivity is available.

## Features

- Authentication with role-based access (Customer / Staff) and salted SHA-256 password hashing
- Session persistence across app restarts
- Store front with swipable hero banner carousel, service catalogue, and spare-parts listing
- Branch directory with distance from the device location
- Repair booking flow with technician availability, time slots, branch selection, and damage photo attachment
- Repair tracking with full status-history timeline and payment summary
- Staff console with dashboard stats, live queue management, catalogue CRUD, inventory CRUD (auto stock decrement), and a user directory
- Offline-first sync: remote data pulled and cached locally; writes queued and pushed when online

## Tech Stack

- Android SDK (Java 11), minSdk 24, targetSdk 37
- Material Components, Navigation Component, RecyclerView, ViewBinding
- SQLite with a DAO layer and Content Provider
- Firebase (Auth, Firestore, Analytics)
- Google Play Services Location (FusedLocationProviderClient)
- FileProvider for camera image capture

## Project Structure

```
activities/   Splash, Login, Home, Customer, Staff, AppointmentDetail
fragments/    Staff console tabs: Overview, Queue, Catalog, Inventory, Admin
adapters/     RecyclerView adapters for all lists
database/     DatabaseHelper, DAO layer, TechFixContentProvider
models/       Appointment, Payment, User, Technician, SparePart, etc.
session/      SessionManager (SharedPreferences)
sync/         FirebaseSyncManager (offline-first push/pull)
util/         Location, analytics, network, and threading helpers
```

## Setup

1. Clone the repository.
2. Open in Android Studio.
3. Add `app/google-services.json` from your Firebase project (this file is intentionally excluded from version control).
4. Sync Gradle and run the app on a device or emulator.

## Build

```
./gradlew assembleDebug
```

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
