# TechFix Smart App — Project Context

## Overview
**TechFix Smart App** is a native **Android (Java)** mobile application for a tech-repair shop chain. It digitises the full repair workflow: customers browse services & spare parts, book repair appointments at a branch, attach photos of the fault, and track their repair status — while staff manage the service queue, catalogue, inventory, and users from an admin console. The app is **offline-first**: all data lives in a local SQLite database and syncs to Firebase when connectivity is available.

- **Package:** `com.techfix.app` · minSdk 24 → targetSdk 37 · Java 11 · ViewBinding

## Problem Statement
Walk-in-only repair shops have no visibility for customers (no booking, no status tracking) and rely on paper/manual processes for queues, stock, and records. TechFix centralises this into one app with role-based access.

## Users & Roles
| Role | Access |
|------|--------|
| **Guest** | Browse store front (services, spare parts, branches) |
| **Customer** | Book appointments, upload damage photos, track repairs, pay |
| **Staff/Admin** | Manage appointment queue, catalogue, inventory (stock decrement), technicians, users |

## Key Features
- **Authentication** — login/signup with salted SHA-256 password hashing; session persistence (`SessionManager`); role-based navigation.
- **Store front** — swipable hero banner carousel, service catalogue, spare-parts listing, branch directory with distance from device location.
- **Booking flow** — pick service, technician availability, time slot & branch; attach damage photo via camera (`TakePicture` + `FileProvider`).
- **Repair tracking** — per-user appointment list; `AppointmentDetailActivity` with full status-history timeline and payment summary (Cash/Card).
- **Staff console** — tabbed dashboard: Overview stats, live Queue with status updates (history appended per change), Catalogue CRUD, Inventory CRUD (auto stock decrement on bookings), Admin user directory.
- **Offline-first sync** — remote data pulled and cached into SQLite on start; writes queued and pushed when online.

## Architecture & Structure
```
activities/   Splash, Login, Home, Customer, Staff, AppointmentDetail
fragments/    StaffTabHost: Overview, Queue, Catalog, Inventory, Admin
adapters/     RecyclerView adapters (appointments, banners, branches,
              services, spare parts, technicians, sample images)
database/     DatabaseHelper + DAO layer (User, Appointment, Service,
              Branch, Technician, SparePart, SampleRepair) +
              TechFixContentProvider
models/       Appointment, Payment, User, Technician, SparePart, ...
session/      SessionManager (SharedPreferences)
sync/         FirebaseSyncManager (offline-first push/pull)
util/         NearestBranch (FusedLocation), Analytics, Feedback,
              NetworkUtils, AppExecutors (background DB threads)
```

## Deliverables Covered (module criteria)
| # | Deliverable | Implementation |
|---|-------------|----------------|
| 1 | **SQLite persistence** | Full DAO layer, v4 schema with proper `ALTER TABLE` migrations |
| 2 | **Complex data model & adapters** | 10+ related tables/models; `RecyclerView` + `ListAdapter`/DiffUtil throughout |
| 3 | **Locations / GPS** | `FusedLocationProviderClient`, runtime permission, nearest-branch selection, `geo:` intents |
| 4 | **Camera & images** | Runtime `CAMERA` permission, `ActivityResultContracts.TakePicture`, `FileProvider`, image gallery grid |
| 5 | **Web services / remote data** | Firebase (Auth/Firestore/Analytics) pull-cache-push sync; app usable fully offline |
| 6 | **Content Provider** | `TechFixContentProvider` exposing app data |

## Team Split (one UI each + deliverables)
| Member | Responsibility | UI(s) | Deliverable(s) |
|--------|----------------|-------|----------------|
| **Gavesh Saparamadu** | Workspace setup & foundation | Splash, Login | SQLite, Session mgmt |
| **Yashan Perera** | Store front experience | Home (carousel, services, parts, branches) | GPS/Location, catalog queries |
| **Timesh Dillon** | Booking flow | Customer, Appointment Detail | Camera, Notifications, AppointmentDAO |
| **Siluna De Silva** | Staff console | Staff Activity + 5 admin fragments | Content Provider, Firebase sync |

## Tech Stack
Android SDK (Java) · Material Components · Navigation Component · RecyclerView · ViewBinding · SQLite · Firebase BoM (Auth, Firestore, Analytics) · Google Play Services Location · FileProvider/CameraX-style intents
