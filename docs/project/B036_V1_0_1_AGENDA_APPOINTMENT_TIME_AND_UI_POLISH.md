# B036 — ServiceLoop 1.0.1 Agenda, Appointment Time, and UI Polish

## Handoff identity

- Repository: `daemon-fr/serviceloop`
- Start SHA: `74c129dfa0c7ec4bf5f7b564498c69d6c0a572d0`
- Branch: `codex/v1.0.1-agenda-polish`
- Version: `versionCode 2`, `versionName "1.0.1"`
- Room schema: unchanged at the active v15 schema
- Release state: development/review branch only; no v1.0 or v1.0.1 tag was created

## Implemented scope

### System bars and shared UI

`ServiceLoopTheme` now resolves the independent ServiceLoop appearance preference and synchronizes both status-bar and navigation-bar icon contrast through the supplied `Window`. The `MainActivity` edge-to-edge setup remains intact, and recomposition updates the window controller for Light → Dark → Light changes without restart.

`ServiceLoopPresetChoiceGroup` now centers each pill's label inside a shared minimum-height container. The common `ServiceLoopFieldAction` is a square 48dp field-adjacent control with the existing focus ring, radius, outline, surface, action icon, and accessible name. The audited production field-adjacent actions are:

- `InspectionChecklistSelector`: plan editor, Create Visit task editor, and Visit ad-hoc task editor; all route through the shared action and generated `ServiceLoopIcons.PlusBold`.
- `VisitDateInput`: the Create Visit appointment-date picker uses the same field action and the Calendar icon.
- `VisitTimeInput`: Create Visit, reschedule, and restore use the same field action and the Time icon.

Unrelated Calendar uses remain navigation/status icons in the operational dashboard and Settings/Calendar entry points; they are not field-adjacent date pickers and were intentionally not changed. Other ordinary Add icons remain full-width command leading icons, not selector actions.

### Create Visit and follow-ups

Create Visit uses the existing Existing/New tabs and now has a shared `Set up visit` section stripe in both modes. Appointment date and optional time, task creation, task listing, and final visit actions are separate logical list sections governed by the existing spacing tokens. Follow-up Contact and Corrective are an equal-width paired choice on one row, including at the supported large-font compact-width case.

### Appointment time

The existing `scheduledAtEpochMillis` and `appointmentZoneId` fields are reused. A selected local `HH:mm` is combined with the existing ServiceLoop business date and business zone; an unset time persists as `null`. `Book visit` uses the value, while `Start now` and `Record past visit` intentionally pass no appointment timestamp. The time control has a native simple picker, clear affordance, no seconds, and unsaved-form protection.

Booked Visit rescheduling prefills an existing time and retains it when only the date changes; explicit Clear passes `null` and records the old/new timestamp transition. Restore accepts an optional time and date-only restore passes `null`, replacing the previous start-of-day surrogate. Visit detail and Agenda display a time only when a real timestamp exists. Reminder/calendar scheduling continues to consume the existing timed-Visit truth; no new reminder or calendar model was introduced.

### Home Agenda

Home keeps the existing Dashboard content and defaults to `Dashboard` in memory. `Agenda` is a second Home tab, not a bottom-navigation destination or a new scheduler.

The read-only projection includes:

- unresolved Visits in `BOOKED` or `WORKING` state whose service/appointment date is before the business date;
- open Follow-ups due before the business date;
- current outstanding due-service obligations before the business date;
- actionable Visits, open Follow-ups, and current due-service obligations dated today or later in Upcoming.

Completed/canceled Visits, resolved/closed Follow-ups, and invalid dates are excluded. Each row has the date, optional real appointment time, concise type, identity/context, a trailing disclosure caret, and a whole-row route to `visit/{id}`, `follow-up/{id}`, or the existing `plan/{id}` destination. Rows use max two lines with ellipsis only beyond line two and no per-item card or action-button stack.

Sorting is deterministic: date ascending; on a shared date, timed Visits first in local business-zone time; untimed Visits and other dated work follow; kind, reference, and record ID provide stable tie-breakers. The Visits work queue applies the same timed-first same-date rule while preserving its existing date/status direction.

## Verification

### Automated evidence

- `:app:testDebugUnitTest` — PASS, 427 tests, 0 failures, 0 skipped.
- `:app:assembleDebug` — PASS.
- `:app:assembleRelease` — PASS.
- `:app:lintDebug` — PASS.
- `:app:assembleDebugAndroidTest` — PASS.
- `git diff --check` — PASS at the final source/docs checkpoint.

Focused JVM coverage includes business-zone epoch composition, date-only null behavior, Agenda classification/exclusion/sorting, Visit queue ordering, and Room repository create/reschedule/clear/restore timestamp and schedule-event provenance. Focused Android UI coverage includes Agenda sections/navigation, field-action geometry and content descriptions, large-font paired choices, preset-pill geometry, and runtime system-bar contrast.

### Canonical AVD evidence

The existing AVD named `Pixel_10a_ServiceLoop` (display name `Pixel 10a ServiceLoop`) was started without wipe/recreation. Its serial was resolved dynamically as `emulator-5554`; the physical Pixel 6 Pro serial was not used. The debug APK was built first and explicitly installed with:

`adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`

The assembled Android-test APK was explicitly installed, and the focused B036 Agenda, field-action/follow-up, system-bar, and preset instrumentation run passed 5/5. The system-bar test observed Light → Dark → Light runtime changes and both status/navigation controller flags.

Opt-in MainActivity render evidence passed for the light and dark Home/Work/Settings/Reminder surfaces. Additional canonical screenshots were inspected for light and dark populated Home Agenda, showing `Unresolved (9)`, dense two-line rows, trailing carets, and light/dark system-bar icon contrast. No screenshots or ADB logs are retained in the repository.

## Known limitations and non-goals

- Agenda is a current-work projection, not a conflict detector, duration planner, availability engine, route planner, or calendar grid.
- No backend, account, team sync, dispatch expansion, billing, inventory, customer portal, or new Room scheduling column was added.
- Existing Android Calendar provider mutation behavior remains outside this pass; the appointment-time truth continues to flow through the existing scheduling/calendar integration.
- Release/tag promotion and protected `master` integration are intentionally outside B036.
