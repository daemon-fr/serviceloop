# B037 / ServiceLoop 1.0.1 owner-review UI corrections

Date: 2026-09-18
Branch: `codex/v1.0.1-agenda-polish`
Starting revision: `eea7ca54e7a66d79e29fd7d4def2ebd800d18489`

## Scope

This checkpoint implements the owner visual-review corrections for the ServiceLoop 1.0.1 agenda/visit polish. It remains development-branch work: versionCode is 2, versionName is `1.0.1`, Room schema is v15, no release tag was created, and protected `master` was not changed.

## Implemented corrections

- Plan interval units are one compact equal-height horizontal band on ordinary-width screens, with a narrow/large-font fallback that preserves readable 48dp targets. The vertical editor rhythm is explicitly balanced around the interval row.
- `ServiceLoopFieldAction` is now a solid light-teal/dark-teal semantic selection treatment with teal icon ink, the shared field radius, no decorative border, an accessible 48dp target, focus ring support, and the shared field/action gap. Inspection template, visit date, and visit time actions use the same primitive.
- Create Visit uses plain page-background headings with exact `Choose a customer` and `Set up visit` copy. The obsolete Appointment section heading and its stripe treatment are gone.
- Date and optional time use one compact editable field family. Date keeps the weekday inside the field, centers the date value, retains calendar picking, and accepts manual `YYYY-MM-DD` edits. Time is a real editable `HH:mm` field, permits blank, validates strictly, retains time picking, and clears by deletion. Reschedule and restore use the same family; read-only visit detail remains read-only.
- New Visit back protection compares a meaningful draft snapshot to its initial baseline. Mode changes, customer/site search, focus, and picker dismissal do not dirty the form. Restored date/time values and route-preselected site/plan values remain clean.
- Only the requested Follow-up explanatory sentence was removed. Contact/Corrective choice semantics, private content, corrective/contact outcome rows, and historical behavior remain unchanged.

## Regression coverage

- `B037OwnerReviewCorrectionsTest`: strict optional-time parsing, invalid-time rejection, draft dirty/restoration semantics, and source contracts for headings, shared action treatment, and removed copy.
- `ServiceLoopPresetChoiceLayoutTest`: interval units remain one equal-height horizontal band and large-font compact choices retain their fallback behavior.
- `B036FieldActionUiTest`: shared action geometry plus editable date/time height and manual editing/clearing.
- `B037OwnerReviewRenderTest`: light/dark rendered Plan Editor, Create Visit, appointment, and Follow-up surfaces; meaningful-draft back guard; IME-aware back dispatch; route preselection baseline.
- Existing B026 Create Visit journeys were rerun for due-service failure, existing/new mode and customer-type behavior, and new-customer save behavior.

## Evidence

The canonical AVD was resolved dynamically from the display name `Pixel 10a ServiceLoop` on each device-directed run; the resolved serial was `emulator-5554`. The rebuilt debug APK and Android-test APK were installed explicitly with `adb -s <resolved-serial> install -r`.

Observed focused device results:

- `B036FieldActionUiTest`: 3/3 PASS.
- `ServiceLoopPresetChoiceLayoutTest`: 2/2 PASS.
- `B037OwnerReviewRenderTest`: 2/2 PASS.
- Existing B026 Create Visit methods: 3/3 PASS.
- Fresh light and dark screenshots were captured from the owner-review render test and visually inspected after adding the app canvas/system-bar surface to the render harness.

The full JVM unit report at the latest completed run was 431 tests, 0 failures, 0 errors. `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug`, `:app:assembleDebugAndroidTest`, and `git diff --check` passed after the last source/test edit. Final branch/remote parity and protected-branch checks are recorded in the task handoff.

## Boundary

This is not a release claim and does not include a release tag, master merge, backend, accounts, team sync, dispatch, invoicing, accounting, inventory, or customer portal work.
