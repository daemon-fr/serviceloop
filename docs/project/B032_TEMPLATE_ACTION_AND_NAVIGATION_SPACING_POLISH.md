# B032 — Template action and navigation spacing polish

**Status:** IMPLEMENTED on `codex/b013-ui-overhaul`
**Start SHA:** `fb4f17fd663c4e31da81547cb1124fc9670819b3`
**Scope:** narrow presentation, accessibility, generated-icon, and spacing corrections; no business, persistence, recurrence, or template-history semantics changed.

## Inspection-template item actions

Template item rows now share one icon-only action layout in both the non-edit draft row and the expanded edit row:

- Delete is a larger red circular button at the far left with the generated `trash` icon.
- Move up and Move down are adjacent pale-teal circular buttons in the middle, using `arrow-fat-line-up` and `arrow-fat-line-down`.
- The right action is `pencil-simple` / `Edit item` for the non-edit row and `check-fat` / `Save item changes` for the edit row.
- Move actions retain disabled state at the first and last item.
- Content descriptions remain `Delete item`, `Move item up`, `Move item down`, `Edit item`, and `Save item changes`.

The collapsed/expanded caret remains an explicit right/down state. Checklist rows on the template detail screen remain quiet read-only rows; only the established version-history navigation is disclosed there.

## Form and directory spacing

The new-template `Item type` label has a small intentional gap before the first selection card. Private technician guidance remains compact and usable. Equipment now has a visible gap between its information block and the Customer/Site action row.

The shared navigation-button content layout reserves trailing space for disclosure carets, keeps labels centered in the remaining area, and applies consistently to Site and Equipment redirect buttons.

## Generated assets

`tools/phosphor/serviceloop-icons.json` is the source mapping for `trash`, `arrow-fat-line-up`, `arrow-fat-line-down`, `check-fat`, and `pencil-simple`. The repository Phosphor generator produced the Kotlin aliases and vector drawables; no ad-hoc icon assets were added.

## Verification

- Required Gradle unit, debug/release assembly, lint, and Android-test assembly gates: PASS.
- Focused Compose tests for template actions, move operations in both rows, action semantics, caret direction, item-type spacing, and Site/Equipment navigation layout: PASS when run individually on the canonical AVD.
- The full retained B026 UI class ran 26/27 tests; the one failure is the pre-existing one-time-record fixture/search assertion (`searchFindsOneTimeRecordWithoutRegisterReveal`), unrelated to this pass. The six B032-relevant tests passed.
- The canonical AVD was resolved dynamically by identity `Pixel_10a_ServiceLoop`; all device commands used the resolved serial explicitly, and debug plus Android-test APKs were installed explicitly.
- Representative light and dark template, Site, and Equipment screenshots were captured and visually inspected under `app/build/reports/b032-rendered/`.
- A separate approximately 320dp compact-width run was not performed.

This remains implemented review-branch work. Protected `master` was not changed and the branch was not pushed as part of this pass.
