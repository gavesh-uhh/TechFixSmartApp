# TechFix UI Rehaul — v2 Design Direction
**Design lead brief:** Modern, clean, light theme, navy hint. The previous pass was a reskin ("meh") because it only swapped colors on old layouts. This version redefines **structure, elevation, insets, and one signature element** — then applies tokens everywhere.

**Prerequisite fix before any of this:** `activity_customer.xml` is currently malformed (view-binding crash: "root is null" + BOM). Restore it first — see Recovery at the bottom.

---

## 1. Grounding
- **Subject:** a repair bench. Every repair that enters TechFix gets a **job docket** — that paper slip is the most authentic artifact in this world, and customers already know it.
- **Audience:** walk-in phone/laptop owners in Colombo & Galle, checking "is it fixed yet, what does it cost."
- **Each screen's single job:** customer screens answer *"what happens to my device next"*; staff screens answer *"what do I do next."*

## 2. Signature element — the Job Docket card
Every appointment renders as a **workshop docket**: white card, top row carries a **monospace job number** (`#000123`, IBM Plex Mono, navy) like a stamped ticket, a dashed perforated-style divider above the meta section, and a **status stamp** chip (uppercase, letterspaced, tinted). The detail screen extends this: the timeline reads as the docket's progress stamps. This is the one memorable device; everything else stays quiet. (Deliberate aesthetic risk: utility-print styling inside a clean light app — justified because job numbers are real data users quote at the counter.)

## 3. Token system

### Palette (light, navy-hinted)
| Token | Hex | Use |
|---|---|---|
| `ink` | #0B1D33 | primary text |
| `navy` | #1B3A5C | primary buttons, active states |
| `steel` | #5B7A9D | icons, secondary accents |
| `paper` | #F6F8FA | screen background |
| `card` | #FFFFFF | all cards |
| `line` | #E3EAF2 | hairlines, dashed dividers |
| `muted` | #64748B | secondary text |
| `ok/warn/bad` | #15803D · #B45309 · #B91C1C (+12% container tints) | status stamps only |

### Type pairing (deliberate)
- **IBM Plex Sans** (bundled) — all prose/UI text
- **IBM Plex Mono** (new) — job numbers, prices, timestamps, coordinates. Mono data on sans prose is the workshop vernacular.

Scale: Display 28/34 · Headline 22/28 · Title 17 SemiBold · Body 14/20 · Caption(Mono) 12/16 · Stamp 11 Medium, letterSpacing 0.08em.

### Elevation — modern soft shadows
Kill heavy Material ambient shadows. Two tiers:
- **E1** cards/list items: elevation 3dp, radius 16dp, navy-tinted shadow @8% (`Widget.TechFix.ShadowCard`)
- **E2** dialogs/sheets: elevation 8dp
Rule: one elevated layer per region — cards float off paper; buttons are flat navy fills, never raised.


### Spacing & insets (fixes the NotchBar collision)
- Root of **every** activity: `android:fitsSystemWindows="false"` + insets consumed manually.
- Rework `WindowInsetsHelper`: `applyHeader(header)` pads the header band by **statusBars inset height**; `applyBody(body)` pads bottom by navigationBars inset. Every activity calls both. No interactive content inside the status-bar zone.
- **De-top-centric layout:** header bands shrink from 112–120dp → **64dp content + inset**; big titles move *into the scrollable body* (large-title pattern) so nothing crowds the notch. Headers keep only a back/nav slot + compact wordmark right-aligned.
- Grid: 4dp base; screen padding 16dp; section gap 24dp; card gap 12dp; card padding 16dp.

## 4. Screen blueprints

```
Customer dashboard            Staff workspace
┌──────────────────────────┐  ┌──────────────────────────┐
│ (inset)        TF ⚙ out │  │ (inset)         STAFF ⎋  │
│                          │  ├──────────────────────────┤
│ Your repairs ── large    │  │ QUEUE            6 open  │
│ title, scrolls away      │  │ ┌─ docket ┐ ┌─ docket ─┐│
│ [Book][Track][Explore]   │  │ └─────────┘ └──────────┘│
│ pill tabs under title    │  │ Operations cards…        │
└──────────────────────────┘  └──────────────────────────┘

Detail = full docket: number + stamp header, dashed rule,
vertical stepper timeline, payment footer in safe area.
```
- **Dashboard:** large title in body ("Your repairs"), pill tab row *under* it, Book form as one docket card, Pay action docked to a bottom sheet instead of an inline button.
- **Login:** centered card with a quiet ghost docket-number print behind it ("TF·0001" @4% opacity) — brand echo, not decoration.
- **Staff:** queue dockets get a left 3dp status stripe; operations grouped under SectionTitles.

## 5. Copy pass
Buttons say what they do: "Book repair", "Pay Rs X now", "Mark ready for collection". Empty states direct: "No repairs yet — book your first repair and track it here." Errors give cause + fix: "Enter an appointment ID — find it on the queue card."

## 6. Rollout (each step must green-build) — ✅ COMPLETE (builds + tests green)
| Phase | Work | Files |
|---|---|---|
| 0 | Fix malformed `activity_customer.xml`, strip BOMs | layouts |
| 1 | Insets rework (`WindowInsetsHelper` + call sites), header shrink | helper + 6 activities |
| 2 | Tokens: mono font, palette, ShadowCard style, Stamp style | font/, colors, themes |
| 3 | Docket component shared by customer list & staff queue | adapters + item layout |
| 4 | Detail-screen stepper + payment footer | detail layout/activity |
| 5 | Login/Home/Splash polish, empty states, copy pass | remaining layouts |

## Recovery notes (prerequisite)
`activity_customer.xml` lost its root element during the agent edit. Restore root `<LinearLayout id=customerRoot>` wrapping `customerHeader`, `dashboardTabs`, `dashboardContent` (bookPanel/repairsPanel/explorePanel/loadingSkeleton), preserving all bound ids; strip BOM; green-build before Phase 1.
