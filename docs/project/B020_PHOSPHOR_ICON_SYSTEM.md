# ServiceLoop — B-020 Phosphor icon system

**Status:** Owner-authorized B-013 amendment, revised 2026-09-10  
**Working branch:** `codex/b013-ui-overhaul`

## Decision

ServiceLoop adopts **Phosphor Icons** as the product UI icon family for B-013 instead of the Material Symbols Outlined family named in chapter 7 of the frozen UI reference package.

This amendment changes the icon family only. The frozen v1.0 UI reference remains authoritative for icon purpose, semantic meaning, placement, target size, accessibility, hierarchy, tint/state treatment, and all non-icon UI contracts.

The owner further selected **Phosphor Fill as the single product icon style**. Use Fill consistently across ordinary navigation, root destinations, actions, entity/category glyphs, status-support glyphs and handoff icons. Do not mix Regular/Light/Bold/Duotone/Thin with Fill merely for decoration or selected-state emphasis.

The root destinations must especially use the solid Fill treatment for **Home**, **Work**, and **Customers**.

## Source and licensing

The owner has placed the complete Phosphor SVG source set locally under:

`tools/phosphor/source/svgs/`

The source contains the six Phosphor weight/style families. This locally supplied source tree is the canonical vendored input for the B-013 icon integration.

Before committing it, inspect available package/source metadata and preserve the exact upstream version/provenance where it can be established. Preserve the Phosphor MIT license in the repository. Do not rely on runtime/CDN/network icon retrieval.

## Repository strategy

Keep the **complete locally supplied Phosphor SVG source set** in the repository under the non-Android-resource vendor/tooling path:

`tools/phosphor/source/svgs/`

The full source set is intentionally retained so future ServiceLoop UI work can select additional icons without introducing a network/build dependency or changing icon families.

The Android application itself must contain only the icons ServiceLoop actually uses. Generate or copy the selected **Fill** icons deterministically from the vendored source into a small app-owned Compose icon layer or generated Android resource layer. Do not add a runtime Phosphor dependency, remote-SVG dependency, icon font, or the complete SVG catalog to the APK.

The generated/selected application icon layer must be reproducible from a checked manifest mapping ServiceLoop semantic aliases to exact Phosphor icon names and the fixed `fill` style.

The entire vendored source tree is repository/tooling material only and must not be included in Android resources or release packaging.

## Weight/style policy

Use **Phosphor Fill for all product UI icons**.

This supersedes the earlier B-020 default of Phosphor Regular.

Do not use different Phosphor weights to encode selected/unselected, enabled/disabled, normal/error, or root/non-root states. Those states continue to be represented by the canonical ServiceLoop color, container, label, accessibility and semantic-state contracts.

For root navigation specifically:

- Home → solid Fill icon;
- Work → solid Fill icon;
- Customers → solid Fill icon.

Selected root state uses the canonical selected/action presentation around the same Fill-family glyph rather than switching icon weight.

If a particular Phosphor Fill glyph is visually unsuitable at the required 20/24dp geometry, choose a semantically equivalent Phosphor Fill glyph from the vendored catalog before considering any cross-weight exception. A cross-weight exception requires explicit owner/orchestrator approval.

## Geometry and accessibility

Preserve the B-013 icon geometry unless a specific component contract says otherwise:

- standard visual icon: 24dp;
- small icon where explicitly specified: 20dp;
- meaningful icon-only action target: at least 48dp;
- accessible contextual name for icon-only actions;
- visible labels remain where the reference requires text plus icon;
- color never becomes the only carrier of state meaning.

Do not scale icons by substituting arbitrary font glyphs or Unicode characters.

The filled visual weight does not authorize enlarging ordinary glyphs beyond the canonical geometry. Judge optical balance at the specified size and select the best Fill glyph when several semantic candidates exist.

## Initial semantic mapping direction

Inspect the actual locally vendored Phosphor Fill catalog and resolve one exact icon name for each semantic alias. The intended direction is:

- Back → ArrowLeft
- Search → MagnifyingGlass
- Settings → Gear/GearSix family
- More → DotsThree
- Add → Plus
- disclosure → CaretRight
- expand/collapse → CaretDown/CaretUp
- Date → Calendar/CalendarBlank family
- Time → Clock
- Home → House
- Work → ClipboardText/Clipboard family
- Customers root / customer directory → AddressBook/UsersThree family; prefer a directory/customer metaphor that does not conflict visually with Site
- Customer/company entity → Buildings where appropriate
- Site → MapPin family
- Equipment → Wrench
- History → ClockCounterClockwise
- Photo → Image
- Camera → Camera
- external open → ArrowSquareOut
- Share → ShareNetwork/Share family
- Copy → Copy
- Delete → Trash
- Warning → Warning
- Error → WarningCircle/XCircle family as appropriate to the semantic contract
- verified/local saved → Check
- private/locked → Lock
- expanded text → ArrowsOut
- Report → FileText
- Restore → ArrowCounterClockwise/ClockCounterClockwise family
- Backup → FileArrowUp/Archive/Database family chosen to communicate local backup rather than cloud synchronization.

Where multiple listed Phosphor candidates exist, select one exact **Fill** glyph during the Stage-B icon-manifest pass based on the frozen screen/component meaning and use it consistently thereafter. Do not use a cloud glyph for local backup unless the workflow actually refers to cloud storage.

## Root-navigation identity

The root destinations are visually important and must not use Unicode stand-ins.

Use a coherent filled three-icon set, with the default direction:

- Home → `House` Fill;
- Work → `ClipboardText` Fill, or the closest available Fill work/service-list glyph after inspecting the local catalog;
- Customers → `AddressBook` Fill, or the closest available Fill customer-directory glyph that remains visually distinct from Site/MapPin and Equipment/Wrench.

Choose the exact final names from the vendored source before generation. Keep the same selected/unselected glyph; selection is communicated through the canonical root-navigation state treatment, not by changing weight.

## Build and determinism

No network fetch may be required to build ServiceLoop after the vendored source and generated selected icons are committed. The release APK must include only the selected app icon assets/code, not the complete vendor SVG tree. The full vendor tree is repository source/tooling material only.

Generation must be deterministic and reviewable. Prefer a small checked script plus manifest over hand-copying arbitrary path data into many Kotlin files. Generated output must be stable for identical source + manifest input.

Do not execute SVG content. Treat the vendored files as data and parse/convert them with ordinary deterministic tooling.

## Reference interaction

Chapter 7 of `docs/ui-reference/v1.0/ServiceLoop_UI_UX_Implementation_Reference_v1_0.md` is superseded only where it mandates **Material Symbols Outlined as the icon family/source or outlined visual style**. Its semantic mappings remain guidance and must be translated to the closest approved Phosphor Fill equivalents under this decision. All other B-013 visual, component, state, accessibility and platform-chrome requirements remain unchanged.
