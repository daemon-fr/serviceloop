# B-028 — Navigation, Work actions, and Customer-type UX

**Status:** IMPLEMENTED on `codex/b013-ui-overhaul`
**Starting SHA:** `938db94a90d93ce6c94493b6ecff1b22c5de0b1b`
**Scope:** navigation affordances, Calendar-off remedy, Visit Progress numbering, Service Plan spacing, shared Work New-visit docking, and consistent Standard/One-time customer UX.

## Navigation affordances

ServiceLoop now has one reusable `ServiceLoopNavigationButton` primitive. It keeps the existing secondary-button hierarchy while adding the Phosphor disclosure icon as a decorative trailing affordance inside the same touch target. Navigation-only relationship and information gateways use it; business-effect commands do not.

Converted controls include Visit Customer/Site, Equipment Customer/Site/history, Site Customer/history, Customer history, and comparable information gateways. Edit, Add, Create visit, Start, Book, Save, Retry, Restore, lifecycle commands, external Call/SMS/Email/Maps actions, and Calendar provider actions remain command or handoff controls without a disclosure. The duplicate content-level `Back to visit` action was removed; the standard top detail Back remains the single backward affordance.

## Calendar and Visit Progress

When Calendar integration is disabled, Visit detail now says exactly `Calendar integration is off` and exposes `Calendar settings` as an internal navigation control. Add, Recreate, Remove, and Open Calendar behavior remains unchanged.

The shared Visit Progress row renders the stable `ServiceProgressItem.position`, so numbering remains Visit-level across equipment/site groups and is also exposed in the row’s accessible text. Service Plan detail has a normal large token gap before its existing action stack; button order and behavior are unchanged.

## Work New visit action

Due services, Visits, and Follow-ups use the same narrow `rememberWorkNewVisitDocked` controller and `WorkNewVisitAction` component. Each list always reserves its natural bottom slot. Before docking the slot is a non-clickable layout placeholder, so it contributes geometry without adding an invisible accessibility action.

- Short or filtered-short lists start docked with one full-width `New visit` action.
- Long lists start with a compact `+ New visit` floating action.
- The floating action sits above persistent Due-services Book/Start actions and above list/system insets for Visits and Follow-ups.
- When the reserved slot becomes visible, the state makes one `FLOATING → DOCKED` transition with a restrained fade/vertical translation; the floating action disappears.
- Docking is one-way for that screen instance. Scrolling upward or lengthening a filter does not restore the floater; leaving and re-entering creates a fresh screen instance.

## Customer type UX

Create Visit now has the exact top-level modes `Existing` and `New`. Existing preserves current deliberate customer/site discovery, including allowed ONE_TIME search/reveal behavior. New collects customer identity and default-site information, plus the exact checkbox `One-time customer (no contract)`, unchecked by default.

New Visit creation uses a generic transactional `createNewCustomerVisit` input. Checkbox-off creates a persisted `STANDARD` Customer, default Site, Visit, and ad-hoc tasks; checkbox-on creates the same durable structure with `ONE_TIME`. Register Add Customer + first Site uses the same checkbox and persists the selected type. Existing one-time filtering and explicit reveal behavior remain intact.

Edit Customer uses the same checkbox. ONE_TIME → STANDARD is supported without replacing Sites, Equipment, or history. STANDARD → ONE_TIME is supported when the customer has no ServicePlan rows. The repository re-reads current state inside the write transaction and rejects STANDARD → ONE_TIME when any plan exists, including ACTIVE, PAUSED, or ENDED plans, before mutating the customer. The exact UI explanation is `This customer has recurring service plans and cannot be marked one-time.`

The old production `Make Standard` promotion action was removed from Customer and Equipment UI. The compatibility repository method remains deprecated for older Dispatch/test callers, while the authoritative edit path is `CustomerInput.customerType`.

## Verification

Local gates completed successfully:

- `:app:testDebugUnitTest` — PASS (**410 tests, 0 failures, 0 skipped**)
- `:app:assembleDebug` — PASS
- `:app:assembleRelease` — PASS
- `:app:lintDebug` — PASS
- `:app:assembleDebugAndroidTest` — PASS
- `git diff --check` — PASS at final gate

The debug APK and Android test APK were built first and installed explicitly with `adb -s emulator-5556 install -r` on the dynamically resolved `Pixel 10a ServiceLoop` AVD. The focused connected suite ran directly on that AVD, avoiding the Gradle connected-runner path that also discovered the attached physical phone:

- `B026LocalFlexibleWorkUiTest` + `ServiceWorkspaceCoreUiTest` — **32/32 PASS**

Coverage includes disclosure semantics, Calendar-off settings navigation, shared Work docking, Due-services action placement, Existing/New mode behavior, new Standard/One-time creation, Register/Edit type controls, plan blocking, Back-stack behavior, Visit Progress numbering, and Service Plan spacing. A broader exploratory probe also exposed two unrelated legacy Completion fixture failures; it is not claimed as task acceptance evidence.

One first aggregate final-gate invocation encountered a pre-existing nondeterministic Dispatch history-order assertion; the exact test passed in isolation and the subsequent full 410-test run passed. No B-028 test or production assertion failed in the final run.

## Rendered evidence

Human/Rendered inspection was performed from screenshots on the canonical AVD at the normal 1080×2424 portrait size in Light appearance for Home, Work Due (long/floating above persistent actions), Work Visits (short/docked), Create Visit Existing, Create Visit New, and the Service/Visit Progress surface. Dark appearance was inspected on the New-customer form. A temporary approximately 320dp-width / 2.0-font-scale dark check was also performed, then display size, font scale, and appearance were restored to canonical values.

The rendered checks confirmed the New Visit action hierarchy, Existing/New labels, long checkbox copy, real disclosure-style navigation treatment, top-level Back without a content-level duplicate, and stable numbered Visit Progress rows. Calendar provider mutation and full Calendar settings state coverage remain outside this pass’s human-rendered evidence because the preserved canonical dataset has Calendar integration disabled and no disposable writable provider fixture; the off-state remedy is covered by focused instrumentation.

## Known remaining issues

No task-scoped implementation issue remains known. Real Calendar Provider mutation remains the pre-existing verification boundary documented in the implementation state, and B-008 pilot evidence remains outside this engineering pass.
