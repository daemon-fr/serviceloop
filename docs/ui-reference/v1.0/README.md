# ServiceLoop UI/UX Reference v1.0 — repository intake

This directory is reserved for the owner-supplied **ServiceLoop UI/UX Reference v1.0** package used by B-013.

## Authority

The owner approved the package as the presentation implementation authority for B-013. See `docs/project/B013_UI_IMPLEMENTATION_PLAN.md` and B-019 once consolidated into `BASELINE_DECISIONS.md`.

The package was produced against repository/source commit:

`1fd51131b040ab62af3874c1106615b75f3008fe`

The B-013 working branch was created directly from that commit before any UI-overhaul changes.

## Expected committed files

The exact extracted package must be present here before Stage A source implementation:

- `README.txt`
- `ServiceLoop_Source_Manifest_v1_0.json`
- `ServiceLoop_UI_Audit_v1_0.json`
- `ServiceLoop_UI_Coverage_v1_0.csv`
- `ServiceLoop_UI_Tokens_v1_0.json`
- `ServiceLoop_UI_UX_Implementation_Reference_v1_0.html`
- `ServiceLoop_UI_UX_Implementation_Reference_v1_0.md`
- `references/mockup_editor.png`
- `references/mockup_outbox.png`
- `references/mockup_review.png`
- `references/mockup_selected.png`

Do not regenerate, reformat or editorially rewrite these source-package files when importing them. Preserve their bytes.

## Canonical relationships

- `ServiceLoop_UI_UX_Implementation_Reference_v1_0.md` is the canonical narrative/manual.
- `ServiceLoop_UI_Tokens_v1_0.json` and `ServiceLoop_UI_Coverage_v1_0.csv` are normative mechanical companions.
- `ServiceLoop_UI_Audit_v1_0.json` and `ServiceLoop_Source_Manifest_v1_0.json` define package checks/evidence boundaries.
- The HTML file is a self-contained reading edition, not a separate design authority.
- The PNG files are visual-character/reference inputs; they do not define an authoritative pixel-to-dp scale.

## Recorded package hashes

From the supplied audit:

- `ServiceLoop_UI_UX_Implementation_Reference_v1_0.md` — SHA-256 `5cfdbd8c520f73eeb4ff66216d9f21a3a0137a800378f70c8de77d907b0dbdbe`
- `ServiceLoop_UI_UX_Implementation_Reference_v1_0.html` — SHA-256 `98120be8cc1d1bc407687616463ebe0ceb33f1b5e2903937177c8ee482ee07b2`
- `ServiceLoop_UI_Tokens_v1_0.json` — SHA-256 `7757a15c129b198f043faf40111836395946a54a8c3c1f2a49fe0780c7ef919c`
- `ServiceLoop_UI_Coverage_v1_0.csv` — SHA-256 `1b6f13d40f05977d92b3bb043e623753f1f599065d3897bf052af0c0c147e4e2`

Reference image hashes:

- `mockup_outbox.png` — `993b5c8376eebf052c9df01e97932b1d77da3572f853733f97b2e2a165bc7184`
- `mockup_selected.png` — `cdabb353913d28486ee7979a0a17e77037153c200a92e9781fabbf8005ac5616`
- `mockup_editor.png` — `3e81229e7c921fea9d475dedcba91e4ff4c5ac2811b2009d51053988b8dcdb20`
- `mockup_review.png` — `34d574e194eec80cec5b73f57120cd2282be34b16620755c498026539dab9a46`

The importing agent must calculate and verify these hashes before committing the package.

## Package audit summary

The supplied audit reports:

- 357 Conceptual-C source action groups mapped;
- 93 current additional action groups;
- 282 explicit compound-action variants;
- 183 explicit input controls;
- 915 traceability rows including parent groups/variants;
- 73 surface contracts;
- 36 component contracts;
- 12 layout patterns;
- 20 interaction patterns;
- 129 semantic-state mappings;
- 37 theme color roles;
- 263 calculated contrast checks with 0 documented calculation failures;
- 20 acceptance scenario families;
- 12 named exception records.

These are package/document audit counts, not proof that the Android implementation already conforms.

## Import method

The original owner-supplied ZIP is intentionally **not reconstructed from generated content**. Import the exact ZIP supplied by the owner, extract these files into this directory, verify hashes, then commit the unchanged package on `codex/b013-ui-overhaul`.

After import, remove any temporary ZIP copy from the repository unless the owner explicitly asks to keep the archive itself; the extracted frozen reference files are the desired repository form.
