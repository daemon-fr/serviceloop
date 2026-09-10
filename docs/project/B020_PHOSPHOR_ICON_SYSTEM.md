# ServiceLoop — B-020 Phosphor icon system

**Status:** Owner-authorized B-013 amendment, 2026-09-10  
**Working branch:** `codex/b013-ui-overhaul`

## Decision

ServiceLoop adopts **Phosphor Icons** as the product UI icon family for B-013 instead of the Material Symbols Outlined family named in chapter 7 of the frozen UI reference package.

This amendment changes the icon family only. The frozen v1.0 UI reference remains authoritative for icon purpose, semantic meaning, placement, target size, accessibility, hierarchy, tint/state treatment, and all non-icon UI contracts.

## Source and licensing

Pin the official `@phosphor-icons/core` package at version **2.1.1** as the canonical source asset set. Preserve its MIT license and source metadata in the repository. Do not rely on runtime/CDN/network icon retrieval.

## Repository strategy

Keep the complete pinned upstream icon set in the repository as an immutable vendor source archive under a non-Android-resource path, rather than committing thousands of loose SVG files into the app source tree. The vendor area must include the upstream archive, license, package metadata/source note, and SHA-256 verification.

The Android application itself must contain only the icons ServiceLoop actually uses. Generate or copy those selected icons deterministically from the vendored source into a small app-owned Compose icon layer. Do not add a runtime Phosphor/remote-SVG dependency merely to access the full catalog.

The generated/selected application icon layer must be reproducible from a checked manifest mapping ServiceLoop semantic aliases to exact Phosphor icon names and weights.

## Weight/style policy

Use **Phosphor Regular** as the default ServiceLoop UI weight. This is the closest replacement for the previous outlined/regular icon direction and should be used consistently for ordinary navigation, actions, entities, status-support glyphs, and handoff icons.

Other Phosphor weights remain available in the vendored source but are not to be mixed casually. `Fill`, `Bold`, `Light`, `Thin`, or `Duotone` may be introduced only when a documented ServiceLoop state/selection contract benefits materially and the result remains coherent in both themes. Stage B should default to Regular rather than invent per-screen weight choices.

## Geometry and accessibility

Preserve the B-013 icon geometry unless a specific component contract says otherwise:

- standard visual icon: 24dp;
- small icon where explicitly specified: 20dp;
- meaningful icon-only action target: at least 48dp;
- accessible contextual name for icon-only actions;
- visible labels remain where the reference requires text plus icon;
- color never becomes the only carrier of state meaning.

Do not scale icons by substituting arbitrary font glyphs or Unicode characters.

## Initial semantic mapping direction

Use the official Phosphor catalog to resolve the exact available icon names. The intended semantic direction is:

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
- Customer/customer directory → Buildings/AddressBook family as appropriate to the surface
- Site → MapPin family
- Equipment → Wrench
- Work → ClipboardText/Clipboard family
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

Where multiple listed Phosphor candidates exist, select one exact icon during the Stage-B icon-manifest pass based on the frozen screen/component meaning and use it consistently thereafter. Do not use a cloud glyph for local backup unless the workflow actually refers to cloud storage.

## Build and determinism

No network fetch may be required to build ServiceLoop after the vendored source and generated selected icons are committed. The release APK must include only the selected app icon assets/code, not the complete vendor archive. The full vendor archive is repository source material only.

## Reference interaction

Chapter 7 of `docs/ui-reference/v1.0/ServiceLoop_UI_UX_Implementation_Reference_v1_0.md` is superseded only where it mandates **Material Symbols Outlined as the icon family/source**. Its semantic mappings remain guidance and must be translated to the closest approved Phosphor equivalents under this decision. All other B-013 visual, component, state, accessibility and platform-chrome requirements remain unchanged.
