# ServiceLoop — B030 template editor polish, Work actions, and navigation layout

**Status:** IMPLEMENTED on `codex/b013-ui-overhaul`
**Date:** 2026-09-17

## Scope

B030 polishes the inspection-template editor, stabilizes the shared Work `New visit` action, and corrects compact navigation/action layout. The pass preserves the local-first ServiceLoop loop and does not add backend, account, sync, dispatch, invoicing, inventory, or customer-portal scope.

## Implemented behavior

### Template editor

- New-item creation now presents an explicit `Item type` label and selectable STATUS/TEXT/NUMBER cards. The selected card has a pale-teal selection surface, strong outline, radio semantics, and the accepted two-line educational descriptions.
- Private technician guidance uses the compact full-width editor while preserving the normal long-text editor behavior. It remains visibly private and supports expansion into the full-screen editor with focus, IME, and accessibility handling.
- Collapsed draft items expose a tappable summary with a right caret and one horizontal `Remove | Edit` row. Expanded items use a down caret and the compact icon-over-label strip in the exact order `Remove | Move up | Move down | Done`.
- Move controls are visibly and semantically disabled at the first/last position. `Done` collapses the draft item; Save persists the draft as one new immutable revision.
- Template state actions now stay synchronized with the reloaded detail (`Active | Disable`, `Disabled | Activate`) without changing the revision. Detail checklist rows are quiet read-only rows; only the top Edit action edits. Version history is a quiet `Version history (N)` navigation button, and detail Delete is text-only.

### Work actions and navigation

- Visits, Follow-ups, and Due Services always contain a real active natural `New visit` button. The floating action is shown only when a meaningful layout places that natural slot offscreen, and it dismisses as soon as any portion of the natural slot enters the viewport. The old synthetic spacer/measurement feedback loop was removed.
- Due Services keeps the floating action above the Book/Start action area. Short pages remain stable and directly actionable; long pages dock after scrolling without duplicate actions or layout blink.
- Site Customer/Edit/Maps actions use content-aware adaptive widths. Navigation buttons center their label/icon content across the button while keeping the disclosure caret pinned at the trailing edge.
- Work filter UI tests clear the presentation-preference SharedPreferences file for test isolation.

## Implementation notes

The shared action primitives now support centered navigation content, optional destructive-button icons, compact icon-over-label actions, and adaptive action rows. Two deterministic Phosphor Fill circle-arrow assets were added through the existing local icon generator. Template state mutation reloads the same detail pair after the repository write, preserving durable failure semantics and immutable revision identity.

## Verification evidence

### Local automated checks

- `:app:testDebugUnitTest` — **PASS**, 415 tests, 0 failures.
- `:app:assembleDebug` — **PASS**.
- `:app:assembleRelease` — **PASS**.
- `:app:lintDebug` — **PASS**.
- `:app:assembleDebugAndroidTest` — **PASS**.
- `git diff --check` — **PASS** at handoff.

### Canonical Android runtime

The debug and Android-test APKs were built first and installed explicitly with `adb -s <dynamically resolved serial> install -r` on the AVD whose display name is `Pixel 10a ServiceLoop`. The serial was resolved from the AVD identity for each device-directed run.

- Focused B030 connected UI suite (`B026LocalFlexibleWorkUiTest` + `WorkFilterSelectorUiTest`) — **29/29 PASS**.
- Existing B013 owner surface render suite — **3/3 PASS**.
- Human/rendered inspection — **PASS** for the new-template editor and expanded template-item editor in light appearance, plus representative light/dark Work surfaces. The template screenshots verified the type-card selection, compact private guidance, caret states, and ordered action strip; Work screenshots verified the floating action above persistent Due Services actions without overlap.

No system-handoff or Calendar-provider evidence is claimed by this milestone. The preserved canonical business dataset was not cleared or reset.

## Review boundary

This milestone remains implemented on `codex/b013-ui-overhaul` and is not an owner-accepted change to protected `master`.
