# TechFix Smart App — Implementation Plan

> **STATUS: All phases implemented and validated (`assembleDebug` + `testDebugUnitTest` pass).**
> Note: `JAVA_HOME` on this machine is broken — use `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'` before `gradlew`.


## Phase 0 — Foundations (models, enums, session)
**Goal:** Replace string-driven logic with a proper model layer and add login sessions.

1. Create model classes in `model/`: `Branch`, `Service`, `Technician`, `SparePart`, `SampleRepair`, `User`, `Appointment` (rework: add `userId`, `createdAt`, `timeSlot`).
2. Create enums/constants: `AppointmentStatus`, `PaymentStatus`, `UserRole`.
3. Create `SessionManager` (SharedPreferences) — store logged-in `userId` + `role`; add `logout()`.
4. Bump `TechFixDatabase` to v4: add `user_id`, `created_at`, `time_slot` columns to `appointments`; add `role` to `users`; add `status_history` and `payments` tables (Phase 2/3 use them).
   - Replace drop-everything `onUpgrade` with proper `ALTER TABLE` migrations.

**Deliverable check:** SQLite ✔ (improved), Complex Data Model ✔ (started).

---

## Phase 1 — Auth & per-user history (fixes biggest correctness gap)
1. Wire `LoginFragment` → `SessionManager` (persist login on success; check session in nav start so users stay logged in).
2. Move staff credentials into the `users` table with `role = STAFF`; remove hardcoded check.
3. `db.add(...)` records the logged-in `userId`; all customer queries filter `WHERE user_id = ?`.
4. Add password hashing (simple SHA-256 salted) — plaintext passwords are unacceptable even for a coursework app.

---

## Phase 2 — RecyclerView adapters + repair tracking UI (Complex Data Model & Adaptors deliverable)
1. Add `RepairStatus` model + `status_history` table usage; staff status updates append a history row instead of overwriting.
2. Build `AppointmentAdapter` (RecyclerView + ListAdapter/DiffUtil) replacing the programmatic TextViews in `CustomerFragment`.
3. New **AppointmentDetailFragment**: full repair info + vertical status timeline (history rows) + Pay button targeting *that* appointment.
4. Split customer Repairs tab: "Active" vs "History" (status = Completed) via simple filter chips or tabs.
5. Add `Payment` model + `payments` table usage; Pay flow = choose method (Cash/Card) → insert payment row → mark appointment paid; show payment summary on detail screen.

**Deliverable check:** Complex Data Model & Adaptors ✔.

---

## Phase 3 — GPS / Maps (Locations deliverable)
1. Runtime permission request for fine/coarse location (`ActivityCompat.requestPermissions`).
2. Get real device location via `FusedLocationProviderClient` (add `play-services-location` dependency) with Colombo fallback if denied.
3. Pass real lat/lng into `branchFor(...)`; show chosen branch + distance on booking confirmation.
4. Branch info screen: list both branches with "View on map" (`geo:` implicit intent per branch, not just Colombo).

**Deliverable check:** Locations / Map GPS ✔.

---

## Phase 4 — Camera & sample images (Camera deliverable)
1. Proper camera flow: `registerForActivityResult(ActivityResultContracts.TakePicture)` with a `FileProvider` URI — the current bare intent discards the photo.
2. Add `CAMERA` runtime permission handling.
3. Customer: attach damage photo to a booking (store URI on `appointments`).
4. Staff: upload "after repair" sample images → saved to `samples` table.
5. Explore tab becomes a real gallery: `SampleImageAdapter` (RecyclerView grid) loading URIs.

**Deliverable check:** Camera & Image Integrations ✔.

---

## Phase 5 — Web services & remote data (Web Services deliverable)
1. Stand up (or simulate) a simple REST endpoint; extend `RemoteService` with POST + JSON parsing (`org.json` — already in Android).
2. Sync strategy: pull services/parts/branch data on app start → cache into SQLite; app remains fully usable offline (offline-first satisfies the Offline Application deliverable too).
3. Push new appointments/statuses when online; queue when offline.

**Deliverable check:** Web Services & Remote Data ✔ + offline ✔.

---

## Phase 6 — Staff/management completeness
1. Full CRUD screens for services, technicians, spare parts, device categories (add `DeviceCategory` model + table).
2. Decrement spare-part quantity when a booking uses a required part; warn if stock = 0.
3. Staff dashboard: list all appointments with filters (branch/status); technician availability toggle already exists in DB — expose it in UI.
4. Run DB writes off the main thread (simple `ExecutorService`).

---

## Suggested order & rationale
Phases 0–2 first: they fix correctness (sessions, per-user data, payments) and bank the easiest deliverable (Adapters). Phase 3–4 are independent and can be split between team members (one UI per member requirement). Phase 5 last since it depends on stable local schema.

## Team member split (one UI each, per methodology requirement)
- Member A: Login + Session (Phase 1)
- Member B: Booking + Appointment detail/timeline (Phases 0–2)
- Member C: Branch/Maps (Phase 3)
- Member D: Camera/Explore gallery (Phase 4)
- Member E: Staff dashboard + CRUD (Phase 6)
