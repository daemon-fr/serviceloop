# V16 Service — UI Style and Icon Contract

## Adopted presentation authority

The adopted V16 Service UI/UX reference v1.0 package is the presentation implementation authority. Its Markdown manual, token JSON, and coverage CSV are normative mechanical companions. Audit/source-manifest files define provenance and evidence boundaries. HTML is a reading aid; three retained PNG mockups define visual character, not pixel-to-dp scale. Business semantics, persistence, privacy, Dispatch, Recovery, reminders, and Calendar remain governed by their adopted contracts.

The visual reference does not authorize workflow or data-rule changes. Required behavior retains accessible labels, state meaning, error/empty/loading behavior, touch targets, and light/dark support from the adopted UX documents.

## Icon system

The product uses the locally vendored Phosphor SVG catalog and Phosphor Fill consistently for app UI icons. A checked manifest maps semantic aliases to exact upstream Fill names. Generation is deterministic; only selected app-owned outputs enter Android resources. The complete catalog remains tooling/source material and must not enter the APK. No runtime, CDN, font, or network icon dependency is added.

Keep upstream attribution and licensing unchanged. Do not rewrite upstream package names or copyright statements. Keep icon meaning coherent across navigation, actions, entities, and status. State is communicated by color, container, label, accessibility, and semantics, not by changing icon weight.

Standard visual geometry is 24dp; explicitly small icons are 20dp; meaningful icon-only actions have at least a 48dp target and contextual accessible name. Keep visible labels where the reference requires them. Color is never the only state cue. Home, Work, and Customers use solid Fill glyphs with the same glyph selected and unselected.

## Asset and build rules

Retain the adopted teal direction, layout, and shapes. B051 is an identity update, not a logo redesign. Inspect owned launcher, generated icon, report, mockup, and document assets for painted/embedded brand text. Regenerate branded outputs using the existing deterministic tool after updating paths and manifests. Preserve upstream artwork and license text.
