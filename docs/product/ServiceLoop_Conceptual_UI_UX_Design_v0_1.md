# ServiceLoop — Conceptual UI/UX Design

**Version 0.1 · 5 September 2026 · Proposal for review**  
**Canonical edition:** this Markdown file. The offline HTML is generated from this same content.  
**Design direction:** a calm, explicit service book for one independent technician.  
**Not authorized:** implementation, a selected launch trade, a commercial model, or a claim of tested accessibility/usability.

[Open the individual-action coverage appendix](ServiceLoop_UI_UX_Action_Coverage_v0_1.md). It is part of this deliverable, not optional supporting material. Every source CORE action has an individually identified row; source-policy alternatives are not silently activated.

## Reading this proposal

Read A–D for the decisions, E for the buildable conceptual screen specifications, F–H for complete workflows and report/state behavior, and I–J for verification and traceability. The catalogue and appendix together define control placement: E explains the composition; the appendix names the exact location and applicable source contract for every control. The document is intentionally more detailed than an everyday user manual.

**Reference legend.** `C:` = *ServiceLoop_Conceptual_App_Map_v0_1*; `F:` = *ServiceLoop_Complete_Functional_App_Map_v0_1*. A source-qualified action such as `C:S24-A10` is not interchangeable with `F:S24-A10`. `UI-xx` identifies this proposal's surfaces; `P-xx` identifies reusable interaction contracts; `L-xx` identifies layout families; `X-xx` identifies source conflicts; `FC-xx` identifies explicit functional change proposals; `Q-xx` identifies newly checked official guidance. Brackets in wireframes identify controls, not additional features. “Below fold” describes the reference viewport, not hidden functionality.

**Authority and evidence.** Current user instructions govern. The original context and functional prompt define the bounded investigation, not user approval of either map. Source-supported needs, presentation proposals, functional recommendations, and future validation are labelled separately. No interviews, field trials, task-time measurements, or payments are claimed.


<a id="A"></a>

# A. Working baseline and conflict/assumption register


## A1. Source inspection and provisional selection

Both complete Markdown maps were reread, including shared interactions, automatic behavior, rules, journeys, platform limitations, extensions, and unresolved decisions. Their local HTML editions were compared with Markdown-rendered content. F has the same normalized content in both editions. C's HTML adds a reading-edition introduction/footer and repeats one navigation sentence; no action omission or material policy difference was found between its editions. The HTML styling of either source is **not** an Android visual specification.

There is no explicit adoption or supersession of either proposal in the supplied conversation. **Use C as the provisional working baseline for this design.** Its explicit checklist-review state, separate machine observations, and dependency-first lifecycle rules provide a coherent basis for a technician to understand what was recorded versus what was fulfilled. This is a design recommendation, not inferred user approval. F remains a fully traced comparison source, including its different defaults and safeguards.

Source families inspected: [C Markdown](ServiceLoop_Conceptual_App_Map_v0_1.md), [C reading edition](ServiceLoop_Conceptual_App_Map_v0_1.html), [F Markdown](ServiceLoop_Complete_Functional_App_Map_v0_1.md), [F reading edition](ServiceLoop_Complete_Functional_App_Map_v0_1.html). Their checksums and enumeration audit are in J. The earlier research context was read as background, not re-run as a market study.

## A2. Consequential conflicts

These choices affect behavior, security, or the life of records. They are not color or layout decisions. All X items remain owner-review decisions. The working UI shows the C policy unless FC explicitly states otherwise; the F variant is localized here and in its affected action rows, not drawn as a second app.

| ID | C proposal | F proposal | Working UI consequence / owner decision |
|---|---|---|---|
| X-01 Backup confidentiality | C:S34, C:N1 propose passphrase-protected portable packages; C:D12 treats backup before erase as a separate task. | F:S34 produces an unencrypted CORE package; protection is deferred. | UI-34 requires a passphrase and explains no reset. Accept encryption as CORE only after security/recovery review. Rejecting it removes that field, adds an explicit unencrypted-data warning before every write, and changes UI-35 unlock—not the rest of the app. |
| X-02 Incomplete recovery | C:S34-A02 and C:S35 permit a deliberately labelled incomplete recovery copy when media is already missing. | F:S34/F:S35 require complete packages; missing media blocks full recovery output/acceptance. | UI-34/35 show an unmistakable incomplete branch, missing-file manifest, and persistent restored warning. Owner must accept this policy, not mistake it for a full backup. |
| X-03 Import/export policy | C:S37 supports repeat **create-only** batches, exact unchanged-key reuse, deliberate branch skipping; C:S36 offers privacy/scope choices and a directory export. | F:S37 permits import only into an empty business dataset; F:S36 exports all records with private fields and is not a round-trip directory format. | UI-37 follows C, never overwrites or merges existing changed records. UI-36 defaults private data off. File schemas, limits, duplicate rules and result-file differences are specified in J2. An F decision would disable import after business data exists and replace the file contract—not just rename the button. |
| X-04 Archive, retire, move, end | C:R07 requires dependencies to be resolved individually; retirement requires ended plans. Moves require no open equipment task and carry acknowledged plan states/dates across owners. | F:R06 permits cascading cancellations/pauses, retirement pauses plans, same-owner moves carry tasks, and cross-owner moves pause plans. | UI-43 names blockers; UI-12 shows carried states/dates, even across customers. No automatic bulk cancellation. Cross-owner carry is a consequential owner choice. See FC-03 for an additional correction-draft guard. |
| X-05 Completion and recurrence | C:S22/C:S24 require explicit checklist **Reviewed**, independently chosen **Performed**, and an initially unchecked fulfillment choice. Captured interval is used; counted completion must be strictly later than prior counted completion. | F:S19/F:S20 use **Completed**, a different checklist/outcome relationship, propose fulfillment after that choice, and review the current interval. | UI-22 has “Mark checklist reviewed”; UI-24 has separate outcome and unchecked “Fulfill this service obligation.” Same-day second service is history only under C. No favorable answers are preselected. |
| X-06 Record structure and templates | C stores machine-level findings/parts/photos, explicit finding disposition, and can use an archived assigned template's frozen content. | F's work-item workspace attaches these details to a service and includes different public/private note fields; an archived assigned template must be replaced/omitted before a new run. | UI-21 owns machine evidence; UI-22 owns answers and work performed. No new public visit-summary field. Archived template label explains the retained copy. Details that cannot be mapped without changing ownership are flagged in F action rows. |
| X-07 Input rules and draft lifecycle | C has ordinary-form recovery buffers, specific uniqueness rules, photo caps, and one-off working-draft removal. | F has different limits/duplicate policies, no equivalent ordinary durable buffer promise, Save-and-leave, and retained cancelled shells for discarded visits. | UI-41 distinguishes recovered unsaved input from saved records; UI-20 discard explains whether an ad-hoc draft is removed or a booking becomes cancelled. All material limit differences are in J2; they are not silently combined. |
| X-08 Contact/history semantics | C permits selected contact-data picking, “Edited on” contact notes and deletion of erroneous unlinked notes; task closure is separate. | F uses manual contacts, preserves old contact values/entered-in-error state, and can resolve/reschedule the originating task when saving contact. | UI-07/09 offer a system picker; UI-29 follows C's note policy. No automatic task closure or comprehensive communication audit claim. Owner to accept weaker contact-note history or revise it explicitly. |
| X-09 Reminders and backup nudges | C:S32 uses explicit Save, 14-day default horizon, 2-hour default lead, generic notifications, and changed-data/age backup nudges. | F:S32 changes immediately, defaults 30 days/1 hour, permits names, and F:S33 has a 7-day nudge snooze. | UI-32 has Save; no customer names on notifications and no snooze control. UI-33's backup-age control is immediate and labelled as such. Denied permission never disables business records. |
| X-10 Booking and correction/report policy | C restricts new past bookings, offers explicit guarded reconciliation of schedules, and warns before sharing old/voided originals. | F allows past bookings with warning, fixes booking site after creation, has different reconciliation guards, and does not offer a void original as a valid service report. | UI-18 sends past work to historical entry; UI-26 shows each before/after proposal; UI-39 labels/warns on old originals. These warnings do not make an invalid record valid or revoke previously shared files. |
| X-11 Finding/task description | C:S23 requires a corrective-task description but C:S28 lists title/date/context and optional internal notes. | F has different service-level finding/task ownership and does not resolve C’s internal wording. | Working assumption: the linked required public finding supplies the description; task notes stay optional/private. Owner must confirm; see J2.1. |

## A3. Explicit functional recommendations beyond C

**FC-01 — Restore discoverability of global history and report attention from F.** Add **Work → More → History**, with the existing C:S40 history layout in an all-records scope, plus Home “Records needing attention” links for failed/missing PDFs and correction drafts. F:S03-A08, F:S02-A11/A12 and F:S26 support these responsibilities. This is an explicitly proposed expansion of C's entry/filter coverage, not a fourth root or an analytics feature. It does not add record types or change their meaning.

**FC-02 — Add the narrow save-failure rescue actions from F:U07.** UI-58 offers **Open device storage settings** and **Copy unsaved text** for text currently held in memory, with a privacy warning and no copying of secrets. It offers no promise of recovery for missing photos or uncommitted data after process death. This is a functional recommendation because it adds actions, although the persistence rules remain C's.

**FC-03 — Block equipment moves while a related correction draft is open.** F's stronger move guard is recommended in addition to C's working/booking/task guards. UI-12 lists the correction draft and provides “Open correction.” This prevents a correction being silently retargeted during an ownership change. It is not an automatic discard, commit, or alteration of that draft. Owner approval is required.

All other differences are resolved to C or explicitly left as unadopted variants; a desirable F control does not silently enlarge CORE. Presentation choices such as moving a field into an expansion, a direct scoped entry, or using a full screen rather than a sheet preserve C semantics.

## A4. Scope and design assumptions

CORE remains one technician/business/authoritative local dataset, customer–site–equipment relationships, date-based plans, contact handoffs, bookings, inspections, multi-machine visits, individual completion outcomes, follow-ups, immutable finalized snapshots/revisions, a fixed report, ordinary reminders, and complete recovery/import/export. No account is required for local work.

LATER remains usage/runtime recurrence, scanning, signatures/acknowledgement, calendar export, extra report layouts/specialist forms, and automatic external backup. OUT remains billing, stock purchasing, dispatch, teams, sync, portals, campaigns, sensors, predictive maintenance and certification. **There are no disabled “coming soon” entries for these.** No launch SDK, framework, orientation, language list, device model, price, or licensing layer is selected.

Design assumptions to test: short interrupted field sessions; similar machines; occasional low storage; harsh or dim light; one-handed operation; long names; infrequently used recovery tasks. These are test conditions, not interview findings. Ordinary touchscreens are not promised to work with every glove or in rain. The example business is fictional; the owner is not assumed to operate a service business.


<a id="B"></a>

# B. Experience strategy and task priorities


## B1. One direction: the explicit service book

The app should feel like a dependable work record, not a dashboard, wellness app, enterprise console, or camera portfolio. A neutral canvas, dark readable text, restrained teal actions, and short equipment identifiers let the work dominate. A photo assists identification; it never substitutes for a machine label. Urgent obligations use a small amber date label, not a screen full of red.

**Priority order:** preserve unsaved work when a real failure occurs; resume the technician's actual working visit; show imminent appointments; expose overdue obligations and open follow-ups; keep records/report/backup maintenance visible without making it the main task. A saved draft is useful progress, not a completed visit. A completed record is a fact, not evidence of report delivery.

| Technician's question | Concrete response in this design |
|---|---|
| What needs attention, and what is arranged? | Home's resume block and next appointment; Work Due rows show the obligation date and a separately named booking/working link. Home counts remain drill-downs, not metrics. |
| Which customer, site, machine, service? | Two-line context header, generated equipment reference, optional internal ID/serial, named machine switcher. No ambiguous auto-selected machine; scope is confirmed before work starts. |
| What do I check and record here? | Work-item screen with inline answers and a separate machine evidence area; issue details open once, not a modal for every OK or reading. |
| What did I finish, and what changes next? | Grouped review with outcome, fulfillment choice, old/new due date and follow-up effects on each service. Unperformed/partial work remains visible. |
| Can I hand over and recover? | Finalized record stays successful even if PDF creation fails; report preview and structured text match public content; backup stages and replacement preview state precisely what is known. |

## B2. Attention economy and progressive disclosure

The normal flow contains one dominant task action: Resume / Start visit / Mark checklist reviewed / Review completion / Finalize record / Generate PDF / Share PDF according to state. These labels never collapse into a generic “Done.” Secondary information sits beneath the identity and action, not behind a secret long-press. “More” contains named infrequent actions, each exhaustively specified in E and the appendix.

Administrative hierarchy is preserved but not compulsory: global search opens equipment directly, due selections open scoped visit setup, and follow-ups open their actual source. The work workspace shows a machine's service rows directly so an experienced technician can open the next service without returning through customer and site.

An error that prevents a durable save takes precedence over urgency. An ordinary overdue plan does not block unrelated work; an unreviewed checklist does not prevent retaining partial work; a backup reminder does not interrupt an inspection. Consequential transitions get explicit review. Harmless navigation does not get a confirmation.

## B3. Fictional fixture and consistent time states

**All people, organizations, machines, addresses, and trade content below are fictional test fixtures.** The example checklist is not a maintenance procedure and does not choose a launch trade. English labels are design copy, not a launch-language decision. Example time zone is Europe/Bucharest solely to make appointment examples concrete.

Business: **Mira Field Service**; technician **Alex Marin**. Customer `CU-001`: **Harbor Fitness and Rehabilitation Cooperative**. Site `ST-001`: **Riverside Centre — East Building, Second-floor Training Room**, **18 River Lane, Riverside**. Long names remain available in full. Optional shorthand “Riverside Centre” is a display excerpt, not a separately invented nickname field.

| Equipment | Identity | Plans / next due before V-001 |
|---|---|---|
| EQ-001 · T-01 | Treadmill 01 — window side; StrideWorks R8; serial SW-R8-0417; identification photo present | P-001 Condition inspection, every 3 months, 1 Sep 2026. P-002 Lubrication service, every 6 months, 1 Sep 2026. |
| EQ-002 · T-02 | Treadmill 02 — door side; same make/model; **Serial not supplied**; no photo | P-003 Maintenance, every 6 months, 1 Sep 2026. P-005 Electrical inspection, every 12 months, 15 Sep 2026. |
| EQ-003 · B-01 | Stationary bike 01; WheelWorks C2; serial WW-C2-023 | P-004 Maintenance, every 6 months, 1 Sep 2026. |

Visit `V-001`: booked for **3 Sep 2026, 09:00**, moved on 2 Sep to **5 Sep 2026, 09:00**, reason “Customer requested Saturday access.” Four selected services: P-001, P-002, P-003, P-004. P-005 is **not selected**. V-002 is a separate **8 Sep 2026, 14:00** booking at the same site for a one-off **Assess console rattle — T-02**; it claims no recurring plan. Existing contact follow-up `FU-001`, **Confirm access for the return visit**, is Open and due 5 Sep. It is not automatically resolved by finishing V-001.

| Stage | Snapshot and counts used by wireframes |
|---|---|
| T0 First use | Empty business dataset; no imaginary example data in the actual app. Journey F1 builds the records. |
| T1 Arranged | 5 Sep before start: 4 overdue obligations, 1 due soon; 2 booked visits; no working visit; 1 due open contact follow-up. |
| T2 Interrupted | 5 Sep 10:15: V-001 Working, saved at 10:14; V-002 Booked. Home: **4 overdue, 1 due soon, 1 booked in next 7 days, 1 unfinished, 1 follow-up due**. Report/backup counts never include a proposed task as a live task. |
| T3 Finalized | 5 Sep 10:40: V-001 finalized revision 1. P-001 advances to **5 Dec 2026**; P-003 to **5 Mar 2027**. P-002/P-004 remain **1 Sep 2026**. Home: **2 overdue, 1 due soon, 1 booked, 0 unfinished; 1 follow-up due / 2 open in total**. New FU-002 is due 12 Sep. PDF may still be pending/failed. |
| T4 Corrected branch | An explicitly separate what-if after T3: change V-001 actual date to 4 Sep with reason, only if latest/ownership guards allow; P-001 proposal 4 Dec and P-003 proposal 4 Mar. Revision 1 remains dated 5 Sep. Main T3 counts/examples do not silently become this branch. |

V-001 results at T3: T-01 inspection **Performed**, checklist reviewed, issue “Fraying on outer belt edge,” disposition **Corrective follow-up needed** → FU-002 **Replace worn belt — T-01**, due 12 Sep. T-01 lubrication **Partly performed**, “Cleaned accessible surfaces; lubricant unavailable,” no fulfillment. T-02 maintenance **Performed**, “Checked and secured guard fixing,” part **Guard fastener · 1 piece**, no issue observed in recorded work, fulfilled. B-01 maintenance **Not performed**, reason “Room unavailable,” observation **Not assessed**, no fulfillment. This is not a blanket safety assessment.

P-001 checklist revision 2: **Guard fixing** OK (required status); **Belt condition** Issue found (required status); **Displayed distance** 1240.5 km (required numeric, merely a recorded reading); **Optional accessory** Not applicable, reason “Not fitted” (required status); **Additional observation** Not recorded (optional text). Three V-001 attachments: PH-01 T-01 belt, PH-02 T-01 nameplate, PH-03 T-02 guard. Only PH-01 and PH-03 selected for the customer report. EQ-001's directory identification image is a separate fourth image, not silently included. Last verified backup initially 1 Sep at 08:00; a new T3 backup includes all four images and the actual report versions that exist.


<a id="C"></a>

# C. Information architecture and navigation


## C1. Navigation tree

```text
Home [UI-02]                         Work [UI-03]                    Customers [UI-05]
  Resume working visit               Due services / Visits /       Customers / Equipment
  Next booked visit                  Follow-ups                      Search / filter / sort
  Overdue / due soon / follow-ups     scoped lists + selection         Customer [UI-06]
  Records needing attention          More → History [UI-40, FC-01]      Site [UI-08]
  Recovery / reminder notices        New visit [UI-18]                    Equipment [UI-10]
  Search [UI-04] / New visit          New follow-up [UI-28]                 Plan [UI-13]
                                                                       Scoped history [UI-40]
Context paths (not new roots)
  Contact [UI-45] → OS dialer/composer/map → Contact note [UI-29]
  Visit setup [UI-18] → Booked [UI-19] / Working [UI-20]
    Working machine area [UI-21] → Work/checklist [UI-22] → Finding [UI-23]
    Completion review [UI-24] → Final record [UI-25] → Report viewer [UI-39]
    Correction / void [UI-26] → revised Final record
  Follow-up [UI-27] / edit [UI-28]
Settings [UI-30], reachable from each root toolbar
  Business and report identity [UI-31]
  Reminders [UI-32]
  Inspection templates [UI-15 → UI-16 → UI-17 → UI-48]
  Data and recovery [UI-33 → Backup UI-34 / Restore UI-35 / CSV UI-36–37]
  Privacy, help, app information [UI-38]
First launch [UI-01] → start empty / import / restore / local-storage explanation
Shared surfaces [UI-41–59] are listed below; system UI is never drawn as ServiceLoop UI.
```

## C2. Back, context and surface rules

The three root destinations use a labelled bottom navigation bar on compact windows. Each root retains its current tab, filters and scroll for the session. A root tap returns to that root's saved location; it does not wipe search or start a new visit. Fresh ordinary launch opens Home; recovering interrupted input offers a specific resume card. Notification entry opens its named record/list with a predictable Up route to the relevant root, not a fabricated deep stack.

Detail screens replace the root bar. Full task screens have Back/Up, a stable title, and a task footer; ordinary forms show Cancel and Save. Working screens show Back plus save status and “Leave visit for later,” not a misleading Cancel. Android Back first dismisses keyboard, then the active transient surface, then returns to caller. Never intercept Back globally just to show branding or an exit prompt. Dirty-form and unsaved-failure guards occur only when their actual state requires them. Native back integration is a technical requirement to validate, not a custom swipe gesture [Q-06].

Selectors with search, multi-selection, or entity creation are full screens, not nested sheets. A small single-choice menu may be a sheet, but opening a keyboard-heavy child replaces it and returns to the parent's unchanged selection. No stack of sheets. Dates use a native date picker with an accessible text-entry alternative where the chosen platform component supports it; unsupported versions use a labelled date-entry form with localized example and validation. Destructive consequences use full review screens when they need dependency lists or before/after rows. A small alert is reserved for one bounded decision.

On width ≥840dp with sufficient space, list-detail is a presentation proposal: a 320–400dp list pane and flexible detail pane. At 600–839dp use a navigation rail and a centered one-column task; add a second pane only when each remains usable at the actual font scale. At compact or large-text stress conditions return to one pane. These thresholds are design starting points, not an inherited device/SDK lock. State follows the task when windows resize [Q-05].

## C3. Source destination crosswalk

C:S01–S40 map individually to UI-01–UI-40 **with their original meanings**, although UI-20/21 share a working workspace composition. C:D01–D13 map respectively to UI-41–UI-53. New shared decompositions are UI-54 List tools, UI-55 Work selection/one-off editor, UI-56 Identification review, UI-57 Schedule reconciliation, UI-58 Save/integrity failure, UI-59 System handoffs and notifications. These are presentations of named responsibilities; FC-01/02/03 are separately labelled additions.

| F destination | This design's specific surface(s) |
|---|---|
| F:S01, F:S02, F:S03 | UI-01; UI-02; UI-03 + UI-54, UI-40 |
| F:S04, F:S05, F:S06 | UI-05 Customers mode; UI-06; UI-07 |
| F:S07, F:S08, F:S09 | UI-08; UI-09; UI-05 Equipment mode |
| F:S10, F:S11, F:S12, F:S13 | UI-10; UI-11; UI-13 + UI-46; UI-14 |
| F:S14, F:S15, F:S16, F:S17 | UI-45; UI-29; UI-18 + UI-55; UI-19 / UI-20 / UI-25 by actual state |
| F:S18, F:S19 | UI-20 + UI-21; UI-22 + UI-21 + UI-23 + UI-49, with X-06 ownership differences |
| F:S19-D01, F:S19-D02 | UI-23 finding; UI-49 part |
| F:S20, F:S21, F:S22 | UI-24; UI-27 + UI-47; UI-28 |
| F:S23, F:S24, F:S25 | UI-15; UI-16 view / UI-17 edit; UI-48 |
| F:S26, F:S27, F:S28, F:S29 | UI-40; UI-25; UI-26 + UI-57; UI-39 |
| F:S30, F:S31, F:S32, F:S33 | UI-30; UI-31; UI-32 + UI-59; UI-33 |
| F:S34, F:S35, F:S36, F:S37, F:S38 | UI-34; UI-35; UI-36; UI-37; UI-38 |
| F:U01, F:U02, F:U03 | UI-41; UI-42; UI-43 / UI-12 / UI-46 / UI-52 |
| F:U04, F:U05, F:U06 | UI-44; UI-51 / UI-39 / UI-59; UI-55 |
| F:U07, F:U08 | UI-58; UI-54 and P-04 |

This table covers destinations, not action equivalence. The separate appendix enumerates all 732 actions, including F's four embedded-editor actions. A row routed to a conflict explicitly states that the source's behavioral variant is not active.


<a id="D"></a>

# D. Visual system and reusable components


## D1. Tokens and visual roles

**These are recommended starting tokens, not a claim of tested visual design.** Follow the device's light/dark mode; do not add an in-app theme preference. Reject wallpaper-derived dynamic color for this proposal: primary navigation and semantic work/status colors stay recognizable across devices. This is a presentation decision, not a platform prohibition. It does not prevent system high-contrast or accessibility support. Use no network-dependent font or artwork.

| Role | Light | Dark | Usage |
|---|---|---|---|
| Canvas | #F5F7FA | #11181C | Root background; never a full warning color. |
| Surface | #FFFFFF | #172126 | Forms, sheets, raised task blocks. |
| Surface secondary | #EEF2F5 | #253238 | Group headers, read-only inset areas. |
| Main text | #162328 | #EFF5F7 | Body, labels, identifiers. |
| Supporting text | #47565D | #BAC9CF | Secondary context, not disabled via opacity. |
| Primary / on primary | #075E67 / #FFFFFF | #80D4DD / #063D43 | One dominant action, active control, focus ring. |
| Selection / on selection | #D8EFF1 / #063D43 | #163E45 / #B4EDF1 | Selected rows with check/label; not completion. |
| Interactive outline | #687B84 | #8AA2AD | Unselected fields/check boxes, focus-adjacent boundaries. |
| Decorative separator | #CED7DC | #405159 | Group separators only; never the only control boundary. |
| Urgency ink / tint | #714600 / #FFF0CD | #FFD58A / #45320C | “Overdue · 4 days”; calendar icon; small dose. |
| Workflow ink / tint | #164D8C / #E5EFFB | #A9CEFF / #163553 | “Working”, “Reviewed”, step progress; no implication of health. |
| Error ink / tint | #A32628 / #FCE9E9 | #FFB4AB / #512B2C | Saving/validation failure and unresolved input; error icon + text. |
| Confirmed-result ink / tint | #225D3C / #E3F2E8 | #A6DFB8 / #203D2B | A confirmed saved result or explicit OK answer; never blanket “safe”. |
| History ink / tint | #47565D / #EEF2F5 | #BAC9CF / #253238 | “Finalized”, “Revision 1”, “Read-only”, archived/ended. |

Operational urgency is **when** work is due. Workflow is **where** a draft is in its process. Error is **what failed or must be corrected**. Historical state is **what cannot be edited directly**. A red Issue found answer is a recorded observation, not an app failure: it always carries the exact label and finding context rather than a generic warning banner.

### Contrast calculations

The following results are computed from the actual sRGB hex values with WCAG relative luminance `(Llighter + 0.05)/(Ldarker + 0.05)`. Passing uses unrounded ratios; two decimals are displayed. All essential text is designed to the 4.5:1 target even when large text could qualify for a lower threshold. Interactive outline/focus cues target 3:1 against adjacent surfaces. These calculations verify **pairs**, not a rendered app or certification [Q-01, Q-08, Q-09]. Translucent pressed layers, media overlays, disabled states and actual rasterization still require inspection; critical copy never sits directly over a photograph.


| Mode | Pair | Ratio | Target | Result |
|---|---|---:|---:|---|
| Light | text #162328 / surface #FFFFFF | 16.09:1 | 4.5:1 | Pass |
| Light | text #162328 / canvas #F5F7FA | 14.99:1 | 4.5:1 | Pass |
| Light | muted #47565D / surface #FFFFFF | 7.62:1 | 4.5:1 | Pass |
| Light | muted #47565D / secondary #EEF2F5 | 6.77:1 | 4.5:1 | Pass |
| Light | onprimary #FFFFFF / primary #075E67 | 7.48:1 | 4.5:1 | Pass |
| Light | primary #075E67 / surface #FFFFFF | 7.48:1 | 4.5:1 | Pass |
| Light | onselection #063D43 / selection #D8EFF1 | 9.99:1 | 4.5:1 | Pass |
| Light | outline #687B84 / surface #FFFFFF | 4.42:1 | 3:1 | Pass |
| Light | outline #687B84 / canvas #F5F7FA | 4.12:1 | 3:1 | Pass |
| Light | outline #687B84 / secondary #EEF2F5 | 3.92:1 | 3:1 | Pass |
| Light | primary #075E67 / secondary #EEF2F5 | 6.65:1 | 3:1 | Pass |
| Light | urg #714600 / urgbg #FFF0CD | 7.21:1 | 4.5:1 | Pass |
| Light | flow #164D8C / flowbg #E5EFFB | 7.30:1 | 4.5:1 | Pass |
| Light | error #A32628 / errorbg #FCE9E9 | 6.27:1 | 4.5:1 | Pass |
| Light | success #225D3C / successbg #E3F2E8 | 6.72:1 | 4.5:1 | Pass |
| Dark | text #EFF5F7 / surface #172126 | 14.88:1 | 4.5:1 | Pass |
| Dark | text #EFF5F7 / canvas #11181C | 16.29:1 | 4.5:1 | Pass |
| Dark | muted #BAC9CF / surface #172126 | 9.63:1 | 4.5:1 | Pass |
| Dark | muted #BAC9CF / secondary #253238 | 7.75:1 | 4.5:1 | Pass |
| Dark | onprimary #063D43 / primary #80D4DD | 7.05:1 | 4.5:1 | Pass |
| Dark | primary #80D4DD / surface #172126 | 9.66:1 | 4.5:1 | Pass |
| Dark | onselection #B4EDF1 / selection #163E45 | 9.02:1 | 4.5:1 | Pass |
| Dark | outline #8AA2AD / surface #172126 | 6.13:1 | 3:1 | Pass |
| Dark | outline #8AA2AD / canvas #11181C | 6.70:1 | 3:1 | Pass |
| Dark | outline #8AA2AD / secondary #253238 | 4.93:1 | 3:1 | Pass |
| Dark | primary #80D4DD / secondary #253238 | 7.78:1 | 3:1 | Pass |
| Dark | urg #FFD58A / urgbg #45320C | 8.83:1 | 4.5:1 | Pass |
| Dark | flow #A9CEFF / flowbg #163553 | 7.77:1 | 4.5:1 | Pass |
| Dark | error #FFB4AB / errorbg #512B2C | 7.14:1 | 4.5:1 | Pass |
| Dark | success #A6DFB8 / successbg #203D2B | 7.86:1 | 4.5:1 | Pass |


### Type, spacing, shape, icons

| Token family | Concrete proposal and usage |
|---|---|
| Typeface | Platform sans-serif (Roboto where supplied; system Noto/script fallback). Bundle any necessary report font at build time subject to licensing; no network download before use. Font files are not a design deliverable. |
| Page title | 24sp/32sp, medium; use 20sp/28sp medium for compact task toolbar titles. Long titles wrap in content; toolbar names the task, not the entire customer address. |
| Section / key row | 18sp/26sp medium for section; 16sp/24sp medium for machine/service name. |
| Body / form value | 16sp/24sp regular. Persistent field labels and buttons 14sp/20sp medium minimum, critical action/explanation 16sp where space allows. |
| Metadata | 14sp/20sp regular. 12sp/16sp only nonessential report page furniture or minor app reference text that duplicates readable information elsewhere. Never reduce important status/date copy to fit. |
| Numbers / identifiers | Prefer tabular numerals for aligned date/quantity summaries if available; no all-monospace UI. Preserve case, leading zeroes and full serials; wrap long identifiers without inserting saved characters. Copy uses an explicit menu item when supported by the source. |
| Spacing | 4/8/12/16/24/32dp scale. 16dp compact page margins, 24dp at larger widths, 8dp between independent targets, 12dp inside row groups, 24dp between conceptual sections. |
| Targets / density | Minimum 48×48dp interactive target; common action and field min-height 56dp. Content can make them taller. Standard record row approximately 88–112dp at normal text, never fixed. No vertically packed 32dp chips for essential answers. Target expansion must not overlap a neighboring target [Q-01, Q-02]. |
| Shape | 8dp small control corners; 12dp task-block corners; 20dp top corners for transient sheet. No pill on every fact. A plain outlined field remains recognizable. |
| Elevation | Level 0 content, subtle divider at pinned bars; low elevation only for sheet/menu/FAB. Do not convey selection or urgency through shadows. |
| Icons | One bundled Material Symbols/Material icon family, usually 24dp inside ≥48dp target. Home: home; Work: checklist; Customers: groups; search: search; settings: settings; contact: phone with label; history: history; backup: backup/file; private: lock; error: error; overdue: event; working: edit-note; final record: description. Use labelled “More” overflow semantics and meaningful item labels. No separate bespoke icon library per trade. |
| Photos | Directory thumbnail 56dp square; detail identification 80dp; evidence grid 2 columns on 360dp, 1 column at large text. Fit within image bounds for identification, no crop that removes serial; tap full viewer. Missing identity photo: equipment-outline icon + “No photo”; missing retained evidence: broken-image icon + filename/context + “File missing,” not the same placeholder. |

### State and motion tokens

Pressed/hovered states use native restrained state feedback; selected state includes a checkmark/filled radio plus label. Keyboard focus has a 2dp primary outline with 2dp offset around the actual target, never clipped or hidden beneath a sticky footer. A filled primary button uses an outside contrast ring rather than a same-color inner stroke. Disabled action text stays readable, accompanied by the unmet condition; opacity alone does not explain it. Critical validation runs when attempted rather than hiding all controls until perfect input.

Use native screen, menu and Back transitions. Proposed custom duration, where needed: 150–200ms, no entrance sequence before content is usable. Disable nonessential custom motion when system animation reduction is in force. Optional selection/finalization haptic follows device/user availability; visual/text confirmation remains sufficient when it does not fire. No custom sound, confetti, animated counters, shimmer pretending to be progress, swipe-only command, drag-only reorder or long-press-only feature.


<a id="P-01"></a>

## P-01 · Action hierarchy and task chrome

A filled primary button performs the next named task. An outlined secondary action remains distinct; text actions open tools or perform harmless navigation. Destructive actions live in an explicitly named More item and a separate consequence review, not alongside Save in a bright red primary position. Primary form/review action sits in a bottom safe-area footer; it moves above the keyboard without covering the focused field. The content has matching bottom padding. If the action label needs two lines it grows; at 320dp or large text two footer actions stack. Toolbar retains at most Back, task title and one labelled More control. Only root directory lists use an extended Add FAB; its bottom padding and scroll padding clear navigation and last row. Work uses a tab-specific New button/selection footer, not competing FABs. Home's New visit is secondary to Resume. Menus close before an editor opens. No Undo is offered for finalization, file sharing, replacement restore, moves or lifecycle operations; use their actual correction/recovery paths.


<a id="P-02"></a>

## P-02 · Four persistence meanings

Ordinary forms: required fields marked “Required”, optional fields marked “Optional” on their label; explicit Save activates the record only after success. Cancel/Back with changes opens UI-41. A recovered ordinary buffer says “Recovered unsaved input — review and save”; it is not included in operational lists as a customer or plan. Child editors (template item, one-off setup, staged follow-up) use Save item/Use follow-up to update their parent draft, followed by the parent's explicit save/finalization. Working visit and correction fields autosave durably; the persistent status line says Saving… / Saved on this device · 10:14 / Not saved — action needed. No Cancel implies rolling them back. Immediate controls are limited to C's Make default site and backup-reminder preference, each with inline saved feedback; reminder settings themselves are explicit Save. IME Done hides the keyboard or commits one field, never finalizes a visit, resolves a task, sends a file or restores data. A save failure retains the current input when possible, keeps the last saved timestamp visible, and opens UI-58 for recovery.


<a id="P-03"></a>

## P-03 · Input and validation

Use persistent outlined labels and grouped fields, never placeholder-only labels. First tap focuses the first editable required field in a new short form; contextual selectors do not auto-launch a keyboard. Text supports normal editing and system clipboard; phone/email/decimal keyboards are hints, not validation. Numeric readings accept locale decimal notation, signed finite values and the template's visible unit; part quantity is positive, at most three decimals. No thousands separators while editing; localize display after commit. Preserve leading zeroes in identifiers as text. Next follows visual order; multiline Return creates a line; an accessible toolbar Save remains reachable. Validate after field exit when useful and on Save/Finalize, not with errors on an untouched empty form. A summary “Review 2 fields” at the top links to each error, scrolls it clear of keyboard/footer and focuses its input; field-level text says what to correct. Never erase other valid input. Limit counters appear near limits, not for every short field. Required reasons appear immediately after the choice that makes them necessary. Date entry shows an unambiguous localized example and full year; a proposed date never silently commits because the picker closed.


<a id="P-04"></a>

## P-04 · Lists, search, selection and return

List rows emphasize equipment/service or customer name, then distinguishing reference and site, then a dated state. The row body is one target opening detail; a separately bounded trailing action or booking badge is a sibling target, never nested inside it. No row has three tiny action icons. Filters use UI-54; applied scope/count/filters remain visible and independently removable. Search is local and not a network operation: blank query explains scope, results show number, zero matches says “No matches” with Clear search/filters, a genuinely empty register offers Add, and failed loading offers Retry without an empty-state illustration. Live filtering does not clear selection; footer says “4 selected · 1 hidden by filters” with View selected. Work selection is explicit and same-site constrained. Conflicting reserved obligations show “Open working visit,” not a misleading selectable checkbox. Preserve query, filters, sort, scroll and focused row on return; restore nearest surviving row if one disappeared after a valid mutation. Duplicate candidates show both identities side by side in UI-53; never merge or choose the first similar machine silently. Noninteractive count badges are announced with their label, not as buttons.


<a id="P-05"></a>

## P-05 · Inspection answers and review

Status question: a labelled radio group laid out as two columns at 360dp—OK / Issue found / Not applicable / Not checked—with every option ≥48dp tall; at large text use one column. Initial Not checked is an honest default, not a favorable answer. Required items marked Required remain incomplete for checklist review when Not checked. NA expands a required reason field; Issue found reveals the linked finding summary and one Edit finding action. The finding itself uses UI-23 to capture a real description/disposition. Text/numeric questions use a field plus “Not applicable” action; its selected state replaces the editable value with a labelled NA reason, with “Enter a value instead” to revert after confirmation of incompatible input. Blank optional text says Not recorded in the report. Changing an answer after Reviewed resets that review state with a persistent small “Changed since review” note. Mark checklist reviewed is explicit and validates required answers, not an equipment safety declaration. Saving incomplete answers or recording legitimate partial/unperformed work remains allowed. Keep/remove a finding is deliberate when its originating Issue answer changes; a live follow-up is not automatically deleted.


<a id="P-06"></a>

## P-06 · Identity, privacy and history

Show the active site and machine at every work transition; use generated EQ reference even when serial/internal ID is absent. A long customer/site name wraps in the opening context panel and has an “Identification” row for full details; a compact pinned header retains machine ref + service, not four truncated breadcrumbs. Every public notes group says “Customer report”; every internal group says “Private — not in customer report” with lock icon. Access notes are collapsed by default but reachable at the working overview and contact/map context; do not put gate codes in notifications, public summaries or copied prepared messages. Finalized snapshots have a Read-only / Revision label and current entity links explicitly named Current equipment / Current plan. Historical follow-up status says “At this visit…”; today's status is a separate live link. Editing a directory record does not refresh snapshots without UI-56's deliberate action. Never use green “safe” or “all clear” from a single checklist.


<a id="P-07"></a>

## P-07 · Feedback and interruptions

Inline saved/error text is the source for local state. A snackbar is only for a reversible, already-saved low-risk change or a noncritical handoff note; it is not the only record of failure. Persistent banner + linked error details for failed saving/missing required attachments; full result page for backup/restore/import; dialog only for a bounded discard/confirmation. Screen readers hear a polite summary once after an operation or validation attempt, not every autosave keystroke. A new blocking error can be announced promptly without stealing focus mid-typing; its Review action is reachable. Ordinary offline work has no red offline banner or fake sync spinner. An interrupted camera/file operation returns to its original saved context; if dataset/context changed, do not attach the late result elsewhere. Reopen after weeks recomputes due labels and explains a passed booking; it does not jump scroll while the user is selecting.


<a id="P-08"></a>

## P-08 · Photos and external actions

Before capture show site, EQ ref, evidence purpose and inclusion state. Save the parent checkpoint, then hand off to an OS-owned camera/picker—no ServiceLoop-drawn simulation of that surface. Intake is Copying photo… until validated app-owned content exists; cancel/failure leaves earlier media intact. Directory/logo image has no report-inclusion toggle; visit evidence defaults excluded and permits caption, explicit inclusion, view and remove. Copies are optimized under C's proposed limits; review fine print/serial legibility at full size. Replacing a historical file requires the matching original or a correction, never a silent substitute. Returning from dialer/composer/share sheet says only “Returned from [app/action]”; a persistent optional Record contact / Record handoff link captures what the technician actually knows. A missing handler gives Copy details/manual action; cancellation does not create a sent/delivered record. Exported files cannot be recalled by editing the app.


<a id="P-09"></a>

## P-09 · Consequences, dependencies and date comparisons

Use UI-43/46/57 for named operations with current → proposed values, affected records and the actual reason requirement. Blocked operations show why and provide a link to each blocker; the confirmation stays unavailable until revalidation succeeds. Opening a blocker preserves the review inputs and returns to a refreshed list. No hidden cascade and no “Are you sure?” without effects. A plan's appointment and obligation dates have distinct labels. A backdated/history-only entry never changes a schedule unless explicitly eligible and selected. Finalize is guarded against duplicate invocation; a retry of an already committed operation opens its record, not another advancement. No deletion, archive or correction claims to delete already exported customer copies.


<a id="P-10"></a>

## P-10 · File tasks and recovery

Stages are named: Check source → Prepare snapshot → Write file → Verify written file, or Choose → Unlock/inspect → Review replacement → Replace → Review restored data. Show item/byte progress only when known, otherwise a labelled indeterminate indicator; no made-up percentage or estimated time. Before commit Cancel leaves live data intact; the short final switch clearly disables cancellation and explains it. Result identifies dataset, snapshot time, selected destination and exact verification status. A provider URI/name is a destination, not proof of cloud availability. Do not say “Backed up safely” just because a picker returned. All restore warnings are in the main review, not only a tiny dialog. Wrong passphrase/corrupt/unsupported file retains current data and selection where safe; passphrases are never recovered as draft text or diagnostics.


## D2. Reusable layout families and reference viewport

Reference compact window: **360×800dp**, normal text scale. Wireframes assume illustrative 24dp top and 24dp bottom system insets; real insets are measured, not hard-coded. Root content budgets include a 56dp toolbar and approximately 80dp navigation bar. Task screens replace navigation with a 64–88dp safe-area action/footer region. These are composition estimates: the UI scrolls rather than shrinking text when actual bars, localization or fonts require more room. UI catalogue entries name the initial content at this reference size.

| Family | Anatomy / fixed versus scrolling | Instances |
|---|---|---|
| L-01 Root overview | Toolbar; optional blocking-save notice; ordered task blocks and summary links in one scroll; labelled root navigation fixed. | UI-02 |
| L-02 Filtered list | Toolbar; tabs/scope/search; active filters/count; vertically scrolling rows; selection footer when active; root nav only on roots. | UI-03/04/05/15/40/42/54 |
| L-03 Context detail | Toolbar; identity/status; dominant contextual action; section rows and read-only summaries; named More menu. | UI-06/08/10/13/16/19/25/27 |
| L-04 Explicit editor | Toolbar Cancel/title; fixed-context summary; field groups; optional expansions; bottom Save; linked error summary. | UI-07/09/11/14/17/23/28/29/31/32/48/49/55 child |
| L-05 Work workspace | Saved-state header; full site identity on entry; machine groups with direct service rows; machine evidence tab/area; bottom review action. | UI-20/21/22 |
| L-06 Consequence review | Title and reason/context; per-record before/after or outcome groups; blockers/warnings; named confirmation footer. | UI-12/24/26/35 replacement/43/46/47/52/56/57 |
| L-07 File wizard/result | Step title, snapshot/file identity, check results, one stage action, truthful progress, durable result page. | UI-34/35/36/37/51 |
| L-08 Media/report reader | Context/version state; content viewport; accessible text/page/zoom alternatives; labelled external actions separate from document navigation. | UI-39/44 |
| L-09 Short decision / handoff | Named context and bounded options; Cancel or Close; expands to full screen if text/keyboard demands it. | UI-41/45/50/53/59 |
| L-10 Settings/help/error | Grouped explanatory rows; state summary; no pretend toggles for absent features; persistent recovery actions. | UI-01/30/33/38/58 |

The wireframes in E instantiate every family and the high-consequence variants. They are structural diagrams, not images or interactive prototypes.


<a id="E"></a>

# E. Complete screen and state catalogue

All 59 UI surfaces below instantiate P-01–P-10. Source action placements are individually enumerated in the linked appendix; no unnamed overflow bucket overrides these entries. Every entry states normal-size initial content, while large-text adaptation changes the fold rather than hiding capabilities.

<a id="UI-01"></a>

## UI-01 · First use

**Traceability and surface:** C:S01; F:S01. Layout L-10.

**Initial viewport:** ServiceLoop title, one-sentence local-service-book premise, Start empty, Import directory and Restore backup. No keyboard, permissions, fake customers or logo hero.

**Anatomy:** One vertically scrolling column, 24dp section gaps. Primary Start empty, secondary Import directory/Restore backup, text How local storage works. No root bar until start/import/restore succeeds.

**Content and inputs:** Copy: “Keep customer equipment, service work and reports on this device.” Below actions: “No ServiceLoop account needed. A backup is needed to recover after device loss.” No fields. The local-storage explanation is UI-38 topic, not an onboarding slide deck.

**Visible actions and menus:** Start empty → UI-02 empty. Import existing directory → UI-37. Restore backup → UI-35. How local storage works → UI-38. F’s immediate Add first customer and Business details shortcuts are not active on this welcome surface; both remain reachable after Start empty, as recorded in X-07/appendix.

**Interaction, focus and persistence:** Back from import/restore before commitment returns here. Failed import/restore never counts as first-use completion. Start empty is not an erase operation; it is offered only on genuinely empty initial state. Focus begins on title, not the primary action.

**Meaningful states:** Interrupted ordinary setup reopens its recovery card only after the relevant context exists. A corrupt/unreadable dataset goes to UI-58, never this empty screen. Completed restore opens its explicit result, not Start empty.

**Resilience:** Buttons stack, text wraps, 320dp and 200% text remain a scrollable page. No forced setup carousel.

**Rationale:** First useful work is not gated on branding, notifications or an account.

```text
L-10 / UI-01
ServiceLoop
Keep customer equipment, service work
and reports on this device.
[Start empty]                   primary
[Import existing directory]
[Restore backup]
No account needed. Back up to recover
from device loss.
[How local storage works]
```

<a id="UI-02"></a>

## UI-02 · Home

**Traceability and surface:** C:S02; F:S02; FC-01. Layout L-01.

**Initial viewport:** At T2: Home toolbar with Search and Settings; Resume visit block with site excerpt, V-001, saved timestamp and direct Resume; next booking V-002; start of Overdue services. All five summaries are present in the scroll, not necessarily above fold.

**Anatomy:** Toolbar → blocking current-save banner only when relevant → unfinished/resume block → next imminent booked visit → Overdue/Due soon → due follow-ups → Records needing attention → quiet recovery/reminder notices. Root nav fixed. If no working visit, upcoming booking becomes first task. Up to three items per work summary, then View all. Additional working visits are links below the most recently edited one, not hidden. Correction drafts have their own attention group, not the visit count.

**Content and inputs:** T2 labels: “Unfinished visits · 1”, “Saved on this device · 10:14”, “Booked visits · 1”, “Tue 8 Sep · 14:00”, “Overdue services · 4”, “Due soon · 1”, “Follow-ups due · 1 · All open 1”. Counts name units. At T3 follow-ups show 1 due / All open 2. Optional backup notice says snapshot age/changed status, never generic cloud status.

**Visible actions and menus:** Resume/open row → exact UI-20 or UI-19. Each View all opens corresponding UI-03 state/date filter: overdue active; today–horizon active; Booked next seven days; Working all; Open follow-ups through today, with All open link. New visit is a secondary labelled toolbar/content action below initial resume block, never competes as second filled button. Empty state offers Add first customer. Search → UI-04; Settings → UI-30; Work/Customers root nav. Backup notice → UI-33/58 according to actual problem; reminder notice → UI-32/50. FC-01 report attention → UI-40 records needing attention; correction draft → UI-26. Passed booked visits show separate “Past bookings · review” → Work Booked/past.

**Interaction, focus and persistence:** Count navigation carries a visible removable filter label, not a secret query. Back from a row returns to the same block/scroll. No keyboard on entry. Root navigation never confirms harmless departure. A saved current draft may be left; a real unsaved error invokes P-02/P-07.

**Meaningful states:** Empty: “Add a customer to create your first service obligation,” Add first customer primary, import under Getting started. No overdue: plain “No overdue services,” not a celebratory safety claim. All five counts stay reachable even when zero. Multiple urgent data errors collapse into one named persistent banner opening their list; routine backup age stays low prominence.

**Resilience:** Long names show two wrapping lines plus EQ/visit reference; details expose full names. Cards grow. At large font the initial viewport may contain only the resume block—counts remain below, not shrunk. No auto-scrolling as counts refresh.

**Rationale:** Home answers “what next?” while Work holds complete queues. “Booked service remains here until fulfilled” appears as a short help line under due counts, explaining overlap without teaching the data model.

```text
L-01 / UI-02 / T2
Home                           [Search] [Settings]
Unfinished visits · 1
Riverside Centre — East Building…
V-001 · 3 machines · 4 services
Saved on this device · 10:14
[Resume visit]                         → UI-20

Booked visits · 1                 [View all]
8 Sep · 14:00  Assess console rattle
T-02 · Riverside Centre             [Open]

Overdue services · 4              [View all]
T-01 · Condition inspection
Due 1 Sep · Working V-001
----------- reference fold -----------------
Due soon · 1                     [View all]
Follow-ups due · 1               [All open 1]
Records needing attention (only when present)
[New visit]   Backup snapshot: 1 Sep [Review]
[Home selected]      [Work]       [Customers]
```

<a id="UI-03"></a>

## UI-03 · Work: three task lists and selection

**Traceability and surface:** C:S03; F:S03; F:U06; FC-01. Layout L-02.

**Initial viewport:** Toolbar Work with Settings/More; tabs Due services, Visits, Follow-ups; local search; Filters and Sort; removable scope chips, result count, first two to three rich rows. T2 Due defaults to 5 services (4 overdue plus P-005 due soon), not only overdue.

**Anatomy:** Tabs stay above list; search can collapse to a search field affordance while scrolling but active query/filter summary stays visible. Rows use separated full-body opening and a distinct trailing booking/working link where present. Selection replaces toolbar with count/Cancel selection and adds Book selected, Start selected, Contact footer; no root FAB under that footer. Root nav remains separate.

**Content and inputs:** Due row: service name, equipment name/internal ID/generated ref, customer/site excerpt, obligation date with urgency, separately named booking/working status, latest relevant contact excerpt when present in the underlying context (full contact through plan; not an invented communication feed). Visits row: appointment or actual date explicitly labelled, site, state, machine/service counts. Follow-up row: title, Contact/Corrective label, due date, equipment/site, source link through detail. Filters/sorts are instantiated in UI-54.

**Visible actions and menus:** Tab controls switch and retain independent state. Search/Clear and Filters/Sort → UI-54. Row → UI-13/19/20/25/27 by actual type/state; working/booking badge → reserving visit. “Select services” in Due More enters checkbox mode; same-site eligible items only, reserved work instead opens its visit. Book selected/Start selected → UI-18 prefilled but reviewed; Contact selected site → UI-45. New visit on Due/Visits; New follow-up on Follow-ups. More menu: Select services (Due), History (FC-01, all tabs); no bulk Complete. Settings/Home/Customers remain reachable.

**Interaction, focus and persistence:** In selection, full-row tap toggles a checkbox; separate Open details text action remains available. Choosing another site explains “Select services from one site per visit” and offers Clear selection, never silently drops earlier picks. Filter-hidden selection is counted and View selected restores it. Back exits selection before leaving. After finalization return refreshes dates while preserving list context; removed rows are announced once.

**Meaningful states:** Empty dataset offers Add equipment path to UI-11; empty filter says No services match and Clear filters; query error says Cannot read work list/Retry. Past bookings remain Booked with “Appointment passed—review”; they are not silently cancelled. Cancelled/finalized/paused/ended views are explicit filters with appropriate read-only rows.

**Resilience:** At 320dp the three tab labels may form horizontally scrollable native tabs with visible selected semantics, or wrap two lines without tiny targets. At 200% text choose full-width tab selector control labelled “Work list: Due services” that opens all three named choices; this is the same navigation, not a hidden new mode. No row forces all identity text onto one line.

**Rationale:** Grouping by work type avoids a calendar-first system and keeps due obligations independent of appointments.

```text
L-02 / UI-03 Due / T2
Work                       [More] [Settings]
[Due services] [Visits] [Follow-ups]
[Search services, equipment or customer  ×]
[Filters 2] [Sort: Due date]   5 services
Active · Overdue to 19 Sep                  ×
Condition inspection · T-01 / EQ-001
Riverside Centre…   Due 1 Sep · 4 days overdue
[Open service]                 [Working V-001]
Lubrication service · T-01 / EQ-001
Due 1 Sep                      [Working V-001]
… P-003, P-004, P-005
[New visit]                 [Home Work Customers]

UI-03 Visits / T2
[Due services] [Visits] [Follow-ups]
[Search] [Filters] [Sort]      2 visits
Working · V-001 · 5 Sep (actual)
Riverside Centre… · 3 machines · 4 services
Saved 10:14                              [Open]
Booked · V-002 · 8 Sep 14:00 (appointment)
Assess console rattle · T-02             [Open]

UI-03 Follow-ups / T3
[Due services] [Visits] [Follow-ups]
[Search] [Open · All dates] [Sort: Due]
2 follow-ups
Contact · FU-001 · Due today, 5 Sep
Confirm access for the return visit      [Open]
Corrective · FU-002 · Due 12 Sep
Replace worn belt — T-01                 [Open]
```

<a id="UI-04"></a>

## UI-04 · Global search

**Traceability and surface:** C:S04. Layout L-02.

**Initial viewport:** Back, focused search field, scope line “Search your saved records”, entity chips, inactive option and result count. With blank query show examples and no invented recent-search history.

**Anatomy:** Full screen, keyboard initially open only when deliberately entered through Search. Search field and selected entity persist above result list. Each result has a type label and minimum distinguishing context, not a flat ambiguous list of names.

**Content and inputs:** Query matches source-supported names, references, serials/identifiers and known entity fields; not OCR or image content. Chips All, Customers, Sites, Equipment, Plans, Visits, Follow-ups. Include inactive Off. Results show full matched identifier on its own line when it disambiguates similar machines.

**Visible actions and menus:** Type/Clear; entity chip; Include inactive; result row → exact record state; Back to originating root/detail. No add button inside global results; create flows use the appropriate register/context.

**Interaction, focus and persistence:** Debounced local results may update while typing without stealing keyboard focus or announcing every character. Announce count after pause or search submission. Back dismisses keyboard then returns, preserving caller. Selecting an inactive result does not reactivate it.

**Meaningful states:** Blank instructions, no matches with Clear, unreadable data with Retry, active/inactive badges. Deleted/missing target from an old notification/search state gets “Record no longer available” and a parent-list route.

**Resilience:** Wrap long identifiers; highlight matched text with weight/underline, not color alone. Result groups preserve headings for TalkBack. Large directory search must be tested under I, not promised instant.

**Rationale:** Direct access makes customer→site drill-down optional while retaining identity at the destination.

<a id="UI-05"></a>

## UI-05 · Customers and equipment register

**Traceability and surface:** C:S05; F:S04/F:S09. Layout L-02.

**Initial viewport:** Customers toolbar, Customers/Equipment switch, search/filter/sort, active scope/count, first three customers or two equipment rows, extended Add button clear of root nav.

**Anatomy:** One register with two modes, not two roots. Header and controls fixed; rows scroll; Add customer/Add equipment extended FAB at lower end-side. In import-result scope a removable “Imported batch” chip is above rows.

**Content and inputs:** Customer row: name, generated ref, default site excerpt, sites/equipment counts. Equipment row: name, EQ ref/internal ID, make/model, serial or Serial not supplied, site/customer, active-plan presence or nearest due summary. Customer active/archive filter; equipment In service/Retired/All, customer/site and plan-presence filters; name/recently changed/next due sort.

**Visible actions and menus:** Switch, Search/Clear, Filters/Sort → UI-54; rows → UI-06/10; Add → UI-07/11 with known scope retained; Search all records → UI-04 via toolbar search scope action; Settings → UI-30; Home/Work root nav. An Import directory link appears in the empty-state guidance and otherwise under Data and recovery—not an unconditional second FAB.

**Interaction, focus and persistence:** No keyboard until search or new editor. Restore same selected mode/filter/position after save. Imported scopes may be cleared without losing imported records. Nonblank same-name results retain separate rows.

**Meaningful states:** Empty: Add customer or Add equipment, plus import explanation. No matches: Clear search/filters. Archived records remain readable. Equipment without active plans has “No active service plan” and Add plan in detail, not a fabricated due date.

**Resilience:** Equipment image is optional 56dp thumbnail, not giant card art. At large text remove thumbnail from row before removing identity; it remains on detail. Scroll accommodates all records; no unannounced product cap.

**Rationale:** A growing directory needs search and identity, not a deep tree or a dashboard of counts.

<a id="UI-06"></a>

## UI-06 · Customer detail

**Traceability and surface:** C:S06; F:S05. Layout L-03.

**Initial viewport:** Customer name/ref, Active/Archived label, Contact button, Sites section with default site and Add site. Long name is full here. Counts and history continue below.

**Anatomy:** Header → current contact summary → Sites → Equipment/Due services/Visits/Follow-ups navigation rows → History → Private notes. More menu is the sole lifecycle/deletion entry. Archived banner precedes action availability.

**Content and inputs:** Name; optional main contact name, phone/email; site count, default marker; linked equipment/work counts with units; private customer notes collapsed by default. Missing contact says Not supplied with Edit customer link.

**Visible actions and menus:** Visible Contact; Edit in toolbar/More; site rows and Add site; contextual section rows; History; New visit, Record contact, Add follow-up in a labelled “Customer actions” expansion. Add first equipment inline empty state opens site choice/default review. More explicitly: Edit customer, Archive customer or Restore customer, Delete unused customer (only eligible; otherwise no delete, explanation under lifecycle). F Show archived sites is realized as the Sites section Active/All presentation toggle where inactive sites exist; it is navigation, not restoration.

**Interaction, focus and persistence:** Contact uses UI-45 current chosen recipient. New visit with several sites opens UI-18 site selection; a default site is suggested visibly, not a silently chosen machine. Edit explicit save. Archive → UI-43 with unresolved children links; no cascade. Return restores this section.

**Meaningful states:** Archived detail read-only except allowed Restore/contact/history according to source context; operational new work requires active context. Empty sites shows Add site; full dependency state lists blockers before archive. Failed read never offers destructive buttons over an empty fallback.

**Resilience:** Long names wrap; phone/email are selectable in contact surface, no tiny adjacent icon row. Many sites show first three plus View sites selector UI-42 with inactive navigation filter, not an invented site-management root.

**Rationale:** The customer page is a relationship hub, while frequent equipment actions remain directly reachable elsewhere.

<a id="UI-07"></a>

## UI-07 · Customer editor and first site

**Traceability and surface:** C:S07; F:S06; X-08. Layout L-04.

**Initial viewport:** Cancel / Add customer; customer name field first and focused. Main contact group begins in viewport; First site group follows. Save customer is fixed above keyboard.

**Anatomy:** Customer identity → optional main contact → First site (new only) → Private notes expansion. Editing an existing customer shows a site-summary link instead of copying site fields into this form.

**Content and inputs:** Customer name Required. Contact name, Phone, Email Optional; phone/email have labelled Choose from contacts actions using UI-59 selected-data picker. New-only First site name Required default “Main site”; Site address Optional; Use customer contact On. C creates customer + first site together. Private notes Optional. Existing customer: Edit site details → UI-09. No tax/account/billing fields.

**Visible actions and menus:** Save customer; Choose phone/Choose email; Edit site details existing-only; Cancel/Back. Duplicate warning → UI-53 Open existing/Create separate/Back. The First site group does not offer F’s separate independent site-contact editing here; choose UI-09 after save when required, preserving C’s creation contract.

**Interaction, focus and persistence:** Next advances name→contact fields→first site; multi-line address Return is text. Save validates name/site label and supplied email, retains all input on error. Selected contact value fills only the chosen field, never grants ongoing address-book sync. Cancelling picker leaves current value. Save returns to UI-06, showing Add first equipment.

**Meaningful states:** New/edited/recovered-unsaved modes labelled. Possible duplicate is warning not forced merge. Existing linked records unchanged until save; recovered ordinary input not a live customer. UI-41 on dirty leave.

**Resilience:** Two-column contact shortcuts wrap to separate rows at large text. Current values remain visible while keyboard moves focus; no expanding decorative header.

**Rationale:** Only customer name is typed on a minimal first pass; a clearly visible default site avoids an obligatory second form without concealing the hierarchy.

```text
L-04 / UI-07
[Cancel]             Add customer
Customer name · Required
[Harbor Fitness and Rehabilitation…]
Main contact (optional)
Contact name [                    ]
Phone [                 ] [Choose phone]
Email [                 ] [Choose email]
------ scroll / keyboard-safe -------
First site
Site name · Required [Main site]
Address · Optional [                    ]
Use customer contact: On
[Private notes · Optional             ▾]
[Save customer]   explicit durable save
```

<a id="UI-08"></a>

## UI-08 · Site detail

**Traceability and surface:** C:S08; F:S07. Layout L-03.

**Initial viewport:** Site full name/ref, parent customer link, address, Contact and Open map; equipment section begins with T-01 and T-02 identity rows.

**Anatomy:** Identity/contact source → contextual action area → Equipment with Show retired toggle → due/visit/follow-up counts → History → Private access notes collapsed. More contains Edit, Make default, archive/restore, eligible delete.

**Content and inputs:** Contact shows “Uses customer contact” or “Site contact” with actual source. Access notes have lock/private label and are not in report summaries. Default site is a text badge, not a misleading toggle that changes merely on viewing.

**Visible actions and menus:** Customer breadcrumb, Edit site, Contact/Open map, Make default site (immediate Saved feedback), equipment rows/Add equipment, Show retired equipment, Due services/Visits/Follow-ups/History. Book visit is primary when no working visit selected; Start visit, Record contact, Add follow-up are labelled secondary actions. More Archive/Restore site, Delete unused site iff eligible.

**Interaction, focus and persistence:** Book/Start opens setup with site confirmed and no ambiguous equipment choice. Archive review requires equipment moved/retired and work/tasks resolved; deleting an eligible unused default site clears default under C, not F’s sole-site block. Back returns caller with scope intact.

**Meaningful states:** No address: “Address not supplied” plus Edit site instead of launching empty navigation. Archived requires Restore before new work. Missing customer read: explain and route recovery, not reparent. Numerous equipment uses scoped register View all.

**Resilience:** Address wraps fully; Show access notes is ≥48dp target and persistent text rather than tooltip. Two contact buttons stack when needed.

**Rationale:** Location and contact source must remain explicit when one customer has several nearly identical sites.

<a id="UI-09"></a>

## UI-09 · Site editor

**Traceability and surface:** C:S09; F:S08. Layout L-04.

**Initial viewport:** Cancel / Add or Edit site; customer selector or fixed customer; site name; address; Save site footer. New name gets focus after customer choice.

**Anatomy:** Parent → location → Contact source and conditional custom fields → Private access notes. Default-site setting stays on detail as C’s immediate action, not a second staged switch in this form.

**Content and inputs:** Customer Required, changeable only for new/wholly unused site. Site name Required, distinct within customer. Address Optional. Use customer’s main contact On; Off reveals optional Contact name/Phone/Email and Choose phone/email. Private access notes Optional with exclusion label.

**Visible actions and menus:** Select customer → UI-42; contact source switch; selected phone/email pickers → UI-59; Save site; Cancel/Back. Matching same-customer site name is a blocking distinct-name error; View existing candidate is navigation, not a “force duplicate” override.

**Interaction, focus and persistence:** Changing parent when eligible revalidates name/contact context and asks before losing incompatible staged custom context, never moves historical equipment. Save is explicit; picker cancellation preserves input; Back uses UI-41. A toggled inherited contact does not copy stale values into reports.

**Meaningful states:** Inherited contact fields are read-only preview rather than dimmed editable boxes. Missing inherited data is clearly Not supplied. Existing used-site parent has “To move equipment, use equipment → Move” guidance, not an enabled unsupported reparent picker.

**Resilience:** Multiline address and private notes grow; inherited details do not disappear from accessibility order. No address geocoding prerequisite.

**Rationale:** Separating contact inheritance from address avoids confusing a customer phone change with a site move.

<a id="UI-10"></a>

## UI-10 · Equipment detail

**Traceability and surface:** C:S10; F:S10. Layout L-03.

**Initial viewport:** Full machine name, T-02 / EQ-002, Serial not supplied, site/customer link, optional 80dp photo and status; service plans including nearest due visible; Book/Start action strip below identity.

**Anatomy:** Identity → named visit actions → Service plans → Open corrective follow-ups → History → Current equipment notes. Latest observation is a dated historical excerpt labelled as such, not a persistent machine safety state. More groups lifecycle operations below Edit.

**Content and inputs:** Name/type/make/model, serial/internal/generated reference, site, photograph, private notes. Each plan row has its own title, interval, due and linked-booking state. Show paused/ended plans controls visibility but never changes state. Plan rows do not merge two obligations on one machine.

**Visible actions and menus:** Edit equipment; site/customer links; photo viewer; Plan row/Add service plan; Show paused/ended; Book visit / Start visit / Record past visit; follow-up row/Add corrective follow-up; History. More: Edit equipment, Move equipment, Retire equipment or Return to service, Delete unused equipment iff eligible. Move → UI-12, retirement → UI-43.

**Interaction, focus and persistence:** Book/Start may preselect this exact machine visibly, but service choices are reviewed in UI-18/55. If a plan already belongs to a working visit, opening it offers Resume rather than duplicating its claim. Returning to service leaves C’s ended plans ended; Add a new plan is available. No auto-resume inferred from “active.”

**Meaningful states:** No serial is permitted and prominent, not an error; generated reference persists. No photo has labelled placeholder. Retired is read-only except allowable return/history actions. Missing historical image has distinct failure state. No active plan prompts Add service plan without fabricating a due date.

**Resilience:** Long ID wraps and full identity available in Identification section. Similar machines always show distinct T-/EQ references. One-handed actions are under identity, not only top-right overflow.

**Rationale:** Equipment identity is more important than a large photo; service plans must remain individually scannable.

```text
L-03 / UI-10 / T2
[Back]               Equipment             [More]
Treadmill 02 — door side
T-02 · EQ-002                      [No photo]
StrideWorks R8 · Serial not supplied
[Riverside Centre — East Building…]
[Book visit] [Start visit]
Service plans                         [Add]
Maintenance · Every 6 months
Due 1 Sep · Working V-001             [Open]
Electrical inspection · Every 12 months
Due 15 Sep                           [Open]
[Show paused/ended plans]
----------- reference fold -----------------
[Corrective follow-ups]   [Add follow-up]
[History]  Latest observation: … at [date]
[Private equipment notes ▾]
More: Edit · Move · Retire · Delete unused (eligible)
```

<a id="UI-11"></a>

## UI-11 · Equipment editor

**Traceability and surface:** C:S11; F:S11. Layout L-04.

**Initial viewport:** Cancel / Add equipment; selected site summary and Change site when allowed; Equipment name field; make/model group begins. Save equipment remains above keyboard.

**Anatomy:** Context → essential identity → optional identity details → Photo → Private notes. On a minimal new record only site and name are required; identity details remain one visible expansion away, not a separate wizard.

**Content and inputs:** Site Required (fixed for existing equipment; moving is separate). Equipment name Required. Type, Make, Model, Serial number, Internal identifier Optional, each text not numeric. Photo Optional. Private notes Optional. Show generated EQ reference after save, never ask the user to type it. Suggested values are not a manufacturer database or automatic machine recognition.

**Visible actions and menus:** Choose site → UI-42; Add/change/remove photograph → UI-44; Save equipment; Cancel/Back. Duplicate candidate UI-53 offers Open existing / Create separate record / Back. C permits a explained duplicate identifier warning, unlike F’s hard internal-ID uniqueness; the UI must not present a contradictory hard error.

**Interaction, focus and persistence:** Name gets initial focus once context is selected. Save validates supported lengths and identity conflicts, shows candidate identity side by side before deliberate separate creation. Image intake may complete into this editor’s staged state; cancellation of the editor removes its uncommitted reference, not an existing machine. Existing-site field opens explanation of Move, not a normal editor.

**Meaningful states:** New/edited/recovered input; missing optional identifiers valid; archived/retired access follows detail policy. Save failure retains text and prior photograph. Source-photo failure leaves “Photo not added” with Retry, not a broken saved identification.

**Resilience:** Optional identity fields expand to full width at large text; no row of narrow make/model text boxes. Full serial visible on edit without ellipsis.

**Rationale:** Low initial entry effort is compatible with honest “Serial not supplied,” provided generated and internal references remain distinguishable.

<a id="UI-12"></a>

## UI-12 · Move equipment

**Traceability and surface:** C:S12; F:U03/F:S10-A09; X-04; FC-03. Layout L-06.

**Initial viewport:** Move equipment title, exact machine identity and current owner/site; destination chooser; any blockers appear before the confirm action. No keyboard until date/reason.

**Anatomy:** Current → destination identity panels → effective date and required reason → retained plan/date list → history/privacy explanation → acknowledgement → fixed Move equipment footer. This is a full screen, not a tiny confirmation.

**Content and inputs:** Destination site Required, chooser displays customer+site together. Effective date Required, not future. Reason Required. Explicit acknowledgement that current plan states and due dates shown will travel with the machine. Cross-customer copy: “Future work will belong to [new customer]. Earlier records keep [old customer]’s identity. Private old-customer details are not copied into new reports.”

**Visible actions and menus:** Choose destination → UI-42; each blocking booked/working visit, open equipment follow-up, or related correction draft (FC-03) → named detail; Confirm move only after all checks/acknowledgement; Cancel returns unchanged. Destination selection alone changes nothing. Cross-customer active plans are shown as Active with their dates under C; do not silently pause them.

**Interaction, focus and persistence:** Revalidate after returning from blockers and at confirmation. A changed destination invalidates the earlier acknowledgement. Back with staged input offers Keep editing/Discard, not an irreversible move. Success returns equipment detail with new site and a dated move event in history. Saved records retain original snapshots.

**Meaningful states:** Same-site destination is unavailable with explanation. Inactive destination not selectable for new work. Blocked state lists counts and links, not a generic error. Save failure keeps old relationship and all plans unchanged. A correction draft remains intact; opening it does not commit it.

**Resilience:** Long old/new customer names each get full-width wrapping panels; do not use a cramped side-by-side table on phone. At 840dp they may sit beside each other with explicit From/To labels.

**Rationale:** The customer transfer is a data-lifecycle decision. Its plan consequences must be visible even when the names look similar.

```text
L-06 / UI-12
[Back]                  Move equipment
T-01 · EQ-001 · Treadmill 01 — window side
From: Harbor Fitness… / Riverside Centre…
To:   [Choose customer and site]
Effective date [5 Sep 2026]
Reason · Required [                         ]
Plans carried unchanged
Condition inspection · Active · Due 1 Sep
Lubrication service · Active · Due 1 Sep
Cannot move yet
[Working visit V-001 — open]              blocker
[Open corrective task FU-002 — open]      if present
[Correction draft — open]                 FC-03
[ ] I reviewed the carried plan states and dates
[Move equipment — unavailable: resolve blockers]
[Cancel]
```

<a id="UI-13"></a>

## UI-13 · Service-plan detail

**Traceability and surface:** C:S13; F:S12. Layout L-03.

**Initial viewport:** Plan title, T-01/EQ-001 and site, Active/Paused/Ended label, due date, interval, linked visit when present. Open working visit dominates over a misleading Start button when claimed.

**Anatomy:** Context → obligation/interval → current arrangement → template → latest fulfillment/known prior service → history. Plan tools in named More menu; contacts are a labelled action group below current arrangement.

**Content and inputs:** Show “Service due 1 Sep 2026”, separately “Working in V-001” or appointment date. Interval “Every 3 months; next date proposed from actual completion.” Latest counted completion distinguished from a supplied previous-service date. Template row includes name/revision/archive status. Ended carries no live due urgency; retained date is labelled historical.

**Visible actions and menus:** Edit plan; Book service/Start service only unclaimed active context; Open linked visit; Contact, Record contact, Add contact follow-up; template row; Pause plan, Resume plan, End plan via UI-46/43; Record past service; Delete unused plan only eligible; Equipment and History entries. F direct Change next due is presented through Edit plan’s date field plus reasoned review, not an untracked quick edit.

**Interaction, focus and persistence:** Working claim blocks interval/template changes and lifecycle actions under C; booked edits produce start-time review as source allows. Pause keeps due date. Resume explicitly keeps or replaces it. End is terminal, not a toggle. Plan deletion does not become available merely because it is paused.

**Meaningful states:** Paused shows retained due and “No due reminders while paused”; Resume primary. Ended shows read-only historical content and no Resume. Template archived may remain assigned with clear retained-template label. Past entry default History only, not automatic fulfillment.

**Resilience:** Interval and date never share an ambiguous “next visit” label. Long plan/equipment names wrap; a source history link opens the precise record and returns here.

**Rationale:** A plan answers obligation questions. Appointment actions are contextual links, not a substitute due date.

<a id="UI-14"></a>

## UI-14 · Service-plan editor

**Traceability and surface:** C:S14; F:S13; X-05/X-07. Layout L-04.

**Initial viewport:** Add service plan / exact machine; Plan name; Every interval controls; Next due date; Save plan footer. Initial focus Plan name.

**Anatomy:** Machine fixed context → name → interval → initial/current due → Optional earlier service → Optional inspection template. In edit mode show Current due before any changed proposal and a required reason when relevant.

**Content and inputs:** Plan name Required, distinct from Active/Paused plans on same machine. Every Required positive whole number, default 6; unit default Months (Days/Weeks/Months/Years as supported by source date recurrence). Next due Required and initially blank; overdue dates allowed with explanation. Earlier service date Optional, not future; source note Optional. Inspection template Optional None; choose/clear via selector. “Use calculated date” appears only with sufficient earlier-service inputs; it is never automatic.

**Visible actions and menus:** Choose/clear template; Use suggested due date; interval/date controls; Save plan; Cancel/Back. Editing interval alone shows “Current due stays [date]”; changing due requires reason and UI-46-style before/after review. Existing booked visit link explains revalidation on start; working claim blocks the editor with Open working visit.

**Interaction, focus and persistence:** Keyboard numeric for interval, date picker for date, Next flows to due. Earlier information becomes locked after a counted fulfillment under C; it remains readable. Save produces exactly one plan, not a duplicate if tapped twice. Template selection returns to the same scroll/focus.

**Meaningful states:** Invalid interval/date/name errors are local plus summary. No template is valid. No previous service is valid but Next due still required. Changed interval does not silently recalculate a live obligation. Month-end/leap-year examples appear in a brief calculation explanation (e.g. 31 Jan + 1 month → last February day).

**Resilience:** Interval number and unit may share a row at ordinary scale, stack at large text. No auto-shrinking names; saved limits shown near maximum.

**Rationale:** One explicit initial due date avoids deriving business truth from an optional historical note.

<a id="UI-15"></a>

## UI-15 · Inspection-template library

**Traceability and surface:** C:S15; F:S23. Layout L-02.

**Initial viewport:** Templates title, search, Active/Archived/All selector, Sort, count, first rows, New template action. No keyboard until search.

**Anatomy:** Full list entered from Settings or selector; search/filter area fixed; rows scroll. Each row names item count, latest revision and active/archive state. In selector mode selection intent remains in header.

**Content and inputs:** Row example “General condition check · 5 items · Revision 2”; no trade-authoritative preinstalled checklist claim. Sort Name or Last changed. Search by template name, not a form-builder catalog.

**Visible actions and menus:** Search/Clear; Active/Archived/All; Sort; row → UI-16; New template → UI-17. Back returns Settings or calling template chooser; source C new selection requires active templates. No import/export or online marketplace added.

**Interaction, focus and persistence:** New template saves to library; calling selector may then choose it explicitly. Discarding a template editor restores query/position. Archived rows remain visible in Archived/All without being reactivated.

**Meaningful states:** Empty Active library: “Create a reusable inspection checklist” with New template; no matches: Clear. Failed read: Retry. Inactive templates not selectable for a new assignment but inspectable.

**Resilience:** Template titles wrap; count is always “items,” never “passed”. Large libraries use same accessible list pattern.

**Rationale:** Templates are reusable question sets, not a second workflow automation product.

<a id="UI-16"></a>

## UI-16 · Template view and preview

**Traceability and surface:** C:S16; F:S24 view. Layout L-03.

**Initial viewport:** Template name/revision/state, item count, Edit (active) and ordered item preview. Assigned plans link and More remain discoverable.

**Anatomy:** Header → active actions → ordered read-only question rows with type/unit/required labels and optional guidance expansion → Assigned plans. Preview uses actual inspection answer appearance but disables data entry and says “Template preview—no visit record.”

**Content and inputs:** Each item shows question label, Status/Text/Number, Required/Optional, numeric unit, internal guidance. No prefilled OK answer, even in preview. Assigned plans count opens Work All states with a template scope chip.

**Visible actions and menus:** Edit → UI-17; Duplicate → UI-17 unsaved copy with distinct-name requirement; Assigned plans → UI-03 scoped; More Archive/Restore template and eligible Delete unused; Use this template footer only when entered as active selection. Archived assignment explanation: “Existing assigned plans can keep this saved checklist. New assignments use active templates.”

**Interaction, focus and persistence:** Archive is a reviewed lifecycle action, not a deletion of old runs. Editing creates a new revision, never updates working/finalized copies. Duplicate archived template creates a new active proposal, not reactivation of its assignments. Back returns library/selector.

**Meaningful states:** Archived view has Restore/Duplicate, no Edit/Use new assignment. Empty template is only an invalid editor draft, never a valid saved library entry. Referenced template cannot be deleted; show usage route.

**Resilience:** Long guidance expands inline; screen reader order follows item order and announces requiredness/type. Large text does not turn preview into horizontal scrolling.

**Rationale:** The view lets a technician verify a template without accidentally recording an inspection.

<a id="UI-17"></a>

## UI-17 · Template editor

**Traceability and surface:** C:S17; F:S24 edit. Layout L-04.

**Initial viewport:** Cancel / Edit template, name, ordered first items, Add item; Save template footer. First new template name focused, existing editor no unsolicited keyboard.

**Anatomy:** Name → revision-change explanation → ordered question list → Add item → Preview; Save fixed. Each question row has Edit body plus a labelled More item menu, not three tiny reorder/delete icons.

**Content and inputs:** Template name Required, unique among active templates; at least one item. Item rows show requiredness/type/unit. Editing a previously used template states “Creates a new revision; existing inspections keep their copy.”

**Visible actions and menus:** Add item/Edit item → UI-48 staged child. Item menu: Move up, Move down, Remove item. Boundary move disabled with clear first/last position; drag is optional supplementary gesture. Remove asks named-item confirmation and updates staged list. Preview → UI-16 preview mode; Save template; Cancel/Back. No branching, formula, answer automation, item cloning or report designer.

**Interaction, focus and persistence:** Child Save item affects parent form only. Parent Cancel offers discard including staged reorder/items. Save creates revision and returns to library or caller. Reorder keeps focus on moved item and announces its new position, e.g. “Belt condition, item 2 of 5.”

**Meaningful states:** Zero items error on Save, duplicate name distinct-name error; unsaved/recovered parent buffer explicitly labelled. Failed save keeps ordered staged content; old saved revision unchanged.

**Resilience:** Reorder uses full menu targets; many items use a scroll list, with an accessible Add item footer at end and toolbar action for convenience only if one existing add action is mirrored. Never require precise drag.

**Rationale:** Native list editing is more maintainable and accessible than a custom form-builder canvas.

```text
L-04 / UI-17
[Cancel]              Edit template
Name · Required [General condition check]
Saving creates a new revision.
1  Guard fixing · Status · Required    [More]
2  Belt condition · Status · Required  [More]
3  Displayed distance · Number · km    [More]
[Add item]                    [Preview]
Item More: Edit · Move up · Move down · Remove
[Save template]
Child UI-48:
Label · Required [Belt condition]
Type [Status ▾]   Required response [On]
Guidance · Optional / private [           ]
[Cancel] [Save item — staged in template]
```

<a id="UI-18"></a>

## UI-18 · Visit setup: Book, Start now, Record past

**Traceability and surface:** C:S18; F:S16; X-10. Layout L-04.

**Initial viewport:** Mode title/control, customer/site context, appointment or actual date, selected machine/service summary, primary Book visit/Start visit/Create historical draft. A known context stays visibly selected. Equipment is never inferred from a vague query.

**Anatomy:** Header → mode → customer/site → date/time/reminder → Selected work grouped by machine → Linked follow-ups → Private appointment note. Start and past modes show actual date, not appointment controls. Work selection is UI-55; no long nested sheet.

**Content and inputs:** Customer and site Required. At least one machine and service item Required. Each item is a selected eligible plan or named one-off with optional checklist. Book: future/today appointment date Required, Time Optional; enabling time defaults optional Duration to 60 minutes; positive duration, same-day boundary, approximate reminder Default/Off/2 hours/1 day where timed. Start: actual date Today, not future. Past: actual date Required, History only default. Private note Optional. Linked open follow-ups Optional, still explicitly reviewed at completion.

**Visible actions and menus:** Mode selector; choose customer/site; add/remove machine; select plans/Add one-off/Edit one-off → UI-55; linked follow-ups → UI-42; date/time/duration/reminder and reschedule reason; Book visit/Save booking; Start visit; Create historical draft; Cancel/Back. Changing selected site asks before clearing incompatible staged work, per C. F’s fixed booking site/past booking behavior is not active. Removal names affected staged work; actual working-visit removal uses UI-20 guards instead.

**Interaction, focus and persistence:** First focus on first missing context, not keyboard. Save booking creates appointment only; Start/Create historical draft creates durable Working snapshot. Returning from selector preserves date/note. Mode switch confirms loss of incompatible staged booking values but never converts a finalized record. A booking agreement in a contact note does not pre-complete this screen.

**Meaningful states:** Conflict row: “Already in Working V-001” with Open, not selectable. Paused/ended/historical equipment included only by valid source historical mode; explanation precedes selection. No eligible plans permits one-off. Missing active template assigned to a plan follows C frozen-content rule, not silent deletion.

**Resilience:** Long selected-work lists use compact summaries with View selected; actual names and dates remain inspectable. Keyboard never covers footer or selected context. At large text mode choices become one labelled selector with all modes listed.

**Rationale:** One reusable setup screen keeps booking and recording adjacent while clearly distinguishing their consequences.

```text
L-04 / UI-18 / T1 booking edit
[Cancel]              Edit booking
Mode: Book a visit
Customer: Harbor Fitness…
Site: Riverside Centre…                    [Change]
Appointment [5 Sep 2026]  Time [09:00]
Duration [60 minutes]  Reminder [Default ▾]
Reschedule reason · Required
[Customer requested Saturday access]
Selected work · 3 machines / 4 services    [Edit]
T-01: Condition inspection; Lubrication
T-02: Maintenance
B-01: Maintenance
[Linked follow-ups ▾] [Private note ▾]
Service due dates remain unchanged.
[Save booking]
Start mode: Actual service date replaces appointment block.
Past mode: Actual service date blank; History only stated.
```

<a id="UI-19"></a>

## UI-19 · Booked and cancelled visit detail

**Traceability and surface:** C:S19; F:S17. Layout L-03.

**Initial viewport:** Booked label, appointment date/time/time zone, V-001, site and selected work summary, Start visit primary. A past appointment has a separate “Appointment passed—review” label, not Overdue service.

**Anatomy:** Header → arrangement → Contact/Open map → selected machine/service list with independent due dates → linked follow-ups → private appointment note → history of rescheduling/cancellation. More holds Edit/reschedule, Change reminder, Cancel booking.

**Content and inputs:** Appointment date, optional time/duration, reminder state; actual service date is not populated until start. Machine/service rows include exact identifiers and due dates. Cancelled view names reason and shows no active claim; Create another visit from this is an explicit new setup.

**Visible actions and menus:** Edit/reschedule → UI-18 with required reason; Contact/Open map/Record contact → UI-45/29; machine/plan/task rows navigate context; Start visit reviews date and latest eligible captured content then UI-20; Cancel booking via UI-43; cancelled Create another visit from this opens new setup with blank appointment date; Change reminder opens relevant setup field. F state-dependent Resume/View finalized record route to UI-20/25 when the record actually changed.

**Interaction, focus and persistence:** Cancellation frees claims but does not erase overdue obligations. Start revalidates archived/changed plans/templates and active site. Back preserves caller selection. External handoffs never mark customer reached. Current clock changes do not silently start a visit.

**Meaningful states:** Past Booked, Cancelled, edited since selection, conflict/retired work at start, no map handler, denied notifications are meaningful variants. Cancelled work has no Start on the original record; use a new visit. Failed start/save remains Booked and shows retry.

**Resilience:** Appointment time zone is readable when device zone differs. Selected work scrolls; each row has independent date labels. Long notes private by default.

**Rationale:** The appointment is an arrangement. Keeping service due dates alongside it prevents rescheduling from looking like fulfillment.

<a id="UI-20"></a>

## UI-20 · Working visit overview and resume

**Traceability and surface:** C:S20; F:S18; C:S21 composition. Layout L-05.

**Initial viewport:** At T2: V-001 · Working, actual date 5 Sep, site context, Saved on this device 10:14, first machine T-01 with two direct service rows. Review completion footer is visible but does not imply readiness.

**Anatomy:** Fixed compact task/save header → full site identity on initial entry → machine groups. Every group has machine identity, service rows opening UI-22 directly, and “Machine observations, findings and photos” opening UI-21. This is a presentation consolidation of C:S20/S21, not deletion of the machine workspace. Below: linked follow-ups and private access/visit context. Review footer plus labelled Leave visit for later; no root bar.

**Content and inputs:** Actual service date; captured identification; machine/service count; per-service checklist state Not started/In progress/Reviewed and provisional outcome if already chosen in review. “2 of 4 required responses recorded” counts answers, not successful service. Follow-up proposals labelled “Will be created when this record is finalized.” Private access notes show current site access explicitly, separate from captured report identity.

**Visible actions and menus:** Machine card/title → UI-21; direct service row → UI-22. Add equipment/services → UI-55; remove machine/service via item More with recorded-content counts and source retention rules. Actual date → date editor; Review identification → UI-56; Contact/Open map → UI-45 using clearly current contact/address; linked tasks → UI-27/read-only staged UI-28; Review completion → UI-24 only after saving checkpoint; Leave for later returns caller; More: Review identification, Add work, Discard working visit. Discard requires reason and names removal of an ad-hoc draft versus cancellation of a booking-origin draft.

**Interaction, focus and persistence:** On resume restore last machine/service/scroll when that target still exists; Home Resume may open that work-item with a compact “V-001 · Resume point” header and Back to overview. Saving state survives navigation only after durable checkpoint. Pending/failed saves block finalization/external handoffs but do not turn navigation into repeated generic confirms; UI-58 provides actual choices. Remove a service does not delete machine-level evidence while another service remains.

**Meaningful states:** Working, historical Working, saved/interrupted, unsaved failure, removed/changed parent, no selected work after staged removal (add before finalizing), changed identity, missing media. Discard is not Undo for already finalized work. A saved draft may be left indefinitely; dates refresh on return, answers do not.

**Resilience:** For many machines use a labelled “Jump to machine” selector (same UI-42 navigation) and anchors, not a carousel relying on swipes. At 320dp header keeps EQ reference/service over redundant chrome. With 200% text footer stacks and content scroll padding remains correct.

**Rationale:** Direct service rows remove an unnecessary intermediate navigation step while retaining a dedicated machine evidence home.

```text
L-05 / UI-20 / T2
[Back]      V-001 · Working           [More]
5 Sep 2026 (actual)        [Review identification]
Saved on this device · 10:14                  P-02
Riverside Centre — East Building…
[Jump to machine]
T-01 · EQ-001 · Treadmill 01 — window side
  [Condition inspection · Reviewed / 1 issue]
  [Lubrication service · Work recorded]
  [Machine observations, findings and photos]
T-02 · EQ-002 · Treadmill 02 — door side
  [Maintenance · Work recorded]
  [Machine observations, findings and photos]
----------- reference fold -----------------
B-01 · EQ-003  [Maintenance · Not started]
[Add equipment/services] [Linked follow-ups]
[Private access notes ▾]
[Review completion]        no implied fulfillment
[Leave visit for later]
Save-failure variant: replace saved line with persistent
“Not saved — last saved 10:14” [Review saving problem].
```

<a id="UI-21"></a>

## UI-21 · Machine within a working visit

**Traceability and surface:** C:S21; F:S19 evidence ownership variant X-06. Layout L-05.

**Initial viewport:** Machine full name, T-01/EQ-001, captured serial, site/V-001, saved status, service rows and Observation control. Evidence sections follow; identification is not replaced by an undifferentiated photo grid.

**Anatomy:** Compact context/save header → service rows → Observation → Customer observations → Findings → Parts used → Photos → Existing corrective follow-ups → Private notes/history/master links. Observation and required finding errors remain visible in summary even when a later section is expanded.

**Content and inputs:** Observation: Not assessed default / No issue observed in recorded work / Issue observed. Customer observations Optional public multiline. Internal notes Optional private multiline. Findings machine-scoped. Parts are text/quantity records, not stock. Photos use explicit report inclusion Off. No F-style separate public visit summary or item-private-note field is added.

**Visible actions and menus:** Service row → UI-22; Observation radio group; edit customer/private notes autosave; Add/edit finding → UI-23; Add/edit part → UI-49; Add/view photos → UI-44; existing corrective follow-up → UI-27 with source context; Equipment history → UI-40; Current equipment → UI-10; Back to visit → UI-20. Add corrective follow-up is reached through the finding disposition or explicit task link, not inferred from a red answer. Evidence row menus include Remove according to child policy.

**Interaction, focus and persistence:** Changing an observation cannot erase an existing issue. A saved issue forces Issue observed; show linked finding instead of allowing contradictory “No issue” to be selected. Text edits autosave, child dialogs explicitly Save to the working draft. Leaving keeps durably saved values, not final outcomes. New photos are associated with this machine before camera launch.

**Meaningful states:** Not assessed is a legitimate value for unperformed work, not a hidden validation error. No findings/parts/photos shows labelled Add links. A missing retained attachment shows File missing rather than No photo. Historical/correction instances clearly read-only or “Correction draft”; parent record identity cannot be reassigned.

**Resilience:** One column, optional sections can collapse but their item count and unresolved issue label remain visible. Many photos show first four and View all, with all accessible in viewer. Long public notes expand normally.

**Rationale:** Machine-level evidence avoids duplicating the same belt photograph across inspection and lubrication while each service retains its own work/outcome.

```text
L-05 / UI-21 / T2
[Back to visit]             T-01 · EQ-001
Treadmill 01 — window side · SW-R8-0417
V-001 · Riverside Centre…    Saved 10:14
[Condition inspection · Reviewed, issue found]
[Lubrication service]
Observation
( ) Not assessed
( ) No issue observed in recorded work
(•) Issue observed  [Fraying on outer belt edge]
Customer observations · Customer report
[                                          ]
Findings · 1                          [Add]
Parts used · 0                        [Add]
Photos · 2                            [Add]
PH-01 Belt [Included]   PH-02 Nameplate [Excluded]
[Private notes ▾] [Equipment history]
```

<a id="UI-22"></a>

## UI-22 · Work performed and inspection

**Traceability and surface:** C:S22; F:S19; X-05/X-06. Layout L-05.

**Initial viewport:** Condition inspection title, T-01/EQ-001, V-001/site line, save status, due/interval/template snapshot excerpt, Work performed field and first question. Keyboard initially closed; first unanswered required question is a Jump link, not forced focus.

**Anatomy:** Pinned machine/service identity + save line → Work performed → checklist progress/unanswered link → question blocks → explicit review action. Question title/type/required label precede controls. Finding summary sits directly beneath its Issue answer. No sheet for an ordinary OK/number response.

**Content and inputs:** Work performed public multiline (required at final Performed/Partly performed). Status radio controls per P-05. Text field ≤500; finite signed numeric field with immutable unit. Required NA needs reason; required Not checked remains unfinished. P-001 uses its five fixture questions/answers. Optional guidance collapsed and labelled “Guidance — not in report.” Archived assigned template says “Saved template copy · archived in library,” not unavailable.

**Visible actions and menus:** Edit work; OK/Issue found/Not applicable/Not checked; enter text/number; Mark not applicable/Enter value instead; View/edit linked finding → UI-23; Mark checklist reviewed explicit; Choose/change checklist only for one-off before answers (after answers, remove/recreate work item per source); Keep finding/Remove draft finding when source answer changes; Back to previous working surface. Review completion remains reachable via workspace; no Complete service button here.

**Interaction, focus and persistence:** Choosing Issue found records the choice but shows incomplete finding until UI-23 Save. Changing disposition clears incompatible input only after explicit confirmation; preserve earlier saved data until committed. Mark reviewed validates required responses/NA reasons and reports exact missing fields without changing outcome. Editing any reviewed answer removes Reviewed status. IME Done cannot finalize/review automatically. Numeric keyboard must still permit locale decimal/negative input; fallback text keyboard if required symbols unavailable.

**Meaningful states:** No checklist: Work performed plus “No checklist attached” and no meaningless review button. Unanswered/NA/Issue incomplete/Reviewed/Changed since review; failed autosave; missing file linked in finding. Performed but unreviewed checklist can be recorded without fulfillment under C; honest partial finalization remains available via UI-24.

**Resilience:** At 360dp four status choices form two rows, all ≥48dp; long localization or 200% text turns them into a vertical radio group. Long question prompts wrap completely. Sticky context never crowds out all input when keyboard opens; collapse site excerpt before reducing machine/service identity.

**Rationale:** Reviewed means the recorded checklist was reviewed; it does not mean the machine passed or the service obligation was fulfilled.

```text
L-05 / UI-22 / T2
[Back]      Condition inspection        [More]
T-01 · EQ-001 / Riverside Centre…
Saved on this device · 10:14
Due 1 Sep · Every 3 months · Checklist revision 2
Work performed · Customer report
[Inspected recorded items; belt issue observed.]
Required responses: 4 of 4 recorded
1 Guard fixing · Required
[• OK] [Issue found] [Not applicable] [Not checked]
2 Belt condition · Required
[OK] [• Issue found] [Not applicable] [Not checked]
  Fraying on outer belt edge
  Corrective follow-up needed        [Edit finding]
----------- reference fold -----------------
3 Displayed distance · Required  [1240.5] km
  [Not applicable]
4 Optional accessory · Required → Not applicable
  Reason [Not fitted]
5 Additional observation · Optional [            ]
Reviewed · 5 Sep 10:14             saved T2 state
Before review: [Mark checklist reviewed] is explicit.
Editing an answer resets the review state.
```

<a id="UI-23"></a>

## UI-23 · Finding editor

**Traceability and surface:** C:S23; F:S19-D01; X-06. Layout L-04.

**Initial viewport:** Add/Edit finding, fixed machine/source question context; description and disposition are immediately visible; Save finding footer. Issue from checklist fixes Kind to Issue.

**Anatomy:** Context → Kind when changeable → public description → disposition choices and dependent fields → follow-up proposal/link → selected photo links → Save. This is a full editor because notes, task selection and photos need space.

**Content and inputs:** Kind Observation/Issue required; description required public. Disposition required: Resolved during visit / Corrective follow-up needed / No further action. Resolved requires resolution note; No further action requires reason. Follow-up needed requires new proposal or linked open corrective follow-up for this equipment, title/date/context validated. Photos optional references to this machine’s evidence; adding routes UI-44.

**Visible actions and menus:** Disposition controls; New corrective follow-up → UI-28 staged; Link existing corrective follow-up → UI-42 scoped; Select/add photographs; Save finding; Remove finding in More with named effects/answer relationship; Cancel/Back. The actual task is created only on finalization when staged. Existing live task is not removed by deleting a draft finding.

**Interaction, focus and persistence:** First focus description for a new issue. Save validates conditional fields and durably updates working/correction draft; it is not the final record. Kind Issue forces machine observation Issue observed. If cancelling new incomplete issue, return to checklist with incomplete finding warning and original Issue choice, not an invented successful answer. Removing asks what happens to the originating answer and any uncommitted proposal.

**Meaningful states:** Observation versus Issue; new/staged/linked live task; invalid missing description/disposition/date; historical read-only through record; correction draft editable with clear revision context. No auto “fixed” based on task creation.

**Resilience:** Conditional sections push downward rather than opening nested sheets; a task editor replaces this surface and returns with staged summary. Long finding text remains multiline and public-labelled.

**Rationale:** A mandatory disposition makes outstanding problems explicit without requiring every issue to become a new task.

<a id="UI-24"></a>

## UI-24 · Completion review: outcomes and consequences

**Traceability and surface:** C:S24; F:S20; X-05. Layout L-06.

**Initial viewport:** Review completion title, actual date/site/identification row; summary “4 services · outcomes to review”; first machine T-01 with its first expanded service. Finalize record footer persists with a readable blocker count when relevant.

**Anatomy:** Date/identity → grouped machine blocks → each service outcome/work/checklist/fulfillment/dates → machine observation/findings and required corrective proposal → existing task effects → report photo selection → issuer identity → draft preview → final named confirmation. All service outcome summaries remain visible when collapsed. Start with unresolved/missing sections expanded, not a preselected successful summary. A Jump to next item needing review control uses existing section focus, not automatic decisions.

**Content and inputs:** Each service Outcome initially Choose: Performed, Partly performed, Not performed. Work performed required for first two; Not performed requires reason. Checklist required-response and Reviewed state shown. “Fulfill this service obligation” initially unchecked; eligible only for Performed + reviewed checklist or no checklist + source date/claim guards. When checked, show Original due, Actual completion, Next due; calculated next date with explicit confirm/override reason. History only remains a named alternative. Machine observation accepts Not assessed. Existing FU-001 defaults Keep open; resolve is explicit UI-47. FU-002 staged title/date shown and editable. Report photo checkboxes default Off, selected fixture PH-01/03. Business/technician completeness required to finalize.

**Visible actions and menus:** Edit date/identification/machine/work links; per-line outcome radio; fulfillment checkbox/history-only; next date/Use calculated date → UI-57/46; corrective proposal → UI-28; linked-task Keep open/Resolve → UI-47; report photos → UI-44 selection; Draft report preview → UI-39 (no share); Business profile → UI-31; Finalize record → UI-25; Back to working visit. No Select all performed, no Complete everything, no hidden all-plans advancement.

**Interaction, focus and persistence:** Choices are part of durable working draft, but effects are staged until atomic finalization. Attempting Finalize focuses error summary and specific section; no empty disabled button with no explanation. Pending saves/attachments block it. During commit disable repeat presses; success opens exact finalized record. Failure leaves draft/schedules/tasks unchanged; retry of a completed commit opens that result. Next-date review uses captured interval, not an unannounced current edit.

**Meaningful states:** T3 prospective outcome: P-001 Performed + fulfill → 5 Dec; P-002 Partly performed → stays 1 Sep; P-003 Performed + fulfill → 5 Mar; P-004 Not performed → stays 1 Sep. P-005 absent. Backdated/paused/ended/conflicting claims show record-only reason and guarded path; a required unanswered checklist does not erase legitimate partial work. Missing report photograph and incomplete finding/task proposal are explicit blockers.

**Resilience:** No wide transaction table on phone. Each before/after pair is vertically labelled. At large font one service may fill viewport; machine ref remains pinned and item count aids orientation. Values are never reduced to tiny badges.

**Rationale:** The technician approves concrete outcomes and next dates, not a technical transaction. Independent service lines prevent a closed visit from falsely implying universal completion.

```text
L-06 / UI-24 / before T3 finalization
[Back]                  Review completion
Actual service date: 5 Sep 2026          [Edit]
Riverside Centre… · V-001                [Identify]
T-01 · EQ-001
Condition inspection
Outcome: [• Performed] [Partly performed] [Not performed]
Checklist: Reviewed · Issue found
[✓] Fulfill this service obligation      explicit check
Due before: 1 Sep 2026
Next due:   5 Dec 2026                   [Review date]
Finding: Fraying on outer belt edge
Follow-up: Replace worn belt — T-01
Due 12 Sep · Will be created on finalization     [Edit]
Lubrication service
Outcome: Partly performed
Cleaned accessible surfaces; lubricant unavailable
Remains due: 1 Sep 2026 · Will not advance
----------- scroll ----------------------------
T-02 / Maintenance: Performed; fulfill checked
1 Sep 2026 → 5 Mar 2027
B-01 / Maintenance: Not performed — Room unavailable
Remains due 1 Sep 2026
Existing FU-001: Keep open               [Review]
Report photos: 2 selected                [Review]
[Preview draft report]   Issuer: Ready
2 obligations advance · 2 remain due · 1 task to create
[Finalize record]                       not “Done”
```

<a id="UI-25"></a>

## UI-25 · Finalized record and report preparation

**Traceability and surface:** C:S25; F:S27. Layout L-03.

**Initial viewport:** Record V-001 · Finalized · Revision 1, actual service date 5 Sep and recorded time; confirmation “2 obligations advanced; 2 remain due”; PDF state and Generate customer PDF or View report. Finalized success remains visible if PDF fails.

**Anatomy:** Read-only record banner → report preparation/status block → machine/service result sections → recorded due-date effects → follow-ups at time/current links → retained photos → private notes separated → version/history tools. The default public summary is not a green completion seal.

**Content and inputs:** Full captured business/customer/site identity; actual versus recorded date; outcomes, checklist answers including incomplete/NA, work, findings, parts; original/next dates as recorded; report photo selection fixed per revision. Current task links say “Now: Open” separate from “At finalization: created.” Private internal data remains labelled and excluded from UI-39.

**Visible actions and menus:** Generate customer PDF/View report → UI-39; Retry PDF generation after failure, never a second Finalize; Record versions/Report versions selectors; Correct record/Resume correction → UI-26; Void this record → UI-26 void mode; plan/equipment/original customer-site/task links; Create return visit → UI-18 with still-open work offered but unperformed/outcome choices blank; Add contact/sharing note → UI-29; retained photograph → UI-44 read-only. More: Correct/Resume correction, Void, Create return visit, Record versions; Report versions also in report block.

**Interaction, focus and persistence:** No keyboard unless initiating a separate note/correction. Read-only history opens without implicit snapshot refresh. PDF generation reads this fixed revision and can be retried independently. A missing previously issued file uses new rendition identity/recovery behavior, not fake identical bytes. Back returns Work/history and refreshes valid counts.

**Meaningful states:** Current/superseded/voided revision; PDF Not generated/Generating/Failed/Ready/Missing; correction draft existing; missing retained photo; no fulfilled services still a valid record of partial/unperformed work. A void banner carries reason and not-valid-as-current-record message, never deletes the original.

**Resilience:** Long reports stay structured in expandable machine groups; unresolved/outstanding labels visible in collapsed summary. Large text shows state before report controls. One clear dominant report action changes with actual file state.

**Rationale:** Separating the saved record from the PDF prevents a report failure from inviting duplicate fulfillment.

```text
L-03 / UI-25 / T3 / PDF failure variant
[Back]               Service record            [More]
V-001 · Finalized · Revision 1 · Read-only
Service date 5 Sep 2026 · Recorded 10:40
2 obligations advanced · 2 remain due

Customer PDF: Not created
The service record is saved. PDF creation failed.
[Retry PDF creation]                         → UI-39
[Read customer report text]

T-01: Inspection performed; lubrication partial
T-02: Maintenance performed
B-01: Maintenance not performed
[Recorded next dates] [Follow-ups] [Photos]
[Private notes — excluded from report ▾]
More: Correct record · Void record · Create return visit
No Finalize button exists on this screen.
```

<a id="UI-26"></a>

## UI-26 · Correction and void workspace

**Traceability and surface:** C:S26; F:S28; X-10. Layout L-06.

**Initial viewport:** Correction draft / Void record title, exact original visit and revision, saved status, required reason, warning that the original remains. Affected schedule count appears before any Commit action.

**Anatomy:** Original record context → required reason → permitted corrected snapshots/outcomes via existing UI-21/22/23/24 editors in correction mode → Schedule effects → Follow-up effects → draft revised report → Commit footer. Void mode suppresses irrelevant edit fields and shows effect review plus reason. No replacement of the original equipment/customer relationship.

**Content and inputs:** Reason Required, shown appropriately on corrected/void report. Captured identification fields may be corrected explicitly; wrong subject uses Void then create correct record, not reparent. Actual date ≤today; corrected answers/work/parts/photos use same validation. Schedule reconciliation each affected plan via UI-57. Follow-up default Keep; Cancel as erroneous needs reason; add needed task explicitly. Original report versions retained.

**Visible actions and menus:** Edit permitted captured content; Review identification/current data comparison → UI-56; Reconcile affected plan → UI-57; Review task effects → UI-47/28; Preview proposed report → UI-39 draft; Commit correction/Confirm void; Leave correction for later; Discard correction draft with confirmation. Current plan/task links enable separate adjustments without being mistaken for automatically staged correction effects.

**Interaction, focus and persistence:** Durable draft autosaves, leaves unfinished correction visible Home/History (FC-01). Commit rechecks source revision, plan ownership/current dates/later fulfillments/claims. Text-only correction does not change schedule. Later fulfillment preserves today’s schedule; later manual due date is retained by default with reasoned choice; blocked schedule effects provide links rather than forced rollback. A failed commit retains prior valid revision and draft.

**Meaningful states:** Latest revision versus superseded original; schedule unaffected/eligible/blocked; void latest vs older with later service; correction crosses neighboring counted dates becomes history-only plus explicit reconciliation. Wrong subject cannot be selected in a new picker. Unsaved failure uses UI-58. Only one related correction draft resumes instead of duplicating.

**Resilience:** Before/after values use vertical cards with dates fully named and Current/Proposed labels. Large draft content navigates by machine; per-plan effects stay in a required summary, never buried solely in report preview.

**Rationale:** Visible revisions preserve honest history without claiming a compliance-grade audit trail.

```text
L-06 / UI-26 / T4 what-if
[Back]                Correction draft
V-001 · correcting revision 1 · Saved on device
Reason · Required
[Actual work was on 4 Sep; date entered incorrectly.]
Actual date: 5 Sep → 4 Sep 2026             [Edit]
Schedule effects · 2 plans to review
P-001 Condition inspection                  [Review]
Current next due: 5 Dec 2026
Proposed next due: 4 Dec 2026
P-003 Maintenance                           [Review]
Current next due: 5 Mar 2027
Proposed next due: 4 Mar 2027
P-002/P-004 remain 1 Sep. P-005 unchanged.
Follow-up effects: Keep existing tasks      [Review]
Original revision and report remain available.
[Preview revised report]
[Commit correction] [Leave for later]
Later-fulfillment variant: “Current schedule will stay
unchanged because a later service owns this date.”
```

<a id="UI-27"></a>

## UI-27 · Follow-up detail

**Traceability and surface:** C:S27; F:S21. Layout L-03.

**Initial viewport:** Corrective or Contact type, title/ref, Open/Resolved/Cancelled, due date and exact customer/site/equipment context; main action appropriate to type. FU-002 shows source finding separately from task state.

**Anatomy:** Identity/type → due/current state → planning note (private) → source finding/record (public historical excerpt) → linked visit → contextual actions/history. Resolved/cancelled outcome is a dated section, not a changed source finding.

**Content and inputs:** FU-002 “Replace worn belt — T-01”, due 12 Sep; source “Fraying on outer belt edge · V-001 rev 1.” FU-001 Contact may have only customer/site context; a visit still requires a selected site/equipment. Private planning notes never become report work-performed text.

**Visible actions and menus:** Edit/reschedule → UI-28 with required date-change reason; Contact/Open map/Record contact → UI-45/29; Book/Start related visit → UI-18 with context reviewed; Resolve/Cancel → UI-47; Reopen → UI-47 with date/reason; source finding/record/linked visit/context links. Back to list position. No Delete saved follow-up.

**Interaction, focus and persistence:** Booking a corrective visit does not resolve the task; finalization only resolves when explicitly selected. Standalone resolution requires an outcome/date and does not fulfill an unrelated recurring plan. Cancelling preserves task history and its origin. Reopen requires eligible active context.

**Meaningful states:** Open overdue/future, Resolved, Cancelled, linked Working/Booked, archived/inactive context, stale source/missing record. Blocked new work explains context restoration requirements; historical source remains readable.

**Resilience:** Long titles wrap, type label remains visible independent of color. One primary action: Contact for contact task, Book related visit for corrective task when unarranged; linked working visit changes it to Resume.

**Rationale:** Task resolution and equipment/service condition must not collapse into a single checkmark.

<a id="UI-28"></a>

## UI-28 · Follow-up editor: saved or staged

**Traceability and surface:** C:S28; F:S22. Layout L-04.

**Initial viewport:** Add/Edit follow-up or “Planned follow-up — V-001”; type, title, due date, context. Primary label Save follow-up for standalone / Use follow-up for staged.

**Anatomy:** Type/context → title → due/date-change reason → private planning note → staged/saved consequence explanation → footer. Source-linked finding context is fixed and stated.

**Content and inputs:** Type Contact/Corrective required. Title and follow-up date Required (past allowed with due warning). Customer Required; site/equipment required for Corrective, optional for Contact subject to actual parent linkage. Private note Optional. Reschedule reason Required when changing an existing due date. Staged example title “Replace worn belt — T-01”, due 12 Sep.

**Visible actions and menus:** Type/context selectors; date picker; Save follow-up/Use follow-up; Cancel/Back. Existing-task linking and removal of an uncommitted proposal live on the parent finding/review, not an unsupported Delete for live tasks. UI-42 same-machine selector can choose an existing open corrective task; choice replaces proposal only after clear acknowledgement.

**Interaction, focus and persistence:** Initial focus title unless missing context first. Standalone Save writes an Open task; staged Use records the proposal only in working/correction parent. Cancelling child preserves earlier proposal/live record. A source-linked context cannot be changed to a different machine. Returning to parent shows explicit staged label.

**Meaningful states:** New standalone, editable open standalone unlinked, source-fixed, staged, reschedule, invalid missing date/context, duplicates possible requiring deliberate separate/link choice. Closed tasks are viewed in UI-27; reopen is not accomplished by changing editor fields.

**Resilience:** All fields single column; customer/site path wraps. At large font fixed context may collapse to ref+Identification link but full source visible in content.

**Rationale:** Different action wording teaches whether a real task exists yet without exposing implementation transactions.

<a id="UI-29"></a>

## UI-29 · Manual contact and report-handoff note

**Traceability and surface:** C:S29; F:S15; X-08. Layout L-04.

**Initial viewport:** Record contact or Record report handoff; fixed origin context, channel, occurred date/time and outcome. Returned-from-composer hint says no delivery has been inferred.

**Anatomy:** Context → Channel → Occurred → Outcome → Details → optional new contact follow-up → Save. Existing note view is read-only with Edit and eligible Delete erroneous unlinked note in More; report handoff context includes exact report version.

**Content and inputs:** Customer Required; optional constrained site/equipment/plan/visit/report reference. Channel Call/Text message/Email/In person/Other. Occurred date/time Required, not future. Outcome Attempted—no response / Spoke or exchanged messages / Follow-up requested / Appointment agreed / Other. Details Optional except Other requires them. Private note label on details. Create contact follow-up Off initially; On opens/stages UI-28. For manual report handoff choose an honest outcome and details, e.g. “Printed copy handed to site contact”; no automatic Received.

**Visible actions and menus:** Save contact note; Add/edit follow-up proposal; Book visit after note saved opens separate UI-18; Edit note → explicit save and “Edited on”; Delete erroneous unlinked note only eligible, with confirmation; context links in read view; Cancel/Back. F’s mark-entered-in-error/retained-old-value policy and atomic originating-task closure are unadopted X-08 variants; resolve a current task separately via UI-27/47.

**Interaction, focus and persistence:** No automatic saved entry merely on returning from an external app. Save atomically writes note and new proposed contact task if chosen, not booking or resolution of an existing task. Initial focus is first unfilled choice rather than unsolicited multiline keyboard. Back with edited input UI-41.

**Meaningful states:** New, edited, read-only historical, report-version-specific, unsupplied contact method, future time error, orphaned context read-safe. Unlinked-note delete does not delete a task/visit/report it cannot legitimately own.

**Resilience:** Outcome options vertical radios at large text; long details remain readable. No timeline that purports to ingest phone/SMS history.

**Rationale:** The technician records what actually happened, distinct from the app opening a dialer or share sheet.

<a id="UI-30"></a>

## UI-30 · Settings index

**Traceability and surface:** C:S30; F:S30. Layout L-10.

**Initial viewport:** Settings title and all five sections normally fit: Business and report identity, Reminders, Inspection templates, Data and recovery, Privacy/help/app information.

**Anatomy:** Plain grouped navigation rows with descriptive current status; no settings dashboard, user avatar/account or unrelated theme controls. Back returns originating root.

**Content and inputs:** Identity Ready/Incomplete; Reminders Off/On/Blocked by Android; templates active count; last verified backup timestamp or Never; help summary. A status is a navigation-row subtitle, not a toggle with unclear persistence.

**Visible actions and menus:** Each row opens UI-31/32/15/33/38 respectively. Back. No billing, Sync, teams, analytics, camera-permission master switch, language picker or coming-soon sections.

**Interaction, focus and persistence:** No keyboard. Data/error status refreshes on return. Selecting a status row performs no mutation. Unsaved child settings use that screen’s own explicit-save contract.

**Meaningful states:** Incomplete identity allowed until finalization; blocked notifications do not imply app blocked; backup status distinguishes Never/Written not verified/Verified snapshot. Failed status read uses unavailable text, not zero.

**Resilience:** Rows grow, icons decorative with textual labels; all five remain reachable by scroll at large font.

**Rationale:** Infrequent capabilities stay discoverable in a small, truthful settings hierarchy.

<a id="UI-31"></a>

## UI-31 · Business and report identity

**Traceability and surface:** C:S31; F:S31. Layout L-04.

**Initial viewport:** Business and report identity title, public-name fields, optional logo thumbnail, Save profile footer; a finalization-origin banner states which record still needs identity review.

**Anatomy:** Required names → optional public contact/address/logo → business time zone → change consequences. No invoicing/company-registration fields. Names can be the same for a sole trader.

**Content and inputs:** Business/display name and Technician name required for finalization; Phone, Email, Postal address, Logo Optional public. Business time zone Required initialized from device at dataset creation; searchable named-zone selector, not GPS. UI fixture: Mira Field Service / Alex Marin; Europe/Bucharest.

**Visible actions and menus:** Add/change/remove logo → UI-44 single-image; time-zone picker; Save profile; Cancel/Back. After saving from a working/correction record, show “Profile saved. Review identification to use these details in this record” linking UI-56; never silently refresh the snapshot.

**Interaction, focus and persistence:** New incomplete profile can be left to perform earlier work; Save validates entered names when saving as ready for reports. Zone change stages effects: new appointment defaults/business Today/summary timing change; existing timed appointment instants and original zones retained. Preview before Save; returned picker cancel preserves zone. Logo replacement affects only future/refreshed snapshots.

**Meaningful states:** Incomplete/ready, called from finalization, changed zone different from device, invalid email, missing logo allowed, photo/save failure. A profile save is not a report correction.

**Resilience:** Address full width; logo preview fit with remove label, not a miniature unlabeled X. Named zones wrap and provide search; no long unscrollable dropdown.

**Rationale:** Identity belongs to reports without making business registration or branding a first-use obstacle.

<a id="UI-32"></a>

## UI-32 · Reminder settings

**Traceability and surface:** C:S32; F:S32; X-09. Layout L-04.

**Initial viewport:** Reminders title, actual permission/channel state and note “Reminders may be delayed. Work lists remain the source of truth”; Request local reminders preference; Save settings footer. No automatic permission dialog on opening.

**Anatomy:** Status → master preference → Daily summary/time/days/content → shared due-soon horizon → approximate appointment alerts/default lead → channel/test tools → backup reminder link. Parent-off rows remain readable with explanation; no fabricated scheduling guarantee.

**Content and inputs:** Request local reminders Off initially. Daily summary preference On; around 08:00 business zone; Mon–Sun all selected, at least one if enabled. Content Due services/Visits/Follow-ups/Unfinished visits On; Include backup reminder when due On. Due-soon horizon 0/7/14/30 days default14 affects Home/default Due filter, not dates. Appointment alerts Off; default lead 2 hours or1 day, default2 hours. Generic notification text only; no Show customer names preference.

**Visible actions and menus:** Master preference triggers UI-50 only when explicitly enabled; summary fields/day toggles/content; horizon selector; appointment preference/lead; Save reminder settings; Android settings for app or Work summaries/Appointment reminders channels; Send test notification when allowed; Backup reminder settings → UI-33 after save/discard guard; Cancel/Back. Help text links UI-38. OS notification interactions are UI-59.

**Interaction, focus and persistence:** All app preferences staged until Save; permission granted/denied in Android is external and cannot be undone by discarding this form. Show affected count before changing default lead for future default-using bookings; explicit per-visit overrides stay. Saved Off cancels app reminder requests/visible reminders, not obligations. Test says “Test requested,” never “Test heard.”

**Meaningful states:** Off, desired On + permission denied/channel blocked, summary-disabled, zero selected days invalid, no future default bookings, external settings changed on return. Summary empty means no notification. Actual channel sound/vibration controlled in Android, not duplicated sliders.

**Resilience:** Day controls have full spoken weekday names and ≥48dp targets, wrap to multiple rows; not seven tiny single letters. Large text turns switch rows into stacked label/explanation/control. Save visible above IME for time entry.

**Rationale:** Explicit Save is consistent with other compound forms and prevents an accidental half-edited reminder configuration.

```text
L-04 / UI-32 / denied permission
[Cancel]                  Reminders
Android notifications: Not allowed
Work lists still show every due service.
Request local reminders [On]          staged preference
[Open Android settings]
Daily summary [On]
Around [08:00] · Europe/Bucharest
Days [Mon] [Tue] [Wed] [Thu] [Fri] [Sat] [Sun]
Content [Due] [Visits] [Follow-ups] [Unfinished]
Include backup reminder when due [On]
Due-soon horizon [14 days ▾]
Approximate appointment alerts [Off]
Default lead [2 hours ▾]
[Backup reminder settings] [Test — unavailable]
[Save reminder settings]
Granting Android permission is not reverted by Cancel.
```

<a id="UI-33"></a>

## UI-33 · Data and recovery hub

**Traceability and surface:** C:S33; F:S33; X-01/02/03/09. Layout L-10.

**Initial viewport:** Dataset identity, last verified full snapshot date and changed-since status, Create full backup primary; Inspect backup and Restore backup visible as separate choices. Storage/count details expand below.

**Anatomy:** Recovery status → Create/Inspect/Restore → Export/Import → Backup reminder preference → dataset/storage details → Erase under clearly separated danger section → bundled recovery guidance. Actual integrity failures precede routine backup age.

**Content and inputs:** Dataset reference; customer/site/equipment/plan/visit/task counts with units; app-managed media/PDF size; available space if known, otherwise “Space unavailable.” Last attempt versus Last verified full backup (snapshot time, provider/destination, bytes). Copy “Destination name does not confirm upload or future availability.” Backup reminder Off/1/7/30 days default7; saved immediately with inline Saved. With no backup, age starts at first saved business record; only relevant changes trigger stale status.

**Visible actions and menus:** Create full backup → UI-34; Inspect/verify backup → UI-35 inspect-only; Restore backup → UI-35 restore; Export readable CSV → UI-36; Import directory → UI-37 create-only; backup age immediate choice; Erase this device’s data → UI-52; recovery help → UI-38; integrity warning → UI-58 affected-file list and actual records. No Snooze backup, auto cloud switch, purge history, gallery cleanup, or broad-storage permission.

**Interaction, focus and persistence:** Opening file tasks mutates no business record. Returning from verified backup updates the snapshot metadata based on actual verification, not merely Done. Failed immediate reminder preference keeps old value and shows Retry inline. Back returns Settings/Home origin.

**Meaningful states:** Never backed up, changed since verified, written but unverified, incomplete copy not full, destination unavailable now, missing files, low storage. “Verified” does not promise off-device safety. Archived/historical records included in counts rather than hidden.

**Resilience:** Counts use wrapping two-column definition rows, not tiny KPI cards. At large text everything stacks; destructive erase remains separated and labelled.

**Rationale:** Recovery choices must be findable during stress without equating CSV export, local saving and a full backup.

<a id="UI-34"></a>

## UI-34 · Create and verify backup

**Traceability and surface:** C:S34; F:S34; X-01/X-02. Layout L-07.

**Initial viewport:** Create full backup title; Check data action; included counts/source health; passphrase explanation begins once source can be inspected. The next action is named by stage, not a generic Next.

**Anatomy:** Check source → included snapshot inventory/space → Full or deliberately Incomplete type → passphrase/confirmation/acknowledgement → Create backup → OS destination → writing/verification → terminal result. Preserve one stage per screen view with a visible compact stage list; Back does not imply data replacement.

**Content and inputs:** At least12-character passphrase including spaces, confirmation, Show/Hide, explicit “I understand a forgotten passphrase cannot be reset.” No secret recovery buffer/diagnostic/clipboard action. Source inventory includes all saved business records, draft/revision history, app-held images/logos and existing PDF versions. Uncreated PDFs are recorded as pending, not missing files. Missing actual source media blocks Full; optional Incomplete branch lists every missing reference and requires acknowledgement. No incomplete branch for unexplained corruption.

**Visible actions and menus:** Check data; Create incomplete recovery copy only eligible; Show/Hide passphrase; Create backup/Create recovery copy; Verify saved file; Retry/Choose another destination; Done. UI-51 progress offers Cancel before commit/write checkpoints. Destination selection is OS Create document, not a ServiceLoop cloud picker. Source records remain authoritative and unchanged.

**Interaction, focus and persistence:** Source check checkpoints saved drafts; durable ordinary-editor recovery buffers are included as buffers, not as committed record edits. Flush eligible working/correction drafts before snapshot; unpersisted keystrokes are not guaranteed and a failed checkpoint is explicitly reported. Freeze snapshot, protect, write/close, reopen/verify destination. Written result is “File written—verification needed” if reopening cannot succeed. Successful verification immediately records last verified full snapshot; Done only exits. Secret re-entry may be necessary after interruption. Cancelling may leave a partial external file when provider cleanup fails; explain it.

**Meaningful states:** Healthy full, missing-media incomplete, insufficient staging space, provider cancellation/unavailable, mismatched passphrase, write failed, file written unverified, verified full, verified incomplete (does not update full status). All successful results name snapshot time, file/destination, coverage and next step to keep/test a copy elsewhere. No “Cloud synced.”

**Resilience:** Password fields support paste from user-managed password tools but app provides no auto-copy-secret action. Long destination URI wraps in details; progress uses actual items/bytes when known. Long missing-file lists scroll with affected-record links. Result page persists until dismissed.

**Rationale:** A nontechnical user should leave knowing exactly what was verified and what remains their responsibility.

```text
L-07 / UI-34 / T3
[Back]                Create full backup
1 Check data  →  2 Protect  →  3 Write  →  4 Verify
Check complete: 1 customer · 1 site · 3 machines
5 plans · 2 visits · 2 open tasks · 4 images
PDF versions: [actual count at snapshot]
Snapshot will include saved drafts and report versions.
Passphrase · Required [••••••••••••] [Show]
Confirm passphrase   [••••••••••••]
[ ] I understand there is no passphrase reset.
[Create backup] → OS destination chooser

RESULT — VERIFIED FULL BACKUP
Snapshot: 5 Sep 2026, 10:50
File: ServiceLoop_2026-09-05_1050.slbackup
Destination: [chosen provider and location]
Written file reopened and checked against the snapshot.
Off-device availability: Not verified by ServiceLoop.
[Done]
INCOMPLETE VARIANT: amber heading “Incomplete recovery copy”;
“PH-01 missing” list + explicit acknowledgement; never Full.
```

<a id="UI-35"></a>

## UI-35 · Inspect, restore and replacement result

**Traceability and surface:** C:S35; F:S35; X-01/X-02. Layout L-07 / L-06.

**Initial viewport:** Inspect backup or Restore backup mode, Choose backup, “Current data will not change during inspection.” After unlock show file dataset/snapshot/type/inventory before offering replacement.

**Anatomy:** File choice → passphrase unlock/inspection → contents/health result → inspect-only exit OR replacement comparison → backup current data → explicit replacement acknowledgement/type REPLACE → short protected switch → restored result. Restore is a full task, not a confirm dialog stacked on file picker.

**Content and inputs:** Backup file and passphrase; read-only inspection results for format/identity/integrity/source timestamp/missing declared files. Replacement preview shows Current dataset versus Backup dataset counts/dates, working/correction/ordinary drafts replaced, latest safety backup, and no merge. For incomplete copy, list known gaps and require acknowledgement. Type REPLACE plus explicit replacement acknowledgement; no field autocompletes the phrase.

**Visible actions and menus:** Choose backup; Unlock and inspect; Inspect only/View contents; Back up current data first → UI-34 then return with comparison revalidated; Continue to replacement; Replace local dataset; Open restored data; Return to previous dataset only offered for failed/interrupted pre-acceptance outcome per source, not a permanent Undo; Cancel/Back before switch. Reminder settings link on result → UI-32, currently Off. UI-51 Cancel disabled only during actual final switch.

**Interaction, focus and persistence:** Validate/stage before any live replacement, recheck at confirmation. Wrong password/corrupt/unsupported/unexpected missing media leaves current data intact. Failure during staging preserves old dataset. In a recovery state explain which dataset is active, never offer Start empty as a fix. Successful restore clears stale external callbacks/session selections, opens Home with restored identity and reminders Off; warn that another device is not synchronized. Inspection-only never exposes destructive button until Restore is deliberately selected.

**Meaningful states:** Full valid, declared-incomplete valid-with-warning, invalid, incompatible newer format, insufficient space, cancelled, replacement in progress, restored, interrupted switch/previous recoverable. New user may have Current dataset empty; still show replacement semantics. Missing files after incomplete restore remain persistent in UI-33/58.

**Resilience:** Full-width before/after panels, large text warning and phrase field visible via scroll; keyboard focus stays clear of Replace footer. TalkBack announces inspection result once, then reads the comparison before confirmation. No countdown pressure.

**Rationale:** The successful recovery deserves a clear result, not just a destructive warning followed by an unexplained empty Home.

```text
L-06 / UI-35 replacement review
[Back]                  Replace local data
Current: Dataset SL-A · latest saved 5 Sep 10:40
1 customer · 3 machines · 2 visits · 2 tasks
Backup:  Dataset SL-A · snapshot 1 Sep 08:00
1 customer · 1 site · 3 machines · 5 plans
0 visits · 0 tasks · 1 identity image · 0 PDFs
Changes after that snapshot will not be retained.
This replaces records, attachments and saved drafts.
It does not merge two devices.
[Back up current data first]
[ ] I reviewed what will be replaced.
Type REPLACE [                    ]
[Replace local dataset]                 deliberate

SUCCESS
Restored: [dataset] · snapshot [date/time]
[actual counts] · Reminders are off on this device.
Use one working device. Other devices are not synchronized.
[Open restored data]  [Review reminder settings]
If incomplete: persistent “Some attachments were absent
from this recovery copy” [View missing files].
```

<a id="UI-36"></a>

## UI-36 · Readable CSV export

**Traceability and surface:** C:S36; F:S36; X-03. Layout L-07.

**Initial viewport:** Export readable CSV, “Not a complete backup”; export-type choice and scope; Create export footer. Private inclusion default Off visibly stated, not hidden in More.

**Anatomy:** Purpose/type → scope → relevant options → Contents/field guide → Create → OS destination/progress → result with Save another copy/Share. One clear operation; not a general report builder.

**Content and inputs:** Type Directory (fixed import-compatible customer/site/equipment CSV) or Records ZIP (source-defined14 CSVs/manifests). Scope All or One customer. Include inactive On. Include private/access data Off; turning On shows explicit confidentiality warning/acknowledgement. Include previous revisions Off for records export only. No photo/PDF bytes in CSV; manifests reference them, so this is not recovery.

**Visible actions and menus:** Export type/scope/options; Create export; Save another copy; Share export; Done/Back. Contents/field guide expansion explains columns, formula-like value handling and the fixed directory import constraints. No “restore from CSV” button. Sharing uses UI-59, manual result only.

**Interaction, focus and persistence:** Before creating checkpoint/validate exact saved snapshot. Cancelling picker leaves live records unchanged; file failure Retry or alternate destination. Export success describes what was included/excluded and warns that private data, if included, can be read by recipients. A successful share handoff is not a receipt. Directory reimport is create-only, not bulk editing.

**Meaningful states:** Empty chosen scope, optional inactive/private/revisions, provider unavailable, partial write, generated but not externally saved, saved copy. Exporting a customer scope preserves historical ownership as source specifies rather than leaking previous owner private notes via current equipment.

**Resilience:** Options are full-width labelled choices; explain type-specific disabled options in text. Wide CSV schema appears in scrollable documentation, never an in-app spreadsheet editor.

**Rationale:** Privacy choices and an explicit non-backup warning prevent two common misunderstandings of export.

<a id="UI-37"></a>

## UI-37 · Fixed directory CSV import

**Traceability and surface:** C:S37; F:S37; X-03. Layout L-07.

**Initial viewport:** Import directory, “Adds new customers, sites and equipment; does not update existing records”; Get template/example and Choose CSV. No keyboard or editable spreadsheet grid.

**Anatomy:** Rules/template → file selection → validation summary → tabs Errors/Warnings/New/Existing unchanged → row/branch details and duplicate decisions → Import reviewed records → result with directory routes. Step state preserved when inspecting a candidate, not when a changed file invalidates validation.

**Content and inputs:** C fixed22 headers and exact rules are reproduced in J2. UTF-8 comma CSV, ≤20MiB/10,000 rows source proposal; refs1–64 permitted letters/digits/hyphen/underscore; one equipment per row where equipment supplied; customer-only allowed under default-site rules. Existing exact reference reusable only when parent and normalized supplied fields match. Same key with different values is a hard conflict. Similar different refs are warnings requiring separate/skip decision. No plans/history/photos imported.

**Visible actions and menus:** Save blank template/Save worked example (fictional and labelled); Read import rules; Choose CSV; Preview row/filter tabs; Review duplicate → UI-53; Save validation report; Choose corrected file; Import reviewed records; View imported customers / Equipment without active plans; Cancel/Back. Skip row/coherent branch exists in UI-53 and triggers revalidation; remaining errors block commit. F Save import-result file is not added; C provides validation report and on-screen result.

**Interaction, focus and persistence:** Row view is read-only: row number, parent refs, supplied fields, issue and corrective instruction. User edits CSV externally and selects corrected file. Validation never mutates directory. Commit all reviewed, non-skipped valid records atomically, no silent partial success. Repeated identical import may create 0 and reuse unchanged records; say so. Changed file clears old approvals. Success shows new/existing/skipped totals and “No service dates created—add service plans.”

**Meaningful states:** Wrong encoding/headers, oversized file, inconsistent parent fields, duplicate warnings, existing-key hard conflicts, intentional skipped branch, no new records, cancelled, failed commit, success. Current dataset stays intact on failure. F empty-only policy would make this entry unavailable once business data exists; not the selected C path.

**Resilience:** Large validation loads use counts and filters; row details stack field/value, not 22 columns on phone. Errors identify row/reference in text, not only red shading. No performance or maximum business-directory size inferred from import batch cap.

**Rationale:** A small repeatable create-only importer is useful only if its no-overwrite rule is unmistakable.

```text
L-07 / UI-37 review
[Back]                   Review CSV import
Directory_2026-09-05.csv
3 new machines · 1 existing unchanged customer
1 existing unchanged site · 1 warning · 0 errors
[Errors 0] [Warnings 1] [New] [Existing unchanged]
Row 4 · equipment_ref EQ-002X
Treadmill 02 — door side
Similar to EQ-002 / T-02 at Riverside Centre
[Review possible duplicate]                 → UI-53
No record will be overwritten.
[Save validation report] [Choose corrected file]
[Import reviewed records]  unavailable until warnings reviewed
RESULT: Added [actual N]; unchanged [N]; skipped [N].
[View imported customers] [Equipment without active plans]
```

<a id="UI-38"></a>

## UI-38 · Bundled help, privacy and app information

**Traceability and surface:** C:S38; F:S38. Layout L-10.

**Initial viewport:** Help and privacy title, topic search/filter and first topics Getting started; Due dates/partial work/corrections; Local storage/recovery. All essential help remains offline.

**Anatomy:** Topic index → readable topic page with source-supported task links → app/publisher/version/notices section. In a contextual entry, the relevant topic opens and Back returns to the task without clearing input.

**Content and inputs:** Topics: Getting started; Due dates, partial work, corrections; Local storage/recovery including passphrase loss; Permissions/external apps; Privacy policy; What reports contain; Accessibility/display; App information/third-party notices. Diagnostic summary limited to actual version, OS, permission/channel state, operation error code, coarse storage. Never customer identifiers, notes, photos, passphrase or advertising ID.

**Visible actions and menus:** Topic rows/search; contextual links to actual screens; bundled Privacy policy and Open published policy (release URL must be supplied, not invented); Copy support details; notices links; Back. F automatic support email composer is not adopted; copying details for user-controlled sending remains. No chatbot, contact-center account, analytics opt-in or research service.

**Interaction, focus and persistence:** Reading help never requests permission. Browser handoff failure retains bundled policy. Copy details shows “Copied support details—review before sharing,” no silent upload. Task link respects dirty-form checkpoint/return context. App version/publisher are actual release data, not fabricated example values.

**Meaningful states:** No network, unavailable published policy at design stage (release blocker, not a fake production button), no diagnostic error code, current policy version. Design deliverable describes exact location/content requirement; publisher/legal text remains owner-provided before release.

**Resilience:** Readable body text; logical headings; link labels name destinations; long notices scroll. No mandatory HTML tiny-font panel inside app.

**Rationale:** Infrequent recovery knowledge should be available where the problem occurs, even without connectivity.

<a id="UI-39"></a>

## UI-39 · Report preview, structured text, versions and handoff

**Traceability and surface:** C:S39; F:S29. Layout L-08.

**Initial viewport:** V-001 / record revision / report version, Draft/Current/Superseded/Voided/File missing label, actual date and generated timestamp; PDF/Text toggle; first page/content heading. Final-ready Share PDF primary, Save copy secondary; draft no external controls.

**Anatomy:** Fixed context/state header → PDF/Text mode → page or structured content region → accessible page/zoom controls → final file actions. PDF page scales only for preview; native app controls retain size. Version list is a full readable selector with revision/date/unique ID/state. Missing/failure state occupies content region, not a false empty page.

**Content and inputs:** Exact stored bytes for a generated version. Structured text from same public snapshot in report order; no internal fields. Previous/Next/Go to page, page n of total; Zoom in/out/Fit page with buttons as alternatives to pinch; version selector. Readonly selected image captions. Draft watermark/label outside and inside document; no Share/Save/external viewer/handoff in draft.

**Visible actions and menus:** Page navigation and zoom; PDF/Text; Report version; Save PDF copy → OS document; Share PDF → OS Sharesheet; Open in another app; Record handoff note → UI-29 with exact version; Retry generation/Re-create missing PDF; Back/Return to editing. Old superseded/voided original sharing requires explicit warning “This is not the current valid service record,” file/version identity and Share this historical copy confirmation. It is never presented as a valid current report.

**Interaction, focus and persistence:** Generation from finalized snapshot is independent of completion. Original missing bytes cannot be silently recreated as the same version: new rendition gets new ID/date labelled Re-created copy. Missing selected image blocks faithful generation; Locate original/restore/correction offered. Return from share leaves “Sharing opened—delivery not confirmed” and manual note link. In-app Text remains usable if renderer/external handler fails. Focus returns to launching control after system surface.

**Meaningful states:** Draft, current ready, generating, failed, original missing, re-created rendition, superseded, voided, no optional logo/images, many pages. Selecting old version never removes historical status; current data edits do not alter its bytes or snapshot. C permits guarded sharing of historical copies; owner X-10 review remains.

**Resilience:** At large text Text mode reflows naturally; PDF stays a document viewport with zoom/page buttons. Grayscale report uses text/line patterns, not color-only outcomes. Long report no shrinking to one page; G defines pagination.

**Rationale:** The customer report is a public record artifact; the preview must make both its content and its historical status unmistakable.

```text
L-08 / UI-39 / T3
[Back]                  Customer report
V-001 · Record rev 1 · PDF v1 · Current
Service: 5 Sep 2026   Generated: 5 Sep 10:42
[PDF] [Text]                         [Versions]
┌ Report page, exact stored PDF bytes ┐
│ Mira Field Service                 │
│ Service record · V-001 · rev 1      │
│ Harbor Fitness… / Riverside Centre │
│ 2 obligations fulfilled; 2 remain  │
└────────────────────────────────────┘
[Previous]  Page 1 of [actual total]  [Next]
[Go to page] [Zoom −] [Fit page] [Zoom +]
[Share PDF] [Save copy] [More]
More: Open in another app · Record handoff note
Draft: replace footer with [Return to review]; no handoff.
Missing: “Original PDF missing” [Re-create copy] [Text view].
```

<a id="UI-40"></a>

## UI-40 · Scoped/global history and records needing attention

**Traceability and surface:** C:S40; F:S26; FC-01. Layout L-02.

**Initial viewport:** History title, exact origin scope or All records, type/date/search/sort controls, first dated entries. Records-needing-attention entry shows visible Failed/missing PDF or Correction drafts scope.

**Anatomy:** Scope header → filters/search → count → event list. Each row shows event type, actual event/service date, context and recorded-on date when different; current record revision and void/superseded labels remain. Historical events use origin customer/site, not current owner.

**Content and inputs:** Type All activity/Service records/Contacts/Follow-ups/Changes. Date All or From/To. Sort Event date newest/oldest/Recorded most recently. Search across source-supported entered text, not OCR/PDF-byte scanning. FC-01 all-records scope and report/correction-attention modes reuse this list; no new event schema or performance metrics.

**Visible actions and menus:** Type/date/sort/search/Clear via UI-54; entry → UI-25 service, UI-19 cancellation, UI-29 contact, UI-27 task, UI-46 read-only change, UI-26 correction draft; Record past visit → UI-18 actual date required; Back to originating equipment/site/customer/Work/Home. In an existing entity scope, filters never silently change the entity.

**Interaction, focus and persistence:** Opening a historic transition does not replay it. Current entity links are explicitly named. Past entry from scope suggests context but requires actual identification review; default history-only. Returning from a correction preserves the history position and shows new revision without removing original event.

**Meaningful states:** Empty history, no matches, failed read; equipment moved between customers with origin labels; backdated entry; current/superseded/voided records; failed report/correction draft attention. Old attachments may be missing without hiding their record.

**Resilience:** Large histories remain filterable and searchable; detailed notes in row show excerpt with full text in detail, not a paragraph per row. Screen-reader headings label groups by event date.

**Rationale:** Global history is a discoverability addition from F, not a competing fourth navigation section.

<a id="UI-41"></a>

## UI-41 · Unsaved changes, recovered input and failed departure

**Traceability and surface:** C:D01; F:U01; X-07. Layout L-09.

**Initial viewport:** Named editor/record, exact saved checkpoint, whether changes are uncommitted. A short ordinary-form warning fits a native alert; a multi-field save failure opens UI-58 full screen.

**Anatomy:** Title → affected record/context → consequence → action buttons. Never a stack of confirmations. Recovered input uses a banner/card on restart or reopening the editor, not a separate general drafts organizer.

**Content and inputs:** Ordinary copy “Discard changes to Treadmill 02? The saved equipment record will not change.” Recovery copy “Recovered unsaved customer details · 10:12. Review and save to add this customer.” Failed departure: “Changes after 10:14 are not saved. Leaving may lose them.” No editable fields.

**Visible actions and menus:** Keep editing/Resume recovered input; Discard changes; Retry saving when a durable Working/correction write failed; Leave without unsaved changes only after failure acknowledgement. F Save and leave is not a separate control: return to the editor’s Save button under C. In working mode “Leave visit for later” is allowed only for saved checkpoint semantics, not labelled Cancel visit.

**Interaction, focus and persistence:** Default focus on title/message; safe Keep editing first in reading order, destructive loss action named specifically. Back closes alert and returns to editing, never discards. Retry confirms actual durable state before allowing departure. Missing recovered parent routes to safe parent list/explanation, not a different record.

**Meaningful states:** Unchanged form exits directly; dirty ordinary; recovered buffer; saved working draft; failed working save; missing parent; failed buffer cleanup. No false claim every last keystroke was recovered.

**Resilience:** At large font dialogs scroll and buttons stack; no truncated warning or tiny dismiss target. UI-58 takes over if more than a bounded decision is needed.

**Rationale:** Different saved states need different wording; “Cancel” cannot honestly mean undo for an autosaved visit.

```text
L-09 / UI-41
Leave without the latest changes?
V-001 · T-01 Condition inspection
Last saved on this device: 10:14
Changes after that checkpoint are not saved.
[Keep editing]       safe/default
[Retry saving]
[Leave without unsaved changes]    explicit loss
Ordinary-form variant: [Keep editing] [Discard changes].
Recovered-input variant: [Resume recovered input] [Discard].
```

<a id="UI-42"></a>

## UI-42 · Context selector and navigation-only chooser

**Traceability and surface:** C:D02; F:U02. Layout L-02.

**Initial viewport:** Purpose title such as Choose site/Choose equipment/Choose template; visible parent scope; search; selection count where multi-select; first labelled candidates. No automatic keyboard when choosing from short known context.

**Anatomy:** Full screen with Cancel, purpose, search/filter, result list and Use selection footer. Single navigation-only instance opens on row tap and has no misleading Use button. Child Add replaces selector with full editor, never stacks another sheet.

**Content and inputs:** Instances: customer name/ref; site name/customer/address; equipment name/EQ/internal/serial/site; plan title/equipment/due/claim; template name/revision/active; follow-up title/type/date/equipment/source; time-zone list where used uses same search layout with zone names rather than business data. Historical/inactive toggle appears only in a caller that can use it. Clear optional template remains a named None row or parent Clear action, not a compulsory selection.

**Visible actions and menus:** Search/Clear; row/checkbox; Use selection; Add customer/site/equipment/template only where source permits; Show historical/inactive; Cancel. Selected hidden-by-filter count/View selected. Reserved plan Open visit link navigates, not selects. Machine selector for working navigation uses existing selected machines only, no new work addition.

**Interaction, focus and persistence:** Caller supplies permissible context; no cross-site visit selection. A newly saved child returns selected but does not save the unrelated parent. Cancel returns original selection. Single selection can replace only after tapping Use when staged; no guessing first match. Result count announcement after query pause.

**Meaningful states:** Empty valid list with permitted Add, no match/Clear, failed read/Retry, inactive ineligible with explanation, deleted result before Use, selected hidden by filters. A historical entity can be inspectable without selectable for new work.

**Resilience:** Full-row targets, checkbox not nested conflicting action; long names show unique refs. At 200% text footer grows and list retains scroll. No 40-item half-height bottom sheet.

**Rationale:** Explicit selection is the main protection against working on the wrong similar machine.

<a id="UI-43"></a>

## UI-43 · Lifecycle, deletion, discard and dependency review

**Traceability and surface:** C:D03; F:U03; X-04/X-07. Layout L-06.

**Initial viewport:** Named operation and exact entity; effect summary and blocking records. Confirm label is Archive customer/Retire equipment/etc., never OK. Small one-item removal may use alert; dependency operations use full screen.

**Anatomy:** Entity → why/effects → dependency list → required reason/choice → confirmation footer. Blockers are actionable rows returning to refreshed review. Only a state-valid operation has an enabled confirmation.

**Content and inputs:** Archive/cancel/discard reasons where required by source; no reason invented for harmless cancel-navigation. Customer/site/equipment/plan relationships and counts shown. Delete never offered for a referenced record. For unused default-site deletion, explicitly choose another active default or “Leave customer without a default.” C has no automatic lifecycle cascade.

**Visible actions and menus:** Open blocking item; Confirm named action; Cancel. Per-instance effects matrix immediately below this entry is normative. Transient item removals name actual dependent staged data; existing live tasks are not deleted. F’s automatic child cancel/pause policy not active.

**Interaction, focus and persistence:** Before confirming revalidate all blockers and current state. If a blocker appeared, show it and leave prior data unchanged. Cancellation returns without mutation; success returns surviving parent with saved result and clear next action. No Undo promised. An ordinary deletion failure retains data, not a half-deleted tree.

**Meaningful states:** Eligible/blocked; root/child; archive/restore; retire/return; pause/end through UI-46 where date needed; delete unused; remove staged evidence/template item; discard working/correction; cancel booking. Correction move guard FC-03 in UI-12.

**Resilience:** Each dependency row has type/ref/context and ≥48dp Open; long lists scroll. Reason field/error remains above keyboard. Screen reader hears operation and affected count before confirm.

**Rationale:** Resolving dependencies individually is more friction than cascading, but the chosen C policy avoids concealing cancelled customer work.

### UI-43 instance matrix — operation-specific controls and effects

Each instance has a visible entry below, the UI-43 context/effect/dependency regions, **Cancel**, an operation-named confirmation, and source-specific validation. Only the reason requirements explicitly stated here/source contracts are imposed; a blocked review is not a data mutation. An inactive parent explains why restoration is unavailable. Successful operations preserve the relevant historical records and return to the surviving parent/detail with its new state.

| Instance and qualified entry | Fields/confirmation and initial attention | Dependencies / exact effect / return |
|---|---|---|
| Customer archive · C:S06-A07 | UI-06 More → **Archive customer**; name, site/work counts, required Reason, **Archive customer**. Blockers precede reason. | Every site must already be archived; no open customer task or booked/working visit. Open each blocker, do not cascade. History retained. Return UI-06 Archived. |
| Customer restore · C:S06-A07 | UI-06 archived state → **Restore customer**; effects summary; **Restore customer**. | Restores customer only; children/plans/tasks/bookings not reactivated. Return active UI-06 with relevant archived lists still labelled. |
| Customer delete · C:S06-A08 | UI-06 More → **Delete unused customer**, only when eligible; show any empty default sites also removed; **Delete customer**. | No dependencies/history. Otherwise no delete action, with archive guidance. On success return UI-05, focus next customer or empty state. |
| Site archive · C:S08-A09 | UI-08 More → **Archive site**; name/customer, dependency list, archive reason according to the source lifecycle contract; **Archive site**. | All equipment already retired/moved; no booked/working/open anchored task. Default-site consequence must be resolved visibly. No child cascade. UI-08 Archived. |
| Site restore · C:S08-A09 | UI-08 archived state → **Restore site**; parent status then effects; **Restore site**. | Active customer required. Does not return retired machines to service or resume plans. UI-08 active. |
| Unused site delete · C:S08-A10 | **Delete unused site**; if default, **Choose replacement default** / **Leave without default** then **Delete site**. | Entirely unused; no hidden reassignment of customer equipment. UI-06 site list. |
| Equipment retirement · C:S10-A10 | UI-10 More → **Retire equipment**; identity, plan/visit/task blockers, required Reason; **Retire equipment**. | All plans ended, no booked/working/open linked task. Retirement does not mean successful servicing. UI-10 Retired. |
| Return to service · C:S10-A10 | Retired UI-10 → **Return to service**; parent status, ended-plan summary; **Return to service**. | Parent customer/site active. Ended plans remain ended; show **Add service plan** as the next deliberate action. |
| Equipment delete · C:S10-A11 | Eligible UI-10 More → **Delete unused equipment**; identity and photo effect; **Delete equipment**. | No plan, visit, contact/task/history/move references. Otherwise Retire route. UI-08 equipment list or originating UI-05 scope. |
| Pause plan · C:S13-A06 | UI-13 More → **Pause plan**; date retained, claims list, required Reason; **Pause plan**. | Clear booked/working claims first. Excludes obligation from active due/reminders; does not resolve corrective tasks. UI-13 Paused. |
| Resume plan · C:S13-A07 | UI-13 Paused → UI-46 **Resume plan**; retained date, **Keep current date** default or replacement date + reason. | Paused only, valid active context. Old overdue date becomes overdue immediately. UI-13 Active; no booking created. |
| End plan · C:S13-A08 | UI-13 More → **End plan**; retained history, required Reason; **End plan**. | Active/paused, no booked/working claims. Ended is terminal; no misleading Resume for ended plans. UI-13 Ended. |
| Delete unused plan · C:S13-A10 | Eligible UI-13 More → **Delete unused plan**; equipment/title/date; **Delete plan**. | Never referenced except setup metadata. Otherwise End. UI-10 plan list. |
| Template archive · C:S16-A04 | UI-16 More → **Archive template**; affected-plan count, frozen-revision explanation; **Archive template**. | Existing assignments retain usable frozen content; hidden from new choices. Do not detach assignments. UI-16 Archived. |
| Template restore · C:S16-A04 | Archived UI-16 → **Restore template**; name/revision; **Restore template**. | Restores new-selection availability; no historical checklist rewrite. Restore before edit/duplicate as C specifies. |
| Template delete · C:S16-A05 | Eligible UI-16 More → **Delete unused template**; item count; **Delete template**. | Never referenced by plan/work/record. Otherwise archive. UI-15 list. |
| Remove staged template item · C:S17-A04 | Item row menu → **Remove item**; exact item label and staged answer-schema loss, **Remove item**. | Changes only the template editor until Save template; do not delete historical answers. Return item list/focus next item. |
| Remove selected working service/machine · C:S20-A03 | UI-20 group/line menu or UI-55 → **Remove service** / **Remove equipment from visit**; actual counts of answers/notes/findings/parts/photos/claims; named confirm. | Draft only. Removing one line preserves machine-level evidence while another line uses it. Removing the machine names all discarded evidence. At least one line required to finalize. Live tasks not deleted. Return refreshed UI-20. |
| Discard working visit · C:S20-A10 | UI-20 More → **Discard working visit**; required Reason, counts, ad-hoc versus booking-origin consequence; **Discard working visit**. | Ad hoc draft removed; booking-origin retains Cancelled booking and discards stated working data. Release claims; no fulfillment/task closure. Offer ordinary Cancel to return and preserve partial work. Return Work. |
| Cancel booking · C:S19-A05 | UI-19 More → **Cancel booking**; required cancellation reason, selected obligations remain due; **Cancel booking**. | Booked only, not a hidden discard of actual work. Keeps cancelled booking history and releases reservations. UI-19 Cancelled. |
| Discard correction · C:S26-A07 | UI-26 More → **Discard correction draft**; base revision and staged changes; **Discard correction**. | Removes only staged revision. Original/final reports/plans/tasks unchanged. Return UI-25 base record. |
| Remove finding · C:S23-A06; C:S22-A08 | UI-23 More **Remove finding**, or changed-answer UI-22 **Keep finding** / **Remove draft finding**; description and linked staged task/photos summary. | Remove only working/staged scope. Existing tasks/evidence are not silently deleted; unresolved checklist link repaired explicitly. Return originating UI-21/22. |
| Remove part · C:D09-A02 | UI-49 saved-draft part → **Remove part**; part/quantity; **Remove part**. | Removes working record only, not inventory or history. Return UI-21 part section. |
| Remove photo · C:D04-A06 | UI-44 photo More → **Remove photo**; caption/machine/report inclusion; **Remove from this draft** when eligible. | Does not erase source gallery or retained historical evidence. Parent staged/save contract governs commit. Return next photo/grid. |
| Delete erroneous contact note · C:S29-A05 | UI-29 More → **Delete erroneous note**; context/date and deletion warning; **Delete note**. | Only erroneous unlinked note under C. Linked/history-restricted state explains why absent. Not F's retained entered-in-error operation. UI-40/contact context. |
| Move / whole-dataset erase boundaries | Move remains full UI-12; erase full UI-52. UI-43 lists shared dependencies, not an alternative shortcut. | See C:S12-A03, C:D12-A02 and FC-03. No “Undo” or hidden cascading convenience. |


<a id="UI-44"></a>

## UI-44 · Photo intake, evidence selection and retained-photo viewer

**Traceability and surface:** C:D04; F:U04. Layout L-08.

**Initial viewport:** Exact machine/visit or “Business logo” context, photo/placeholder, caption and report inclusion for evidence. Intake starts with Take photo/Choose photo, not a fake custom camera.

**Anatomy:** Source picker entry → OS camera/photo selection → copy/validation progress → fit-image preview → caption/inclusion → Use/Done. Multi-photo viewer shows count and accessible Previous/Next/Zoom/Fit. Single directory/logo photo omits evidence report controls.

**Content and inputs:** Caption Optional per source text limits. Visit evidence Include in customer report Off initially; selected checkbox with public boundary. Profiles: one equipment image/one logo. C guardrails 20 photos per machine/100 per visit, optimized up to2560px long edge without upscaling, metadata handling per source; fine text must be checked at full size. Source limits are unvalidated product proposals, not performance findings.

**Visible actions and menus:** Take photo; Choose photo; View/Previous/Next/Zoom in/out/Fit; caption; Include in report; Remove/Replace with named confirmation; Use/Done; Locate missing original only integrity-matching recovery. In correction/final record, field availability follows edit/read-only context; missing original cannot be replaced by a different photo outside correction.

**Interaction, focus and persistence:** Parent checkpoint precedes external launch. Copying not yet Saved; failure/cancel adds nothing, earlier photo intact. Late result from previous dataset/context rejected safely. Remove a draft photo shows affected finding/report selections and removes only valid draft associations. Use applies to calling staged editor or working draft according to P-02; it does not finalize a record.

**Meaningful states:** New intake/copying/ready/cancelled/failed; no photo versus missing retained file; selected/excluded; max count; historical readonly; exact original located versus mismatch. Missing selected photo blocks faithful final PDF; not silently omitted.

**Resilience:** Fit preserves nameplate edges; controls outside image so contrast does not depend on image. Caption available to screen reader alongside machine/context; photos themselves require meaningful captions when important to interpretation. Pinch/swipe optional only.

**Rationale:** Durable local copies and truthful intake state matter more than a polished gallery animation.

```text
L-08 / UI-44
[Back]               T-01 · Photo 1 of 2
V-001 · Condition evidence
[fit image area; no text drawn over the photograph]
[Previous] [Zoom −] [Fit] [Zoom +] [Next]
Caption · Optional
[Fraying on outer belt edge]
[✓] Include in customer report
[Remove] [Replace]                    editable draft only
[Done]
Missing-original variant:
PH-01 · File missing · selected for report
[Locate original] [Open recovery help]
A different image requires a record correction.
```

<a id="UI-45"></a>

## UI-45 · Contact and navigation handoff

**Traceability and surface:** C:D05; F:S14. Layout L-09.

**Initial viewport:** Contact [actual name], context site/customer, recipient source choice and available phone/email/address. Main actions labelled Call, Text message, Email, Open map with icons and ≥48dp rows.

**Anatomy:** Purpose/context → Site contact/Customer contact selector → visible recipient values → available handoff rows → copy alternatives → Record contact outcome → Close. Full screen at large text/many values; otherwise a single sheet, not nested sheets.

**Content and inputs:** Recipient selection controls exactly whose details will be used. Missing phone/email/address says Not supplied. Prepared message, when offered by source context, uses public identity only and is previewable/copyable; private access notes are not inserted.

**Visible actions and menus:** Open dialer; Compose text; Compose email; Open map; Copy phone/email/address/prepared message; Record contact outcome → UI-29; Close. Missing data links the specific current customer/site editor (after task-save guard), not a request for broad contacts permission. No automatic call, send, SMS history, location tracking or delivery check.

**Interaction, focus and persistence:** Show visible values before external handoff. Return retains original context and offers Record contact outcome without prefilling success. Copy feedback is noncritical snackbar. Missing handler: keep data visible, Copy/manual fallback. Close/Back never writes a contact note.

**Meaningful states:** Site inherited/custom/current customer recipient; no data; no handler; cancelled external flow; actual return unknown; moved equipment displays current site address explicitly before mapping. Historical report contact not silently mixed with current contact.

**Resilience:** Phone/email/address wrap and remain selectable through copy action; no hidden labels behind ambiguous icons. Link to Edit opens a full editor, closes handoff sheet.

**Rationale:** The product assists communication; it cannot know whether a call connected or a message was received.

<a id="UI-46"></a>

## UI-46 · Reasoned date/state change and historical change detail

**Traceability and surface:** C:D06; F:U03 date/state. Layout L-06.

**Initial viewport:** Named change, exact plan/task/record, Current value and Proposed value; reason; confirm. Historical view starts with event date/reason and Read-only, no editable field.

**Anatomy:** Context → before/after → dependent effects → new date/state/reason → named confirmation. Resume variant offers Keep current due or Choose a new due; end/pause uses same vocabulary but no invented date shift.

**Content and inputs:** New due date where applicable, required change reason under source policy; dates fully named with year. Resume can retain overdue date and explains that overdue work reappears. Read-only event displays both original and new value, effective/event date and recorded date if different.

**Visible actions and menus:** Confirm named change; Keep current value when resume permits; Cancel; Close only in historical mode. Complex correction schedule effects use UI-57 instead. No one-tap historical replay or “Undo move.”

**Interaction, focus and persistence:** Date picker stages value; confirmation commits with exact source guards and record history. Cancel leaves current due/state unchanged. Next keyboard does not confirm. A stale current value triggers renewed review, not overwrite.

**Meaningful states:** Manual due change, Pause, Resume keep/change, End where allowed; read-only historic change; backdated reason; blocked claim; invalid date; save failure. Future due dates valid while actual service dates not future.

**Resilience:** Stack Current/Proposed on narrow screens; no color-only red/green diff. Focus reason after date choice if required.

**Rationale:** Before/after presentation is cheaper to understand than explaining recurrence transactions each time.

<a id="UI-47"></a>

## UI-47 · Resolve, cancel or reopen a follow-up

**Traceability and surface:** C:D07; F:S21 closure. Layout L-06.

**Initial viewport:** Action title and exact task, type/equipment/source; outcome/reason/date. Staged mode explicitly “Applied when V-001 is finalized.”

**Anatomy:** Context → actual closure/reopen fields → effect statement → named action footer. Short complete content may be a dialog, but multiline notes/large text use full page.

**Content and inputs:** Resolve: outcome text and actual resolution date not future. Cancel: reason required. Reopen: new follow-up date plus reason, active context required. No checkbox asserting machine safety or recurring-service completion. Default in completion review is Keep open, not Resolve.

**Visible actions and menus:** Resolve / Cancel follow-up / Reopen according to mode; Back. Existing-task Open link remains available before deciding. Staged resolution uses explicit Use resolution wording as presentation of the same source action; it is not immediately applied.

**Interaction, focus and persistence:** Standalone action saves immediately after confirm. From working/correction review it stages effects, labelled until finalization. Cancelling dialog leaves default Keep. Failure preserves Open or prior closed state. Reopening does not reopen an old visit or recreate a plan.

**Meaningful states:** Live open task, resolved, cancelled, staged closure, invalid actual date, inactive context blocks reopen, source changed while reviewing.

**Resilience:** Long task title and reason fully visible. Footer grows and is protected from keyboard. Screen reader action label includes task ref where helpful.

**Rationale:** A task can be closed for several legitimate reasons; closure is not evidence that every service was performed.

<a id="UI-48"></a>

## UI-48 · Template-item editor

**Traceability and surface:** C:D08; F:S25. Layout L-04.

**Initial viewport:** Add/Edit item and parent template name; Label, Type and Required response; Save item footer. New Label gets focus.

**Anatomy:** Label → Type → numeric Unit if applicable → Required response → optional internal Guidance. A single explicit child save returns to the parent’s item list.

**Content and inputs:** Label Required under C’s120-character title guard; Type Status(default)/Short text/Number; Required response On default. Unit Required for numeric, ≤32 characters; guidance Optional ≤500 and private. No options editor for arbitrary answer vocabularies, branching or formulas.

**Visible actions and menus:** Type; Required response; Save item; Cancel/Back. Changing type confirms incompatible staged values/unit before clearing; not an invisible coercion. The parent editor owns eventual revision save.

**Interaction, focus and persistence:** Child Save validates label/type/unit and stages it in UI-17; Cancel discards child input, previous item unchanged. Dirty child uses UI-41, then returns to parent. Reordering not inside child; parent retains position.

**Meaningful states:** New/edited, numeric missing unit, text/status unit hidden, changed type warning, recovered parent draft. Saved original template unchanged until parent Save template.

**Resilience:** Type choices full-width or vertical radios at large text. Unit input not a tiny suffix; its requiredness announced with label.

**Rationale:** A deliberately small item vocabulary avoids an accidental custom-form subsystem.

<a id="UI-49"></a>

## UI-49 · Part used editor

**Traceability and surface:** C:D09; F:S19-D02; X-06. Layout L-04.

**Initial viewport:** Part used and exact machine/V-001; description, quantity and unit, Save part. Initial focus description.

**Anatomy:** Context → Description → Quantity/Unit → optional public note → Save; Remove part in edit-only More/footer text action with confirmation.

**Content and inputs:** Description Required; Quantity Required default1, positive, up to3 decimal places; Unit Required default “piece”; public note Optional ≤500. Fixture “Guard fastener · 1 piece.” No cost, inventory deduction, catalog or purchasing.

**Visible actions and menus:** Save part; Remove part in edit mode; Cancel. Entry points explicitly UI-21 Parts used and corresponding correction mode. F service-specific part attachment is not adopted; the parent machine is fixed.

**Interaction, focus and persistence:** Save durably updates working/correction draft; not final report until finalization. Decimal localization per P-03; invalid zero/negative preserves input. Remove confirms actual part description and does not change unrelated plan/task.

**Meaningful states:** New/edited, invalid number/unit, save failure; finalized part viewed read-only in UI-25/39, correction editable.

**Resilience:** Quantity/unit can share a row normally, stack at large text. Description multiline/wrap within source guard; no short numeric-only “part code” assumption.

**Rationale:** Parts are recorded evidence of work, not a stock-management commitment.

<a id="UI-50"></a>

## UI-50 · Notification rationale and system permission

**Traceability and surface:** C:D10; F:S32 enabling. Layout L-09.

**Initial viewport:** Why notifications are useful, approximate timing limit, Enable notifications and Not now. No customer names or urgent fear copy.

**Anatomy:** Short rationale → actionable permission request only on deliberate enable → return state linked to UI-32. Permission dialog itself is OS-owned UI-59.

**Content and inputs:** “Get optional work summaries and appointment reminders. They may arrive late. Your work lists are available without them.” Permission/app/channel state shown when returning. No exact-alarm permission or battery exemption demand.

**Visible actions and menus:** Enable notifications; Open Android notification settings when permission/channel unavailable or already denied as appropriate; Not now. Reading the explanation does not itself request permission.

**Interaction, focus and persistence:** Enable uses supported runtime request when applicable, otherwise actual settings route. Not now keeps working functionality available. Denial is not followed by repeated automatic prompts. Source C app preference remains staged in UI-32 until Save, separate from actual OS grant.

**Meaningful states:** First request, already allowed, denied, channel disabled, missing settings handler fallback explanation, unsupported request version. Test notification elsewhere says Requested only.

**Resilience:** Button text wraps; screen reader starts on rationale. Native dialog fonts/appearance not controlled by ServiceLoop.

**Rationale:** Reminders assist the service book; they are not its authoritative state.

<a id="UI-51"></a>

## UI-51 · File operation progress, cancellation and terminal result

**Traceability and surface:** C:D11; F:U05. Layout L-07.

**Initial viewport:** Named operation, actual phase, exact source/destination identity and progress when measurable. Cancel/Retry/Return labels depend on state, not all at once.

**Anatomy:** Operation title → phase list/current phase → measured counts/bytes or honest indeterminate status → warnings → allowed action footer → durable result summary on completion/failure.

**Content and inputs:** No free-form inputs except file/destination selectors in parent. Copy text examples “Copying photo 2 of 3”, “Writing backup file”, “Reopening written file for verification”, “Replacing local dataset—do not close this operation.” No fabricated percent or completion time.

**Visible actions and menus:** Cancel operation before commit; Retry if safe; Choose another file/destination; Done/Return to record. Save copy/Share/external viewer are invoked by parent UI-36/39, not a universal file browser. During protected final replacement/commit Cancel unavailable with reason.

**Interaction, focus and persistence:** Failure retains original dataset/file/parent draft where source promises; external partial file cleanup is best effort and disclosed. Retried completion identifies already committed result. Progress disappears only after a readable outcome; critical failure is never solely a snackbar.

**Meaningful states:** Preparing, copying/writing, verifying, protected commit, cancelled, failed, written unverified, verified, success. Parent controls determine whether output exists and whether cancellation may leave a file.

**Resilience:** Operation summaries wrap, step list does not dominate small screens. Live region announces phase changes sparsely, not every byte. Keyboard absent unless returning to passphrase/date form.

**Rationale:** Users need to know what is safe to cancel and what actually finished, not watch an attractive fake spinner.

<a id="UI-52"></a>

## UI-52 · Erase local dataset

**Traceability and surface:** C:D12; F:S33-A07. Layout L-06.

**Initial viewport:** Erase this device’s data title, dataset identity/counts and last verified backup; “This cannot be undone here.” Backup first visible before confirmation.

**Anatomy:** What is removed → what is not removed → backup status → typed phrase and acknowledgement → Erase local data. Full page so saved drafts and attachment effects are not compressed.

**Content and inputs:** Remove local customers/sites/equipment/plans/visits/tasks/templates/profile/settings/drafts/revisions/app-held media and PDFs under source dataset policy. Exported/shared files outside app remain. Type ERASE, explicit acknowledgement, not prefilled. Last verified full backup shows snapshot age and not-same-as-current caveat.

**Visible actions and menus:** Make backup first → UI-34, return revalidated; Erase local data once confirmed; Cancel. No one-tap factory-reset icon. No wipe of gallery originals, provider folders, customer copies or other devices.

**Interaction, focus and persistence:** Revalidate dataset identity after returning from backup or interruption. Before commit Cancel leaves data. Failed erase does not open a false empty welcome while old data is unreadable; show recovery result. Successful erase opens genuine first-use UI-01. No Undo promised.

**Meaningful states:** No backup, stale/verified/incomplete backup, working/correction/ordinary drafts present, insufficient storage/failure, cancelled. Incomplete copy is not a full recovery guarantee.

**Resilience:** Phrase input and warning never behind keyboard/footer; destructive button distinct but not selected by default. TalkBack reads scope before input.

**Rationale:** Explicit scope matters because “delete everything” often falsely implies deletion of already shared copies.

<a id="UI-53"></a>

## UI-53 · Possible duplicate and CSV branch decision

**Traceability and surface:** C:D13; F duplicate controls; X-03/X-07. Layout L-09 / L-06.

**Initial viewport:** Possible duplicate title and two named identities with refs, site/customer and serial/contact evidence. Source supplied similarities are shown; no invented confidence score.

**Anatomy:** Candidate A versus new/imported B as vertically stacked panels → meaningful distinguishing fields → available actions. CSV mode includes row/parent refs and dependent proposed-child count; full screen when long.

**Content and inputs:** Read-only identity comparison. Manual same-name customer/equipment warning permits separate record under C, but same-site name/active-plan name/template rules and exact CSV key conflicts remain hard errors with no override. CSV skip explicitly describes whole dependent branch if necessary.

**Visible actions and menus:** Open existing; Create separate record when policy allows; Go back and edit/Cancel new record; Skip imported row/group when eligible. No Merge, auto-renumber existing business identity or “Use first match.” Hard conflict offers corrected file, not Create separate override.

**Interaction, focus and persistence:** Open existing preserves staged creation; Back returns to comparison. Confirm separate authorizes only this candidate, not all future duplicates. CSV decision revalidates retained hierarchy and can change counts; no business mutation until import commit. Skipping parent names affected children first.

**Meaningful states:** Manual warning, CSV warning, hard key conflict, duplicate within file, missing candidate after refresh, similar machines with missing serial. Empty optional serial is not evidence of identical machine.

**Resilience:** Comparison not a two-column tiny table on phone; long names/context wrap. Three buttons stack and use explicit wording. Screen reader announces candidate labels separately.

**Rationale:** Speed must not come from silently deciding that two similar machines are the same asset.

<a id="UI-54"></a>

## UI-54 · List filters, sort and active-scope tools

**Traceability and surface:** C:S03/S04/S05/S15/S40; F:U08. Layout L-02 / L-04.

**Initial viewport:** Filter [list] title, current fixed context, chosen filter fields, Apply filters and Clear. Sort is a short single-choice sheet; complex filters use full screen with no nested sheets.

**Anatomy:** Named context → list-specific fields → selected count/hidden-selection notice if relevant → Apply/Clear footer. Applied filters return as readable chips and result count on parent. A fixed historical entity scope is not silently clearable.

**Content and inputs:** Work Due: state Active/Paused/Ended/All; date Overdue/Today/Today through horizon/Upcoming/All/Custom; booking Any/Unbooked/Booked/Working; customer/site; sort Due oldest/Customer-site/Equipment. Work Visits: Booked/Working/Finalized/Cancelled/All; next7/Past booked/All/Custom; sort date asc/desc/last edit. Work Follow-ups: Both/Contact/Corrective; Open/Resolved/Cancelled/All; due overdue/next7/all/custom; customer/site/equipment; sort due/customer/updated. Customers: active/archive/all; equipment inservice/retired/all +customer/site +Any/Has active/No active plan; name/recent/nextdue sort. Templates: active/archive/all; name/changed. History: types/dates/sort per UI-40. Global search: entity/inactive per UI-04.

**Visible actions and menus:** Each actual filter choice; date From/To; Apply filters; Clear filters; Cancel; Sort option; search/Clear remain on parent. UI-03 Due Select services is separate from filtering. Exact defaults: Due Active overdue through horizon; Visits Booked+Working all dates, working recent then upcoming then passed; Follow-ups Open all dates; directory active; templates active; history all/newest. Scope/template/import batch chips explicitly removable only where navigation permits.

**Interaction, focus and persistence:** Apply validates From≤To, no live business mutation. Cancel preserves previous applied filters. Selecting a sort applies once and returns; it does not reorder stored records. Retain hidden selection; switching to incompatible scope offers explicit clearing rather than dropping items silently.

**Meaningful states:** No active filters, custom range invalid, no matches, fixed origin scope, selected hidden rows, failed query. Clear cannot restore data or unarchive records; it changes visibility only.

**Resilience:** Radio/checkbox rows ≥48dp; large font single column; date fields stack. All controls keyboard-accessible, no horizontal pill-only gesture requirement.

**Rationale:** The field-specific filter catalogue prevents “Filters” becoming an unspecified feature bucket.

```text
L-04 / UI-54 Due filters
[Cancel]                  Due-service filters
State [Active ▾]
Due [Overdue through horizon ▾]
Booking [Any ▾]
Customer [Any customer ▾]
Site [Any site ▾]
Scope: Template General condition check [Remove scope]
4 selected · 1 hidden by proposed filters [View selected]
[Clear filters] [Apply filters]
Sort is separate: Due date / Customer and site / Equipment.
```

<a id="UI-55"></a>

## UI-55 · Work selection and one-off embedded editor

**Traceability and surface:** C:S18-A03/A04; C:S20-A02/A03; F:U06. Layout L-02 / L-04.

**Initial viewport:** Select work, fixed selected site, search, machine groups with eligible plan checkboxes; selected count and Use selected work footer. One-off editor is a replacement child screen, not a nested sheet.

**Anatomy:** Context → Search → selected summary → equipment headings and plan rows → Add equipment / Add one-off per named machine → Use selection. Working add mode labels “Added work captures current identification/template now”; removal has source-specific recorded-content confirmation.

**Content and inputs:** Rows: equipment name/EQ/internal/serial/site; plan name/due/state/claim/template. New one-off: Title Required, default “One-off service”; optional active checklist; machine fixed/required. No recurring interval or next due for a one-off. Historical mode can inspect inactive context but live current-obligation claims remain guarded. “At another site” live selection is not allowed to cross an existing visit; explain start another visit.

**Visible actions and menus:** Search/Clear; select plan; Add one-off/Edit one-off; remove selected work; Add equipment opens permitted selector/editor; Include inactive historical-only; Use selection; Cancel/Back; Open reserving visit for conflicts. One-off child Save item/Use service stages title/template, Cancel preserves previous selection. Creating new machine saves that machine but not the visit.

**Interaction, focus and persistence:** Disallow duplicate same plan within visit and second claim elsewhere. A filtered-out selected service remains counted; View selected available. Changing site happens in UI-18 with confirmation, not inside this selector. Removal in Working explicitly warns about lost item answers and retained machine evidence where another service remains. One-off after answers cannot silently replace template.

**Meaningful states:** No plans→one-off path; no equipment→Add equipment; reserved/paused/ended/retired modes; duplicate selection; invalid blank one-off name; many items; pending save. No bulk successful answer or selected outcomes.

**Resilience:** Plan checkbox rows roomy; long machine labels full in heading. One-off title keyboard leaves context and Save reachable. Multi-selection is full-screen even on large device if child keyboard needed.

**Rationale:** The workflow supports legitimate one-off work without creating a false recurring obligation.

```text
L-02 / UI-55
[Cancel]                    Select work
Site: Riverside Centre…          [Search]
T-01 / EQ-001 · window side
[✓] Condition inspection · due 1 Sep
[✓] Lubrication service · due 1 Sep
[Add one-off service for T-01]
T-02 / EQ-002 · door side · Serial not supplied
[✓] Maintenance · due 1 Sep
[ ] Electrical inspection · due 15 Sep
[Add equipment]
3 services selected · same site
[Use selected work]
Child: One-off service / T-02
Title · Required [Assess console rattle]
Checklist · Optional [None ▾]
[Cancel] [Use service]
```

<a id="UI-56"></a>

## UI-56 · Captured identification review

**Traceability and surface:** C:S20-A05; C:S24-A01; C:S26-A01; F:S28-A02. Layout L-06.

**Initial viewport:** Review identification, record/working context and “Captured for this visit” versus “Current directory” values. Changed values are textual before/after rows, not only highlights.

**Anatomy:** Visit/site identity → machine identity list → business/report identity → difference summary → explicit refresh/keep action. Historical entry/correction mode permits source-supported captured text corrections without changing master records.

**Content and inputs:** Captured customer/site/address; equipment name/make/model/serial/internal ref; business/technician/logo. Current directory comparison may differ because name/address/template changed. Wrong entity relationship cannot be repaired here; use void/recreate in finalized context. Private access/current contact remains separate and excluded.

**Visible actions and menus:** Review each identity group; Use current identification explicitly when allowed; Keep captured identification; edit captured text in historical/correction mode; return to working/review. These are subcontrols of C:S20-A05/C:S26-A01, not a general data-merge utility. Opening current entity detail does not itself refresh.

**Interaction, focus and persistence:** Show exact fields that will change, apply only after explicit choice, checkpoint resulting draft. Existing finalized record unchanged until correction commit. If current record moved/deleted, preserved snapshot stays readable; no replacement with a similarly named entity. Updating master profile alone never changes issued PDF.

**Meaningful states:** No differences, changed directory, historical intentional old address, missing current entity, correction wrong subject, logo missing, save failure. Keep original remains legitimate when it describes the actual service context.

**Resilience:** One group per full-width panel; long addresses wrap. At large text use Captured then Current, not side-by-side comparison. No truncated changed identifier.

**Rationale:** Dependable history requires showing which version of identity the technician is approving.

<a id="UI-57"></a>

## UI-57 · Next-date confirmation and correction reconciliation

**Traceability and surface:** C:S24-A03/A04; C:S26-A02; F:S20-A03/A04; F:S28-A03. Layout L-06.

**Initial viewport:** Exact plan/machine, current/original due, actual completion, interval snapshot, proposed next date and effect status. A blocked date change shows why before any confirm.

**Anatomy:** Context → dated evidence → current plan state/claim → proposed effect or no-change reason → reason/override field → Confirm effect/Keep current date/Cancel as permitted. In completion it returns a per-line staged decision; in correction it returns reconciliation.

**Content and inputs:** Completion: old obligation due, latest counted/supplied prior service, actual date, captured interval, calculated next. Override date requires reason; “Use calculated date” restores visible proposal, not automatic approval. Correction: original contribution, current next due, intervening manual change/later fulfillment, proposed new next date, retained Active/Paused/Ended state. T4 latest-date example 5 Dec→4 Dec; later fulfillment example Keep current schedule, historical correction only.

**Visible actions and menus:** Fulfill/History only remains UI-24 line control; here Confirm next date/Use calculated date/override field/Cancel. Correction: accept eligible proposed effect; keep current date with explicit reason when manual/intervening change allows; open blocker/current plan; acknowledge no change. No universal “Recalculate everything.”

**Interaction, focus and persistence:** Actual date must be strictly later than latest counted completion or known prior baseline; same-day second service is history-only. Original/latest ownership and downstream claims revalidated. Paused stays paused with retained date reconciled; ended stays ended. Later service’s current schedule is not rolled back. Crossing neighbor counted dates removes fulfillment contribution/history-only with explicit effect review. Commit remains at parent finalization/correction.

**Meaningful states:** Eligible active, record-only, same-day/backdated, manually changed due, later fulfillment, paused/ended, booked/working blocker, stale comparison, invalid override. If retained calculated next date is still overdue it is shown as such—never fast-forward until future.

**Resilience:** Before/after stacked, full year shown; reason multiline. Focus on required acknowledgement after comparison, not automatically on confirm. Return restores original service line.

**Rationale:** This local review keeps technically complex guards understandable as concrete dates and ownership of today’s schedule.

```text
L-06 / UI-57
Confirm next date · Condition inspection / T-01
Obligation due: 1 Sep 2026
Actual service: 5 Sep 2026
Interval captured for this work: 3 months
Calculated next due: 5 Dec 2026
Next due [5 Dec 2026]                [Use calculated]
Override reason appears only when changed.
[Confirm next date] [Cancel]
Correction with later fulfillment:
Original contribution: V-001, 5 Sep
Current next date belongs to: [later record]
This correction changes history only.
Current next date: [unchanged date]   [Open later record]
[Acknowledge no schedule change]
```

<a id="UI-58"></a>

## UI-58 · Save failure, missing media and integrity recovery

**Traceability and surface:** C:D01/C:S33/E13; F:U07; FC-02. Layout L-10.

**Initial viewport:** Persistent “Not saved” or “Some stored files are missing” title, exact record/last checkpoint, what remains safe, and Retry. It is never displayed as an empty database or a short-lived toast.

**Anatomy:** Specific problem → preserved data checkpoint → affected input/file list → primary Retry/Locate original → secondary storage/text rescue/recovery/help → explicit leave-with-loss route only when applicable. Failed-read recovery shows no Add customer masquerading as empty.

**Content and inputs:** Save example “V-001 · T-01 inspection. Last saved 10:14. The newest note and answer are not saved.” List known unsaved text fields, not a false claim that all data is in memory. Missing media includes exact PH/reference, record revision and report inclusion. Storage status shown only when obtainable. No edit of database internals.

**Visible actions and menus:** Retry save/read; Open device storage settings (FC-02); Copy unsaved text (FC-02) with scope/privacy notice and no secrets; Leave without unsaved changes → UI-41; Data and recovery/Restore → UI-33/35 with dirty-save guard; Help → UI-38. Missing original → UI-44 integrity-match location; affected record row → UI-25/21; explicit correction or incomplete recovery-copy path where eligible. No “repair” button that silently drops records.

**Interaction, focus and persistence:** Retry waits for actual save success and updates timestamp. Copy text is a manual salvage route, not backup; photos/structured business effects are not rescued by copying text. Opening Android storage settings checkpoints what can be checkpointed; return rechecks available state. Never delete original saved dataset after failed migration/read.

**Meaningful states:** Low storage; transient write failure; failed read; missing attachment; unexpected corruption; interrupted migration; old dataset still valid; external settings unavailable. Restoration requires validated package and explicit replacement, not a hidden error-dialog action.

**Resilience:** Error text large enough, wrap full record identity, persistent until resolved/deliberately left. Screen reader announces once and can revisit heading/actions. Keyboard input retained when memory exists; no forced text selection overwritten by banner focus.

**Rationale:** Data-loss prevention deserves its own recovery surface, rather than borrowing the operational overdue warning style.

```text
L-10 / UI-58 save failure
[Back]                   Saving problem
Not saved
V-001 · T-01 · Condition inspection
Last saved on this device: 5 Sep, 10:14
The latest note and answer are not saved.
Earlier saved work is still available.
[Retry saving]
[Open device storage settings]               FC-02
[Copy unsaved text]                           FC-02
Copied text may contain private information.
[Data and recovery] [Help]
[Leave without unsaved changes] → named loss review
No Finalize / Share / successful-save badge here.
```

<a id="UI-59"></a>

## UI-59 · OS-owned surfaces and notification entry states

**Traceability and surface:** C:D04/D05/D10/D11; C:S32 notification interaction; F:S32-A12/A13/A14. Layout L-09.

**Initial viewport:** ServiceLoop shows purpose/context immediately before handoff and a truthful return status afterward. It does not draw or control the camera, Android permission dialog, Sharesheet, document provider, keyboard or dialer UI.

**Anatomy:** Caller checkpoint → native handoff → caller restored focus/context → optional next legitimate action. System notifications use generic app/title/body text and a single destination; no customer/finding/access information.

**Content and inputs:** Camera/photo picker: named parent machine/logo; selected-contact picker: chosen phone or email field; file picker: operation/type suggested filename; dialer/composer/map: previewed recipient/data; external PDF viewer: version. Summary notification “ServiceLoop · Work needs attention. Open your work list.” Appointment “ServiceLoop · Appointment around 14:00. Open visit.” Test “ServiceLoop · Test notification.” Exact delivery not promised.

**Visible actions and menus:** User choices inside OS surface belong to Android/provider. ServiceLoop return actions: Retry intake/Choose another source; Record contact/handoff; Copy fallback; Open settings; Return to record. Notification tap summary→UI-02, appointment→UI-19 or its actual Working/Finalized state; dismiss changes no business data. No notification Mark complete or Snooze. Permission rationale UI-50 precedes requests.

**Interaction, focus and persistence:** Cancelled camera/picker/share never creates a success event. Recheck permission/channel state on return. A stale appointment notification after cancellation opens cancelled state and no Start on original. After restore, stale callbacks from previous dataset are ignored with explanation, never attached to a new record with a matching display name.

**Meaningful states:** Missing external app/provider, no permission, cancelled, late callback, device/process interruption, changed orientation, force-stop/reboot effects on reminders, same/different device time zone. External availability limits never disable offline editing.

**Resilience:** Native surfaces inherit system accessibility; ServiceLoop restores focus to the launching control and announces actual result. No imagined screenshots prescribing third-party layouts or buttons.

**Rationale:** Explicit ownership of handoffs prevents the design from promising events the app cannot observe.


<a id="F"></a>

# F. End-to-end workflow demonstrations

These are walkthroughs of the proposal, not observations from a prototype. The fictional dataset and T0–T4 snapshots are defined in B3. Outcomes are entered by the technician; the app does not diagnose equipment or infer successful work. The earlier source journeys are covered individually in J4.

## F1. First useful obligation, then a complete first machine

**Starting condition:** T0, an empty business dataset, on 31 August 2026. No business logo, notification grant, template or contact record exists. The initial due date in this example is deliberately 1 September, not calculated from an invented previous service.

UI-01 **Start empty** opens UI-02. **Add first customer** opens UI-07. Enter **Harbor Fitness and Rehabilitation Cooperative**; the visible First site group initially says **Main site** and **Uses customer contact**. For the minimum path, accept that site label and leave optional contact/address blank. Save creates the customer and the site together. UI-06's **Add first equipment** opens UI-11 with that site visibly named. Enter **Treadmill 01 — window side**, optionally **T-01** as internal identifier, then **Save equipment**. UI-10 displays the generated equipment reference; absent serial/photo are honest missing identity detail, not validation failures.

**Add service plan** opens UI-14. Enter **Condition inspection**, change interval to **3 months**, select **1 Sep 2026**, leave earlier service/template blank, and **Save plan**. The success returns to the machine with a visible plan row: **Condition inspection · Every 3 months · Due 1 Sep 2026**. A second **Add service plan** creates **Lubrication service · Every 6 months · Due 1 Sep 2026**. Neither creates a past service record. Work's due horizon includes the two plans; the next business day they become due without permission or connectivity.

To reach the full fixture, edit the default site in UI-09 to the long Riverside name/address, add the remaining identity fields/photo in UI-11, and add T-02, B-01 and their three plans. These are ordinary saved edits, not further onboarding gates. Create the five-question template through UI-15 → UI-17 → UI-48, save it, later make a deliberate revision to reach revision 2, and assign it to P-001 before V-001 starts. Check the actual question text in UI-16; no preloaded specialist procedure is implied. The first backup at 1 Sep 08:00 occurs after this fixture directory exists; its precise captured inventory is defined in F9.

**Where effort is removed:** the first site is not a separate mandatory wizard; serial, logo and template do not gate the first obligation. **Where effort remains:** a machine and a named plan are separate records; its first due date must be deliberately entered. A minimal record is visibly incomplete in identity, not silently “completed” with guesses.

**Import alternative:** UI-01 or Settings → Data and recovery → UI-37. Read the no-update rule, save the fixed template, choose a prepared CSV, review errors/warnings and confirm the valid set. At success, **Equipment without active plans** opens UI-05 with a visible filter. The app does not manufacture service dates from imported machines. C permits further create-only batches; it never becomes an inline spreadsheet editor or synchronization tool. X-03/J2 retain F's different initial-only contract.

## F2. Due work, contact, booking and postponement

**Starting condition:** all fixture directory records exist, the four 1 September obligations are unclaimed, and the technician is on UI-03 Due services. This is before V-001 was booked.

Enter **Select services**. The technician chooses P-001 and P-002 on **T-01 / EQ-001**, P-003 on **T-02 / EQ-002 — Serial not supplied**, and P-004 on **B-01 / EQ-003**. P-005, due 15 September, remains unselected. The footer reads **4 selected · Riverside Centre**. Similar machine names do not collapse into a single selection. **Contact selected site** opens UI-45 showing whose phone/email will be used. Selecting **Open dialer** opens the system dialer; returning leaves the outcome unknown.

**Record contact outcome** opens UI-29. Select Call and **Appointment agreed**, retain or correct the occurred time and add factual details as appropriate. Save records the contact, not a booking. Use **Book visit** on the saved note or return to **Book selected**. UI-18 retains/reviews the same site and four services; choose **3 Sep 2026, 09:00**, review the optional 60-minute duration/reminder, then **Book visit**. UI-19 confirms **Booked**, not Serviced. Work Due still shows the four obligations and a separately labelled appointment link.

On 2 September, open V-001 and **Edit/reschedule booking**. Set **5 Sep 2026, 09:00** and enter **Customer requested Saturday access**. The review says **Appointment: 3 Sep → 5 Sep. Service due dates: unchanged**. Save returns to V-001, then Back returns to the same due-list position. No service leaves the overdue queue merely because an appointment moved. V-002 is separately booked for the 8 September one-off assessment; it does not reserve P-005 or mark any recurring service arranged.

**Unavailable dialer/composer:** UI-45 retains visible recipient values and **Copy phone/email/address**; the technician can perform the action manually. Cancelling any external surface does not add a contact record. An **Attempted—no response** note is valid without a booked visit. **Cancelled booking:** requires a reason, keeps its history and frees its claims; creating another visit starts a new booking, not a resurrection of an old attendance record.

## F3. Beside the correct machine, with interruptions

**Starting condition:** 5 September, UI-19 V-001, before work. **Start visit** reviews current equipment, selected plan/template changes and actual date, then creates a durable Working visit. UI-20 displays both the original appointment and **Actual service date: 5 Sep 2026**. A changed plan cannot be silently accepted just because the appointment was saved earlier.

Open the **Condition inspection** row directly beneath **T-01 / EQ-001 — window side**. UI-22 keeps that identity above the answers. Record the work note, select Guard fixing **OK**, Belt condition **Issue found**, enter **1240.5 km**, mark Optional accessory **Not applicable** with **Not fitted**, and leave the optional observation blank. No answer is copied from the other treadmill.

The issue reveals **Add finding details**. UI-23 fixes the machine/source question. Enter **Fraying on outer belt edge** and choose **Corrective follow-up needed**. **New corrective follow-up** opens UI-28 staged mode; set **Replace worn belt — T-01**, **12 Sep 2026**. **Use follow-up** returns a proposal, not a live task. Save the finding. Add PH-01 using UI-44, check the copied photo's legibility and caption; retain PH-02 as an excluded nameplate photo. Select **Mark checklist reviewed** only after looking through all required responses. The saved state becomes Reviewed while the issue remains visible.

From UI-20, open T-01 lubrication directly; record **Cleaned accessible surfaces; lubricant unavailable**. Open T-02 maintenance, verify **EQ-002 / door side**, record **Checked and secured guard fixing**, then its machine evidence area to save **Guard fastener · 1 piece** and PH-03. For B-01 retain **Not assessed** because the room cannot be accessed. No evidence is transferred merely by jumping to another machine.

At T2, leave after the checkpoint **Saved on this device · 10:14**. UI-02 offers **Resume V-001**, the last saved machine/service context, and the saved timestamp. Reopen to that exact context at 10:15. **Back to visit** still reaches the overview. Saved answers, photos, proposals and scroll position return; there is no claim that an unacknowledged final keystroke survived.

**Interrupted capture:** a returned file is validated/copied before the photo becomes saved. Camera cancellation retains prior media; an empty returned file is not success. After process recreation, the intake still belongs to the original machine, not whichever machine is now visible. After replacement restore, that old callback is invalid and must not attach to the restored dataset. **Low storage:** UI-58 retains the last checkpoint, offers Retry and the explicitly proposed FC-02 rescue actions, and prevents a false Saved/Finalize state.

## F4. Mixed completion: four services, not one global “Done”

**Starting condition:** T2's working record contains the observations, work notes and the corrective proposal, but no obligations have advanced. The two selected report photos are PH-01 and PH-03; PH-02 remains excluded.

UI-20 **Review completion** opens UI-24. Verify actual date and captured identity. Every service initially asks for its own outcome. For P-001 choose **Performed**; its checklist is Reviewed, including the issue. Explicitly check **Fulfill this service obligation**. UI-57 displays **Due before 1 Sep → Actual service 5 Sep + captured 3 months → Next due 5 Dec 2026**. Confirm that next date; no silent date advancement occurs yet.

For P-002 choose **Partly performed**. Its work note remains visible. No fulfillment choice is enabled: **Remains due 1 Sep 2026**. For P-003 choose **Performed** and explicitly fulfill; confirm **Next due 5 Mar 2027**. For P-004 choose **Not performed** and enter **Room unavailable**. **Not assessed** remains the truthful machine observation and **Due 1 Sep** is retained. P-005 is not in this review; there is no control that changes it indirectly.

Inspect the new corrective proposal and its 12 September date. Keep FU-001 Open; the app does not resolve a contact task because a service visit finished. Review PH-01/PH-03 inclusion and public captions; the private-nameplate image remains excluded. Business identity is required before finalization. If incomplete, UI-31 collects the two public names; return to UI-56 to explicitly use that identity in the draft. Preview the complete draft in UI-39, including unfinished work. It has no Share or Save-copy controls.

The footer reads **2 obligations advance · 2 remain due · 1 corrective follow-up to create**. **Finalize record** commits that exact review once. It opens UI-25 V-001 revision 1 at T3; further taps/recovery cannot consume the next obligations. Home now shows 2 overdue, 1 due soon, 1 booked, 0 unfinished, and 1 due follow-up / 2 open total. A valid zero-fulfillment visit would instead say **No service obligation fulfilled**, not fail merely because work could not be done.

**Return work:** UI-25 **Create return visit** offers P-002/P-004 as still outstanding; it does not reselect already fulfilled P-001/P-003 or prefill Performed. The corrective FU-002 can be deliberately linked to work on T-01. V-002 remains its existing separate one-off booking unless edited through its own booking review. A second day's actual work is a new visit: do not change V-001's date to span both days.

## F5. Handover without confusing record, file and delivery

**Starting condition:** T3, V-001 revision 1 has finalized successfully. Its PDF is not yet created.

UI-25 **Generate customer PDF** generates from the fixed public revision. On success UI-39 shows the exact rendition, its unique version identity, **Generated 5 Sep 2026, 10:42**, and Current. Read the first-page action summary and each machine's detailed results; switch to **Text** for reflowed reading. Review PH-01/PH-03 and their captions. Saving or sharing does not open a report-builder. Printing is an external-app/manual handoff; no extra Print button is added.

**Share PDF** opens the native Sharesheet with that exact version. Returning leaves **Delivery not confirmed**. **Record handoff note** opens UI-29 for an optional factual note: for example, select In person and record **Printed copy handed to the site contact** only if that occurred. The app does not certify receipt, understanding or safe operation. **Save copy** reports a written copy to the chosen provider; it does not register a recipient.

If generation fails at 10:42, UI-25 still states **Service record saved** and offers **Retry PDF creation**. The fallback **Read customer report text** remains available. Retry after resolving storage reads the same fixed revision; no schedule/task change happens again. If PH-01 is missing, faithful generation is blocked with that photo named. Locate the exact original, restore deliberately, or create an explicit correction; do not silently omit the photo. PH-02 never leaks into a report simply because another image is missing.

## F6. Return to history, changed identity and correction

**Starting condition:** T3 main fixture. Later master-data edits change the site's address and template wording. UI-40 from EQ-001 History shows V-001 under its actual 5 September service date; Recorded on remains separately available. UI-25/UI-39 retain the old captured address, checklist revision 2, and exact PDF bytes. A current-equipment/current-site link clearly opens today's directory information instead. FU-002's **At this visit: created, due 12 Sep** is separate from **Current task: Open**; a later resolution must not rewrite that old report.

For the **T4 what-if branch only**, correct a mistaken actual date from 5 September to 4 September. UI-26 requires **Actual work was on 4 Sep; date entered incorrectly**. UI-57 shows the two affected contributions: P-001 **5 Dec → 4 Dec 2026**, P-003 **5 Mar → 4 Mar 2027**. Confirm each eligible effect while P-002/P-004 remain due 1 September and P-005 remains 15 September. Preview and commit revision 2. Revision 1 and its PDF remain identifiable as superseded. This branch is not included in the main T3 backup counts/examples.

If a later valid service already owns today's due date, the same correction is historical only; display the later record and **Current next due stays [date]**. If a manual due edit intervened, keep it by default and require the recorded acknowledgement/reason or explicit replacement. If correcting crosses/equates a neighboring counted completion, remove the erroneous counted contribution and review consequences instead of silently reordering history. A downstream booked/working claim blocks a schedule-changing commit until deliberately resolved; text-only correction remains possible.

**Wrong machine/customer:** choose Void in UI-26, not a new subject picker. Show original pre-fulfillment dates as candidates where this record still owns the contribution, with the same later-service/manual/claim guards. Keep existing tasks unless a specific erroneous task is cancelled with reason. Retain the voided record/reports, then create correctly associated work through UI-18. The original subject links are never quietly moved. Sharing a superseded/voided original is an explicitly warned historical-copy path under C/X-10, not the default handover.

**Past entry:** UI-10/13/40 **Record past visit** opens UI-18 with actual date required and History only. Optional supplied prior-service information in UI-14 is not a substitute for a visit. A same-day second service of the same plan remains history-only under C. A past date that yields an already overdue calculated next date stays overdue; the app does not skip cycles until it reaches a future date.

## F7. Templates and independent obligations over time

UI-15 → UI-16 opens the actual ordered template; Duplicate creates a distinct unsaved copy, and Edit requires an active template. UI-17's item More → **Move up / Move down** exposes reordering without dragging. Save creates a new revision; existing working/final checklists retain their wording and order. An archived but still assigned template remains visibly usable at its frozen latest revision under C. New assignments cannot select it until restored. F's replacement/omission requirement is not activated.

On UI-13, Pause or End first exposes any booked/working claim as a blocker. No booking is automatically cancelled. Pause retains the date; Resume in UI-46 asks **Keep 1 Sep 2026** or **Choose another date**, with its required reason. Keeping an overdue date brings it back to overdue work. End is terminal. Returning retired equipment to service does not resume ended plans; create a new plan deliberately. Editing an interval changes future calculations, not the current due date without an explicit reasoned date edit. These occasional tasks remain in named More menus, never mixed with fieldwork's primary controls.

## F8. Ownership and lifecycle without leaking history

**Separate what-if after T3, not a mutation to the main fixture.** Move T-01 to a new active customer's site. UI-10 More → Move equipment opens UI-12. While FU-002 is Open the move is blocked; its row opens UI-27. The technician must resolve/cancel it honestly or leave the equipment unmoved. A linked booking/working visit or related correction draft (FC-03) similarly blocks. No cascade resolves these on behalf of the technician.

Once eligible, choose the destination with full customer/site names, enter effective date and reason, then review the carried states/dates: P-001 Active / 5 Dec, P-002 Active / 1 Sep under this branch. C carries them even across customers; X-04 leaves that choice for owner review. The acknowledgement names both plans. Confirm moves current assignment only. V-001 remains in the old customer/site history; equipment history spans both contexts with origin labels. New reports use the new customer and never import old access notes or private contact/task commentary. Old photographs are not automatically new-report selections. UI-43 gives the analogous dependency-first archive/retire/unused-delete paths; its instance matrix is in E.

## F9. Full backup, readable export and replacement recovery

**Fixture precision:** the 1 Sep 08:00 full backup contains 1 customer, 1 site, 3 equipment records, 5 plans, 1 template with its two saved revisions, the EQ-001 identification photo, and the saved business profile; no visits, contact notes or follow-ups yet. Bookings/contact/task events occur afterward. This defines actual inspected counts for the older-snapshot example rather than pretending file metadata reveals them. The T3 10:50 backup has 2 visits (V-001 finalized and V-002 booked), 2 open follow-ups, 4 retained images, and exactly one generated PDF rendition **only in the successful-10:42-generation branch**. In the PDF-failed branch, it has zero PDF files and the truthful pending/failed generation state; that is not a missing-file defect.

From UI-33, **Create full backup** → UI-34. Check records/files; include durable working/correction drafts and durable ordinary-form recovery buffers as such, without turning them into saved business entities. Protect using the confirmed passphrase; acknowledge no reset. Choose a destination using Android's file interface. UI-51 names each phase. A provider write acknowledgement yields **File written** only. Reopening and verifying the complete output yields **Verified full backup · snapshot 5 Sep 10:50**. Later edits still show Changed since backup even if the file finished writing later. Keeping a copy outside the phone and independently confirming its availability remains the technician's responsibility.

If PH-01 was already missing before backup, Full is unavailable. **Create incomplete recovery copy** lists PH-01, its record/report impact and every other known gap. It preserves readable records/files only after explicit acknowledgement, never updates Last verified full backup, and is not a repair of unexplained store corruption. A missing file in a purported full package is invalid, not reclassified as an intentionally incomplete copy.

**Restore older snapshot:** UI-35 Choose → Unlock and inspect. Current T3 and older 1 Sep counts appear in separate panels. The comparison says **2 visits and 2 follow-ups now on this device are absent from this snapshot**, alongside the relevant saved changes; it does not imply only these records will be replaced. Back up current data first or deliberately acknowledge skipping. Review replacement, type REPLACE, confirm. Cancel before switch leaves current data. A staging/validation failure leaves current data and explains the file/problem. A successful switch opens a durable result showing the older snapshot, zero restored visits/tasks, one retained image and reminders Off. No modern reports already shared externally are deleted. The restore summary explains that external newer reports can still exist; unique version identity prevents false equivalence if a visible revision number later repeats.

**Inspect only** returns without replacement. Wrong passphrase does not affect current records; forgotten backup credentials have no reset. Readable CSV export in UI-36 is a different workflow: choose scope/type, review private data Off, write/share a plain readable file, and retain the **Not a full backup** result. It cannot restore images/reports. C's directory export alone can feed the create-only importer; records exports do not become bulk-edit data.

## F10. Denied reminders, late return and changed windows

UI-32 only requests Android notification permission after deliberate enabling through UI-50. Denial leaves every due list and saved workflow usable; **Open Android settings** is a choice, not a looping prompt. Save the compound app preferences separately from any system grant. A test produces **Test requested**, not proof of audible delivery.

A late return after weeks recomputes current obligation urgency and presents passed unresolved bookings for review; it does not assume attendance, cancel records, replay every missed alert or move the selected list underneath the technician. Stale notification taps open the record's actual state or explain an unavailable target. Business time zone controls business Today; actual service dates do not turn into times or slide when the device zone changes. Rotation, keyboard appearance and resizing preserve the same draft/context, with one-pane fallback when text no longer fits; field input is never reset simply to get a cleaner layout.

## F11. Friction estimates and decisions deliberately retained

**Counting convention:** a navigation transition is an opening/return between named app surfaces, including selectors/editors, but not scrolling, keyboard show/hide, tab selection, inline expansion or OS-owned screens. An explicit decision is a meaningful choice/commit (select machine/outcome/date, Save, acknowledgement), not every text character or a Back press. Typing counts required text fields in the stated path; optional notes/photos are counted only where deliberately included. Estimates assume valid input, existing referenced records where stated, no save failure, and normal-size phone layout. They are review aids, not measured speed claims. Native picker taps vary, so ranges are appropriate.

| Daily path / starting condition | Approximate navigation transitions | Explicit decisions / required typing | Deliberate friction versus avoided switching |
|---|---:|---|---|
| Empty Home → first useful plan; default site, no optional fields | 6: UI-02→07→06→11→10→14→10 | About 7: save customer, save equipment, interval/unit review, due choice, save plan plus task-entry/context acceptance; 3 required text fields (customer, equipment, service name), one interval and one date input | No identity/logo/permission wizard; explicit due cannot be removed. |
| Add second plan from equipment detail | 2: UI-10→14→10 | About 3–4: review interval, choose date, save; 1 service-name field; date input | No repeated customer/site/machine typing. |
| Contact from an already selected single-site Due set and save an outcome | 3–4 internal transitions via UI-45/29 and return; 1 OS launch+return separately | Channel/outcome/occurred review + Save, about 4; zero required free text unless Other chosen | Cannot infer connected call; machine selections and list position retained. |
| Book four selected services from Due, no further selection needed | 2: UI-03→18→19 | Appointment date/time, duration/reminder review and Book, about 4–6; zero narrative typing | Selection identity remains visible; no need to revisit three equipment detail pages. |
| Reschedule an existing booking | 2: UI-19→18→19 | New date/time, reason and Save, about 3–4; 1 reason field | Reason/unchanged-due explanation is intentional, not extra customer entry. |
| Resume saved checkpoint from Home | 1 to UI-22 or UI-20 according to checkpoint | 1 Resume choice; zero typing | No forced walk through customer/site/equipment hierarchy. |
| Record one ordinary status answer while UI-22 is open | 0 | 1 explicit answer; zero typing for OK | No modal for ordinary answer. An issue appropriately adds finding/disposition work. |
| Complete fixture after evidence/task/photo choices are already saved; start UI-20 | 6: 20→24→57→24→57→24→25; optional preview adds 2 | 4 outcomes + 2 fulfillment checks + 2 date confirmations + identity/date/task/photo review + Finalize: about 12–15; 1 not-performed reason, earlier work notes already present | Per-service decisions cannot be replaced with bulk Done. Performed is not preselected. |
| Ready PDF from finalized record → native Share | 1 internal UI-25→39; 1 OS handoff separately | View/version review + Share, about 2–3; zero required typing | Manual handoff note is optional, never an automatic “received” state. |
| Verify a healthy backup from Data hub | 2 internal main surfaces; native destination separately | Check, passphrase confirmation, no-reset acknowledgement, Create, destination, Verify: about 6–8; passphrase twice | No daily safety interruption; verification is intentionally separate from writing. |

The biggest remaining friction risk is the distinction between **Performed** and **Fulfill**. Retain the two meanings but teach them locally with the date consequence, not a help article. A second risk is the required issuer identity only discovered at the first finalization; Home/Settings can show its incomplete status quietly, and the return path must preserve the whole draft. Do not solve either risk by preselecting successful work or forcing branding before first use.

<a id="G"></a>

# G. Fixed report presentation and recipient experience

## G1. Document contract and hierarchy

This is one fixed document layout implementing C:S39/R05/R10, not a report-builder feature. **A4 portrait, 210×297mm** is C's proposed format; no launch market is inferred. Use 15mm page margins, a 180mm content column and a reserved 10mm footer zone; the layout grows to additional pages. Do not scale a long report down to fit. The customer is a recipient, not an app-account user.

Use an offline available, embeddable sans-serif font with script coverage appropriate to the eventually approved languages. Proposed print roles: business/report heading 18pt/23pt medium; machine headings 13pt/18pt medium; service headings 11.5pt/16pt medium; body, values and outcome explanations **10.5pt/15pt** regular; table headers 10pt/14pt medium; metadata/page furniture 9pt/12pt. Essential conclusions and identifiers never shrink below the body role. Print typography is in points, not Android sp; the app's Text view uses D's scalable roles instead. Font licensing/embedding/script shaping require implementation verification; no font files are part of this package.

Use black/dark neutral text on white. Teal may distinguish headings and fine rules, but no meaning relies on tint. Outcomes use printed words; an empty checkbox never stands for an unanswered question. The customer must distinguish **work performed**, **issue observed**, **work outstanding**, **task planned**, and **next recurring due date** without remembering a color legend.

| Order / report region | Exact presentation and limits |
|---|---|
| 1. Issuer and identity | Business/display name, technician, optional public phone/email/address; optional logo fit within 35×18mm without distortion. Missing logo collapses its space. Right/below: **Service record**, visit reference, actual service date. No “certificate”, certification seal or signature box. |
| 2. Document status | Current revision identity and generated rendition identity/time. Draft: **DRAFT — NOT FINALIZED**; corrected: **Corrected record — revision 2**, reason; void rendition: **VOID — not a valid current service record**, reason. An already issued original's bytes remain unchanged: its later superseded/void status is shown in the app/version selector, not magically stamped into the old file. |
| 3. Customer and site | Full captured customer, site and service address. Wrap long names rather than ellipsizing. Material missing identity says **Serial not recorded** / **Site address not recorded**. Optional absent contact fields may be omitted. |
| 4. Visit result overview | Service count/machine count and factual outcome summary. T3: **4 services on 3 machines. 2 service obligations fulfilled; 2 remain outstanding.** Then a compact outstanding-work block naming the two incomplete services and the corrective task. This is a reading aid derived from detailed records, not a new summary field. |
| 5. Equipment sections | Captured name, internal reference, generated reference, make/model and serial. One section per machine, in the visit's recorded order; no invented drag-to-reorder report feature. Service lines within each machine retain their recorded order and independent outcomes. |
| 6. Service detail inside equipment | Named service and plan or **One-off work**; **Performed / Partly performed / Not performed**; public work or reason; full captured checklist with explicit answers, NA reasons and unrecorded items; recorded obligation effect, including old/new dates or History only. Never suppress an incomplete checklist to imply a pass. |
| 7. Equipment observations/evidence | Recorded observation plus public observations; findings with description, disposition and resolution/no-action explanation; parts with quantity/unit/note; corrective follow-up title and planned date **as recorded at finalization**. Findings/photos are machine-scoped under C, not arbitrarily assigned to every plan. |
| 8. Selected photographs | Only explicitly included retained visit photos, with machine identity, caption and stable photo label. Source-question/finding link printed where it exists. No directory photo/logo reuse as visit evidence. Captions alone cannot include private planning notes by default. |
| 9. Footer on every page | Visit reference, record revision, short unique rendition identity, generated date, **Page n of N**; machine continuation identity when applicable. Full version identity appears in document metadata and a readable end-of-report identity block if too long for the footer. |

The outstanding-work overview reports only the fixed snapshot. It does not pull in subsequently changed task dates or recommendations entered weeks later. A future corrective date is **Planned corrective follow-up**, never the **Next recurring service due**. A finding with **No further action** prints the technician's reason; it is not an app guarantee. No “safe to operate,” regulatory conformity or legally qualified signature is generated.

## G2. T3 report content, completely reconciled

**Mira Field Service — Alex Marin**; **Service record V-001 · revision 1**; **Service date 5 September 2026**; **PDF v1 · generated 5 September 2026 at 10:42 Europe/Bucharest**. Illustrative short rendition label **RPT-V001-R1-A** represents the display of an implementation-generated unique identity, not a proposed sequential ID algorithm. Full customer/site/address are the B3 values.

**At a glance:** four services, three machines. Two obligations fulfilled. **Outstanding service:** T-01 lubrication and B-01 maintenance remain due 1 September. **Corrective follow-up planned:** Replace worn belt — T-01, 12 September. The issue is not hidden beneath the word Performed.

### T-01 · EQ-001 · Treadmill 01 — window side

StrideWorks R8 · Serial SW-R8-0417.

**Condition inspection — Performed.** Work: **Inspected recorded items; belt issue observed.** Checklist General condition check, revision 2:

| Question | Recorded response |
|---|---|
| Guard fixing | OK |
| Belt condition | Issue found — Fraying on outer belt edge |
| Displayed distance | 1240.5 km |
| Optional accessory | Not applicable — Not fitted |
| Additional observation | Not recorded (optional) |

**Checklist reviewed. Service obligation fulfilled.** Due before service: 1 September 2026. Actual completion: 5 September 2026. **Next condition inspection due: 5 December 2026.**

**Lubrication service — Partly performed.** Work: **Cleaned accessible surfaces; lubricant unavailable.** **Obligation not fulfilled. Remains due: 1 September 2026.** There is no substitute “next due 5 March” from the inspection or another plan.

**Machine observation:** Issue observed. **Finding:** Fraying on outer belt edge. **Disposition:** Corrective follow-up needed. **Planned corrective follow-up:** Replace worn belt — T-01, 12 September 2026. If public work notes contain a genuine recommendation it appears as that technician-recorded text; none is invented from the reading. **Photo PH-01:** Fraying on outer belt edge. PH-02 is not in the report.

### T-02 · EQ-002 · Treadmill 02 — door side

StrideWorks R8 · **Serial not recorded.**

**Maintenance — Performed.** Work: **Checked and secured guard fixing.** **Service obligation fulfilled.** Due before service: 1 September 2026. **Next maintenance due: 5 March 2027.** No checklist was attached to this work item; do not print fictitious checked questions. **Observation:** No issue observed in recorded work. **Part used:** Guard fastener · 1 piece. **Photo PH-03:** Guard fixing after recorded work.

P-005 Electrical inspection, due 15 September, was not selected for V-001. It is neither printed as performed nor added to the report's outstanding-selected-service count. The directory still retains its live obligation. The report is not an exhaustive current inventory of every plan at the site.

### B-01 · EQ-003 · Stationary bike 01

WheelWorks C2 · Serial WW-C2-023.

**Maintenance — Not performed.** Reason: **Room unavailable.** **Observation:** Not assessed. **Obligation not fulfilled. Remains due: 1 September 2026.** No photograph/part/reading is fabricated. FU-001's private contact planning task and its notes are absent from the public report.

A zero-performed variant prints **Attendance recorded; no service obligation fulfilled**, the selected items/reasons and any genuinely recorded observations. It never says Completed successfully. The actual service record may still be a legitimate saved report of attendance.

## G3. Annotated first page and continuation wireframes

Text below is a layout diagram, not final pagination evidence. Actual page count is determined from font metrics and content; no promise that this fixture fits exactly two or three pages is made.

```text
REPORT / PAGE 1 — A4, 15mm margins
MIRA FIELD SERVICE                          SERVICE RECORD
Alex Marin                                 V-001 · Revision 1
[optional logo, no placeholder box]         Service: 5 Sep 2026

Harbor Fitness and Rehabilitation Cooperative
Riverside Centre — East Building, Second-floor Training Room
18 River Lane, Riverside

4 services · 3 machines
2 obligations fulfilled · 2 remain outstanding
OUTSTANDING WORK                                      textual heading
T-01 Lubrication — partly performed; remains due 1 Sep
B-01 Maintenance — not performed; remains due 1 Sep
T-01 Belt replacement — corrective follow-up planned 12 Sep

T-01 · EQ-001 · Treadmill 01 — window side              machine heading
StrideWorks R8 · Serial SW-R8-0417
Condition inspection — PERFORMED
Work: Inspected recorded items; belt issue observed.
Checklist: General condition check · revision 2
Question                           Recorded response
Guard fixing                       OK
Belt condition                     Issue found — see finding
... every actual item printed; no omitted failure row ...

V-001 · Record rev 1 · RPT-V001-R1-A · 5 Sep 2026     Page 1 of N
```

```text
REPORT / CONTINUATION PAGE
V-001 · Service 5 Sep 2026 · Record revision 1
T-01 · EQ-001 — CONTINUED

[Remaining checklist rows; column headers repeated]
Obligation fulfilled: 1 Sep → next due 5 Dec 2026

Lubrication service — PARTLY PERFORMED
Cleaned accessible surfaces; lubricant unavailable.
Obligation not fulfilled. Remains due 1 Sep 2026.

Finding: Fraying on outer belt edge
Disposition: Corrective follow-up needed
Planned follow-up: Replace worn belt — T-01 · 12 Sep

[PH-01, fit image; no crop of relevant evidence]
PH-01 · T-01 · Fraying on outer belt edge

T-02 · EQ-002 · Treadmill 02 — door side
StrideWorks R8 · Serial not recorded
... next complete equipment section ...

V-001 · Record rev 1 · RPT-V001-R1-A · 5 Sep 2026     Page n of N
```

## G4. Pagination, photographs, missing content and grayscale

Keep each equipment heading with its identity and at least one service paragraph. Keep a service heading with its outcome and first work/reason lines. Repeat machine identity and **continued** on a new page; repeat checklist column headers. A long question/answer may split only with a continuation marker and preserved order; never drop the answer or reduce its font to force a row onto a page. Prefer a 65/115mm question/answer allocation within the 180mm column for ordinary short entries; use stacked label/value blocks for long answers or scripts. Keep an outcome and its recorded next-date consequence on the same page where practicable; if split, repeat the service title beside the date.

Do not split a photograph from its caption. Proposed evidence sizing: one 170mm-wide image for detailed evidence, or two 82mm images only when both remain legible at ordinary print size. Choose the width automatically from the fixed layout and aspect/legibility constraints, not a new report-layout setting. Fit the retained image inside its box, preserve orientation, do not crop the evidence to a decorative square. Very tall evidence may receive its own page. If a small source image cannot show its detail, label/retain it honestly and let the technician replace it through the valid draft/correction process; no AI enhancement or generated detail. Zooming in the app cannot create absent image detail.

Print test cases include black-and-white laser output and low-quality grayscale preview. Status text, rules and grouping must remain intelligible with all fills removed. Teal headings are supplementary; no outcome icon stands alone. Avoid faint gray body text. No watermark obscures findings; draft/void status uses a strong header/footer, with a restrained diagonal stamp only if it remains non-obstructing. A historical original's later status is known in the app; files already issued cannot be revoked or altered in recipients' possession.

Absent optional business contact/logo/part sections collapse cleanly. Material missing identifiers remain explicit. Unselected photos leave no blank gallery slots. A selected missing file blocks faithful generation and names the affected evidence; it does not create a successful PDF with a hidden omission. A deliberate correction can revise photo selection with reason and a new revision, visibly preserving the older version's identity.

## G5. Private/public preparation and structured text

In UI-24 and UI-25, public sections use **Customer report** labels. Private fields use a lock plus **Private — not in customer report**. The photo review lists Included/Excluded in text, machine context and caption. It never offers a switch to include customer/site access notes, internal task/contact notes or template guidance. A report preview is the actual public projection, not a layout that temporarily reveals private fields for convenience.

UI-39 **Text** presents the same public snapshot and rendition identity: document status → issuer/customer/site → summary → machine heading → service outcome/work → checklist labels/responses → due effect → observation/finding/disposition → parts → planned corrective date → selected photos/captions → version information. Use real headings, lists/definition pairs, selectable narrative text and explicit **View photo PH-01** links. Page navigation belongs only to PDF mode; text scroll is reflowed and independently restored. Switching modes preserves the nearest machine/service section where practical, otherwise opens that section heading rather than pretending exact pixel equivalence. No new edited content or live follow-up state enters this view.

The structured alternative supports in-app reading and assistive technology; it does **not** establish a tagged PDF, PDF/UA certification, legal compliance or universal recipient accessibility. Tagged/exported-document accessibility remains a release capability to test separately. The app offers a readable alternative, not a claim that every third-party PDF viewer will announce an image's caption correctly.



<a id="H"></a>

# H. Cross-cutting states, microcopy, accessibility and adaptation

## H1. State and feedback contract

This table supplements, rather than replaces, the state variants of UI-01–UI-59. An operational problem (a machine needing work), a workflow checkpoint (a reviewed checklist), a storage failure, and a historical status have different words and visual treatments. P-07 determines announcement and persistence; P-02 determines what is actually saved.

| Condition / principal surfaces | Visible treatment and exact example | Duration, next action and accessible behavior |
|---|---|---|
| New empty dataset · UI-02/05 | Neutral task panel: **Add your first customer**. Supporting text: “Start with a name and service location. You can add other details later.” | Remains until data exists. No zero-valued KPI wall. Add customer is primary; import/restore remain in first use and Data and recovery. |
| A real list has no items · UI-03/15/40 | Scope-specific: **No overdue services** / **No inspection templates** / **No history for this equipment**. | Explain a sensible next action, not an error. Do not present a failed read as an empty list. |
| Filters match nothing · UI-03/04/05/40 | **No matches for “SW-R8-0418”**; active scope and **Clear search** / **Reset filters**. | Preserve input and filter choices. Announce result count after settled input, not every character. |
| Local read failed · list/detail | Persistent error panel: **Couldn't read these records**. “Your records have not been deleted.” Show the known error without asserting corruption. | **Retry**; recovery help if repeated. Do not invent items, counts, a “0” result or a save success. |
| Overdue obligation · UI-02/03/10/13 | Small outlined urgency badge plus text **Overdue · due 1 Sep 2026**. Booking appears separately: **Working visit V-001**. | Does not disappear on booking/contact. No full-screen red background. Screen-reader row includes due and arrangement as different statements. |
| Reviewed checklist · UI-22 | Neutral/teal workflow label **Reviewed** with “5 items reviewed; 1 issue recorded” when accurate. | Changing a relevant answer returns it to **Needs review**. It never means the machine passed or the plan was fulfilled. |
| Ordinary input not saved · UI-07/09/11/14/17/31/32 | Editor title plus **Unsaved changes**; Save in toolbar. | On departure UI-41. Recovery buffers, when present, are labelled recovered input, not committed records. |
| Working write pending · UI-20/21/22/26 | Persistent compact status **Saving…**. Last durable checkpoint remains available in detail. | No simulated success, arbitrary progress percentage or finalization before writes complete. Do not announce each keystroke; announce a restored checkpoint or a failure. |
| Working write succeeds | **Saved on this device · 10:14** in the save-status region. | Update unobtrusively. This is not a backup claim. Device/process interruption resumes only the durable checkpoint. |
| Working write fails · UI-58 | Error banner **Not saved**. “Your latest changes are still on this screen. Last saved on this device at 10:14.” Use the timestamp only when known. | Stays until resolved or deliberate loss acknowledged. **Retry saving**, details, FC-02 rescue. Finalization blocked. Focus/announce once without ejecting the user from their field. |
| Low storage before photo intake · UI-44/58 | **Not enough space to keep this photo**. Explain which intake failed; existing retained photos remain visible. | **Retry** / **Choose a smaller photo** via the ordinary picker / storage settings if available. No blank successful thumbnail. No undocumented automatic deletion of records. |
| Camera/picker cancelled · UI-44/59 | Return to original machine/finding; retain all earlier content. | No error toast. A returned URI becomes **Copying photo…**, then retained or failed. Cancellation is not refusal of a required record. |
| Missing retained media · UI-44/58 | Named placeholder **PH-01 unavailable on this device**, its machine/caption/inclusion flag. | **Locate original** verifies identity, never silently substitutes a different photo. A customer PDF needing it is blocked; incomplete recovery is separately labelled under X-02. |
| Notifications unavailable · UI-02/32 | Neutral notice **Reminders are blocked by Android. Your due-work lists still work.** | **Review notification settings**, dismissible from Home without falsifying Settings status. No repeated permission prompt on every entry. |
| Ordinary offline work | No alarming network banner. Local context, saving and all eligible local actions remain available. | Explain connectivity only for the external action that needs it. “Offline” is not an all-purpose diagnosis for a local write failure. |
| External app unavailable · UI-45/59 | **No app available to open this address** (or dial/message/view). | **Copy address** and Back. No automatic installation prompt, sending, or broad permission request. |
| Return from dialer/composer/share | **Record what happened** is an optional manual note entry, not a prefilled successful outcome. | No “Sent” inference. A cancelled handoff leaves dates, task states and saved reports unchanged. |
| Record finalized, PDF pending/failed · UI-25 | Persistent successful record header **Record finalized · revision 1** plus separate error **PDF creation failed**. | **Retry PDF creation**; no Finalize button on this record. Service/date effects do not run again. |
| Historical, superseded, voided · UI-25/26/39/40 | Neutral historical strip **Superseded · revision 1**, or **Voided record** with reason/date and latest-revision link. | Read-only except explicit correction workflow. Do not recolor all old records as errors or silently replace an original's bytes. |
| Backup written, verification pending · UI-34/51 | **File written — not yet verified**. Destination and operation timestamp. | **Verify saved file**; no last-verified-backup update. Remote provider synchronization remains unknown. |
| Backup verified | **Full backup verified** plus snapshot time, counts, destination label and verification time. | **Done**. “Keep an available copy away from this device” is guidance, not a cloud-status claim. |
| Incomplete recovery copy · UI-34/35 | Persistent amber **Incomplete recovery copy — 1 file missing** plus manifest. | Explicit branch acknowledgement and manifest review. Never the same success badge or last-full-backup status. |
| Restore accepted · UI-35 | **Restored on this device**; source snapshot, counts, reminders off and missing-file warning if applicable. | **Open restored data**, **Review reminder settings**. No promise to undo after acceptance, merge datasets or update another device. |
| A guard changes while review is open · UI-24/26/43/57 | **Review changed information** with the affected row and before/now values. | Keep user input; require fresh review of affected consequences. Do not change the selected machine, fulfilled plan or task silently. |

Snackbars acknowledge noncritical completed actions such as **Customer saved** or **Filter cleared**. A snackbar is never the sole place for a save failure, replacement warning, missing attachment, important validation, or unverified backup. No promised Undo exists for finalization, sent files, restore, erase or an operation without a real source-supported inverse. Reopening an editor is not undoing an external effect.

## H2. Consequential phrasebook

| Context | Use | Avoid / reason |
|---|---|---|
| Stop working for now | **Leave visit for later**; “Saved progress stays in this working visit.” | “Cancel” when the visit is already autosaved. |
| Discard an ad-hoc working draft | **Discard working visit**; name the draft, lost notes/photos/proposals and unchanged obligations. | “Delete job” with unclear history/date effects. |
| Status answer | **OK**, **Issue found**, **Not applicable**, **Not checked**. | A universal checkmark that erases unperformed/unknown distinctions. |
| Review checklist | **Mark checklist reviewed**. | “Complete service” at the checklist boundary. |
| Work result | **Performed**, **Partly performed**, **Not performed**. | “Done” applied to all machines. |
| Recurring consequence | **Fulfill this service obligation**; “Next due: 1 Sep → 5 Dec 2026.” | “Schedule next visit”: an obligation is not an appointment. |
| History-only record | **History only — current due date will not change**. Include the actual eligibility reason. | An enabled-looking fulfillment control with no explanation. |
| Machine condition | **Issue observed**, **No issue observed in recorded work**, **Not assessed**, as supported by the selected source. | “Safe”, “Certified”, “Passed inspection” as an automatic conclusion. |
| New corrective task | **Create after finalization** / **Planned follow-up** while staged. | “Task created” before the record commits. |
| Linked task | **Keep open** is the default; **Resolve this follow-up** is explicit. | Closing every task attached to the visit. |
| Private note | **Private — not in customer report**. | A color-only lock or “Notes” whose recipient is ambiguous. |
| Handoff | **Share PDF** / **Record handoff note**. | “Send and complete”, “Customer received report” inferred from a share sheet. |
| Report correction | **Create correction** / **Commit correction**; previous revisions remain. | “Edit original PDF”. |
| Backup | **Create full backup**, **Verify saved file**, **Full backup verified**. | “Synced”, “Cloud safe”, “Backed up” after only file selection. |
| Restore | **Replace local dataset**; “Current records on this device will be replaced by the selected snapshot.” | “Import backup” implying a merge. |
| Encryption | **No passphrase reset is available. Keep it separately from the backup.** | “Secure forever” or a hidden recovery mechanism. |
| Erase | **Erase local data**; “Files already exported or shared are not erased.” | “Delete everywhere”. |

Names and identifiers are inserted as text, not trusted markup. Error text gives a correction: **Enter a number greater than 0**, **Choose a date after 5 Sep 2026**, **This site name is already used for this customer**. Never reveal a passphrase in a validation message, diagnostic summary, clipboard helper or recovered-input preview.

## H3. Reading order, focus and nonvisual operation

**Common order:** screen title and context → important blocking state → scoped content in visual order → action region → root navigation when present. A saved-status label is part of the working header, not repeatedly focused or spoken on every write. Use native semantic roles for buttons, switches, radio groups, tabs and checkboxes. Decorative equipment silhouettes/status icons are ignored where adjacent text already carries their meaning. A read-only historical value is announced as a value, not a disabled editable field. Android semantic mechanisms support these distinctions; their correct behavior must be tested with TalkBack (Q-01, Q-07, Q-12).

A due row's single navigation target can announce **“Condition inspection, Treadmill 01, window side, EQ-001. Overdue, due 1 September 2026. Working visit V-001.”** Its separately focusable booking link says **Open working visit V-001**. Do not merge a row so aggressively that this independent action disappears, or duplicate the entire row once per child label. Selection mode exposes checkbox state and identity; the bottom action says **Book 4 selected services at Riverside Centre**. Selected items hidden by filters remain counted with **Review selection**.

An inspection item is a heading/label group with required state, response type and current answer. **Belt condition, required. Issue found, selected. View linked finding** is distinguishable from **Displayed distance, required, numeric reading, kilometres, 1240.5**. A radio response uses selected state, not just a tinted outline. Required unanswered items remain discoverable in **Review remaining items**, not only in red borders. Optional unanswered values stay blank and identifiable, never read as OK.

On Save validation, show a persistent summary **3 fields need attention** with individually focusable links. Announce it once, then move focus to the first invalid field only after deliberate submit, not during ordinary typing. Preserve all entered values and scroll the field and its error above the IME. Inline errors clear after correction/revalidation; input in one field must not continually announce unrelated errors (Q-10, Q-11). On dialog/sheet close, return focus to the invoking control; after removal, focus the next existing item or the parent heading. After an external action, restore the originating machine and control, not Home.

Keyboard/DPAD Tab traversal follows reading order, includes overflow and all text-labelled alternatives, and shows a persistent ≥2dp high-contrast focus outline with separation from the component edge. That dimension is a design recommendation informed by focus guidance, not a claimed native certification. No focused control is wholly obscured by a sticky bar, keyboard or sheet. Voice access benefits from visible names matching accessible names; no essential action is available only by long press, swipe, drag or pinch. Reordering has **Move up/Move down**; pages and photos have explicit navigation/zoom controls. Tooltips are supplementary, never the only label.

## H4. Window, text, keyboard and locale behavior

**Window size is available space, not a device model.** At 360×800dp, the catalogue's initial views apply after subtracting actual system insets; they are not a promise of an exact visible row count. At 320dp or a short landscape/multi-window height, titles wrap, chips become stacked rows, and consequence columns become label/value blocks. At 200% text/display scaling, allow multi-line actions and taller rows; primary actions move into a scrollable action region when a fixed bar would consume useful editing space. Do not scale the whole screen down, truncate machine identity into indistinguishable titles or shrink body type to preserve a mockup (Q-03, Q-05).

**Wider windows:** 600–839dp may use a navigation rail and wider single content column; at ≥840dp, use a bounded list–detail composition when both panes remain readable. These are proposed layout thresholds, not SDK locks. Selection retains exactly one active context. Back first closes an editor/temporary detail; it does not unpredictably select a different machine because two panes are visible. A fold/hinge/cutout is not a content column. Switching width preserves the draft, selected record, open section, filters and scroll checkpoint; rotation does not finalize, discard or restart a file operation. Test multi-window resize during numeric input, photo return and restore progress (Q-05).

**Insets:** draw background edge-to-edge where supported, but pad all interactive/essential content by actual system bars, gesture/navigation and keyboard insets. Three-button navigation and gesture navigation have different bottom needs. A fixed completion bar sits above those insets, never under the keyboard. Do not hard-code the height of a status bar or assume that hiding chrome makes it safe to ignore it (Q-04). When the IME opens in an ordinary editor, hide nonessential root navigation and photo decoration, not the field label/error or Save. A toolbar Save can remain while the lower action is out of view; both must perform the same single commit, not two distinct operations.

**Input:** names use text capitalization where useful; phone/email use corresponding keyboards; identifiers never auto-capitalize, autocorrect or remove leading zeros without a source rule. Narrative fields permit paragraphs; keyboard Done dismisses the keyboard, not finalizes work or commits a correction. Next moves to the next applicable visible input and skips collapsed optional groups. Numeric readings accept locale-appropriate decimal input and an explicit sign where permitted; retain editable intermediate states such as `-` or `1,` until validation. Do not convert an identifier into a number. Quantity must be positive; units remain visible outside the numeric placeholder. Date controls offer calendar and text entry where supported and expose complete localized dates to assistive technology.

**Localization preparation:** all visible strings, plurals and inserted dates/numbers use locale resources. Allow at least a +40% text expansion stress case, but support wrapping beyond that rather than asserting a maximum. Use day/month names or unambiguous localized formats on screen, full dates in confirmation and report headers, and business-time-zone labels for appointment times. A 12/24-hour preference follows the platform where appropriate; the selected business zone remains explicit. No new app-language setting or launch-language choice is added. CSV remains the specified portable machine format, not a locale-dependent spreadsheet variant. Preserve original names and addresses, script shaping, bidirectional isolation around serials/IDs/dates, and logical start/end alignment; do not mirror a logo, equipment photo or numeral string. Use pseudolocales and an RTL stress locale in implementation testing without claiming those are launch markets (Q-15).

**Motion, sound and haptics:** optional short pressed/reorder/transition feedback never delays the first usable frame, determines completion or carries unique information. Respect disabled platform animations; replace movement with immediate state changes and retain progress text. Standard event haptics may supplement selection/commit feedback only when allowed by user/platform settings. No vibration for every checklist field; no required sound, flashing alarm, pulsating overdue card or confetti. A fallback with no animation, vibration or sound retains every meaning (Q-13, Q-14).

## H5. Automatic behavior visibility crosswalk

These are source-triggered responsibilities, not extra background features. Every automatic action has a visible explanation/recovery path. C is the baseline; F alternatives remain qualified in J and the action appendix.

| C behavior | Visible owner / trigger response |
|---|---|
| C:E01 Date/mutation/list refresh | UI-02/03/10/13 recompute current badges, eligibility and counts after saved changes, day rollover or return; failed reads never become zero. |
| C:E02 Eligible reminders | UI-32/59 generic deep links use current records, suppress stale/duplicate/past-start alerts; approximate timing, no missed-alert storm. |
| C:E03 Autosave and recovery | UI-20/21/22/26/41/58 keep saved checkpoint and pending reviews; unsaved changes after it can be lost. |
| C:E04 Returned camera/picker result | UI-44/59 restore the original operation target across recreation. A deleted draft or replaced dataset invalidates the stale result; never attach to the currently visible machine. |
| C:E05 Template revisions | UI-16/22 show captured revision; archived assigned template remains frozen/labelled, with no silent empty checklist or historical rewrite. |
| C:E06 Finalization/correction/PDF retry | UI-24/25/26/39 separate the once-only record/date/task transition from rendition generation; privacy remains snapshot-based. |
| C:E07 Clock/zone/DST changes | UI-18/19/31/32/46 show business zone and timed appointment differences; date-only obligations stay dates. Ambiguous/nonexistent entered times need a visible choice. |
| C:E08 Reboot/update/process restart | UI-02/20/32/59 recover durable drafts and permitted reminders after protected data availability; no before-unlock or force-stop guarantee. |
| C:E09 External permission/channel change | UI-02/32/50 recheck actual availability; records still work, without prompt loops or battery-exemption demands. |
| C:E10 Interrupted background file operation | UI-34/35/36/37/51 revalidate stage/output and show completed, failed or interrupted. Preserve prior good files/dataset; disclose possible incomplete provider file. |
| C:E11 Restore/reset accepted | UI-35/52/59 invalidate stale callbacks/deep links, rebuild counts, keep restored reminder preferences but delivery Off pending review; not multi-device sync. |
| C:E12 Changes after backup snapshot | UI-02/33 report Changed since backup against snapshot coverage, not slow-write finish time. Other-business verification/incomplete copies do not satisfy current full-backup status. |
| C:E13 Missing media/storage exhaustion | UI-33/44/58 name affected evidence and safe recovery; block false PDF/backup success. A readable-store incomplete copy is not arbitrary database repair. |
| C:E14 Temporary-file cleanup | UI-51/58 explain only abandoned/unreferenced staging cleanup. No silent purge of saved revisions, gallery originals or exported provider files. |
| C:E15 Data-format migration | UI-33/35/58 use a limited recoverability screen on migration/open failure, never an empty replacement dataset. Older/newer format support must be tested. |

### F automatic-behavior correspondence

| Source | Visible equivalent / explicit policy difference |
|---|---|
| F:E01 | C:E01; UI-02/03 refresh actual saved due state. |
| F:E02 | C:E01/C:E06; affected live lists update, UI-25/39 historical snapshots do not. |
| F:E03 | C:E03; UI-41/58. X-07 retains C ordinary recovery buffers, not F's narrower form promise. |
| F:E04 | C:E04/C:E13; UI-44/59 copy result stays attached to original operation identity. |
| F:E05 | C:E03/C:E08; Home Resume/UI-20/UI-26 restore only durable checkpoints. |
| F:E06 | C:E05; UI-22. X-06: C uses an archived assigned frozen template; F replacement/omit branch is inactive. |
| F:E07 | C:E06; UI-24/25 separate finalization and PDF generation. X-05 fulfillment conditions remain C. |
| F:E08 | C:E02; UI-32/59 current generic daily summary, no replay storm. |
| F:E09 | C:E02; UI-19/59 revalidate current appointment; X-09 C default lead remains two hours. |
| F:E10 | C:E09; UI-32/50 actual permission state, business records unchanged. |
| F:E11 | C:E07; UI-18/19/31/32 business-zone/date distinction; ambiguous input is reviewed visibly. |
| F:E12 | C:E08; UI-02/20/32 lifecycle recovery; no before-unlock/force-stop delivery guarantee. |
| F:E13 | C:E12; UI-02/33 full-backup age/changed status. X-09 F seven-day snooze is not active. |
| F:E14 | C:E10/C:E11/C:E14; UI-35/51/58 restore, failed staging and safe temporary cleanup; no deletion of retained evidence. |

The row labels above are descriptive summaries; the original C:E identifiers retain their exact source meaning. J's final audit checks the source headings and any additional trigger details; it does not replace them with a newly numbered automation specification.

<a id="I"></a>

# I. Quality verification and validation plan

## I1. Evidence status and official guidance checked

**Checked for this UI/UX continuation: 5 September 2026.** The date is not inherited from the app maps. Official Android, Material and W3C pages were consulted for the material UI requirements below. Android examples in Compose or Views demonstrate feasible platform routes, not an approved ServiceLoop framework choice. W3C criteria guide measurable design checks; they do not automatically certify a native app or the generated report.

| Ref | Official source / inspected point | Translation into this proposal; evidence limit |
|---|---|---|
| Q-01 | [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps). 48dp touch targets, text contrast guidance and meaningful control semantics. | Minimum 48×48dp interactive footprint; larger field controls where useful. Text role pairs are tested at 4.5:1, even where large-text guidance could allow less. Semantic labels are specific to a machine/item. These are documented guidelines, not observed ServiceLoop results. |
| Q-02 | [Material 3 — Structure](https://m3.material.io/foundations/designing/structure). The current official indexed text recommends considering at least 48×48dp touch targets. | Confirms the baseline control vocabulary's target scale. **Access limit:** the full article rendered only a JavaScript-required message in the browsing tool; the indexed official passage was available. No claim of a full Material article inspection. The 8dp gap, 56dp common row/control size and chosen tokens are this design's recommendations. |
| Q-03 | [Android 14 — Nonlinear font scaling](https://developer.android.com/about/versions/14/features#non-linear-font-scaling). Up to 200% scaling and nonlinear text scaling. | Test maximum font scale and use scalable text/line heights, not a scalar applied to every container. Version is context for the guidance, not a ServiceLoop minimum SDK decision. |
| Q-04 | [Android — Window insets](https://developer.android.com/develop/ui/compose/system/insets). System/IME/cutout inset handling. | H4's edge-to-edge backgrounds and inset-safe actions require actual window measurements. Test gesture, three-button, keyboard and resizing cases. |
| Q-05 | [Android — Adaptive ready quality](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-3). Usable layouts and state continuity across configurations. | No orientation lock as a layout workaround. Preserve ongoing work on resize/rotation; do not claim an adaptive tier has been achieved without running its tests. |
| Q-06 | [Android — Predictive Back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture). Supported native Back integration. | Use native navigation/predictive behavior where available; intercept only a genuine unsaved/destructive boundary, not harmless root switching. Device behavior remains to test. |
| Q-07 | [Android — Accessibility semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics). Roles, headings, states, errors, collections and live regions. | Native semantic tree, succinct error/live-state announcements and separately reachable nested actions. Equivalent supported Views routes are acceptable; custom merging needs manual testing. |
| Q-08 | [W3C — Contrast minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html). Relative luminance and text thresholds. | Numerical token-pair check in D, with no rounding-up to pass. Native-app test targets use this metric; no blanket WCAG certification. |
| Q-09 | [W3C — Non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html). Identifiable controls/states against adjacent colors. | 3:1 check for interactive outlines/focus roles; also text/shape state cues. Decorative separators are not all treated as control boundaries. |
| Q-10 | [W3C — Error identification](https://www.w3.org/WAI/WCAG22/Understanding/error-identification.html). Errors identified in text. | Persistent field errors and linked summaries with correction guidance. Never rely only on red borders. |
| Q-11 | [W3C — Status messages](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html). Status information perceivable without needless focus shifts. | Polite updates for meaningful results; urgent save failure persists and is announced. The native semantic mechanism, not web ARIA pasted into app architecture, implements this intent. |
| Q-12 | [Android — Test app accessibility](https://developer.android.com/guide/topics/ui/accessibility/testing). Manual assistive-technology, analysis, automated and user testing complement one another. | Run TalkBack/Switch Access/keyboard tests and automated checks; a scanner pass alone cannot establish usability. No such app tests have been run here. |
| Q-13 | [Android — ValueAnimator.areAnimatorsEnabled](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled()). Platform animations can be disabled. | Zero-motion fallback and no dependency on animation callbacks to communicate business success. Framework-specific handling must be verified during implementation. |
| Q-14 | [Android — Haptic feedback](https://developer.android.com/develop/ui/views/haptics/haptic-feedback). Standard event feedback and platform/user constraints. | Optional, nonessential haptics only. Respect unavailable/disabled feedback rather than forcing vibration. |
| Q-15 | [Android — Pseudolocales](https://developer.android.com/guide/topics/resources/pseudolocales). Localization testing tools. | Expanded strings and RTL stress testing are release checks, not new launch-language commitments. |
| Q-16 | [Android — Shared documents](https://developer.android.com/training/data-storage/shared/documents-files). User-mediated document-provider access. | UI-59 is OS-owned. Chosen destination/provider availability, cancellation and verification must be handled; selecting a cloud provider is not proof of remote availability. |
| Q-17 | [Android — Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker). Selection-based media access. | User-chosen photos, then durable local intake under C's attachment contract; unavailable/cancelled picker handled without broad-gallery access assumptions. |
| Q-18 | [Android — Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission). Runtime permission and denial behavior for ordinary notifications. | UI-50 explains permission contextually; UI-32 remains truthful when blocked. The product's approximate timing and no-force-stop guarantee remain source limitations, not claims of tested delivery. |
| Q-19 | [Android — Common intents](https://developer.android.com/guide/components/intents-common). Dialer, composer, maps and selected-contact routes. | User-controlled external handoffs with a missing-handler fallback. Neither intent launch nor return proves the real-world action occurred. |
| Q-20 | [Android — PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer). Platform PDF rendering route. | Built-in PDF preview is feasible; structured text is a separate accessible projection. Renderer capability alone does not certify a PDF's tags or reading order. |
| Q-21 | [W3C — Focus not obscured](https://www.w3.org/WAI/WCAG22/Understanding/focus-not-obscured-minimum.html). Focus visibility under overlapping content. | Test sticky bars, IME and dialogs with keyboard/assistive focus. Applies here as a design/test principle, not a native conformance declaration. |

No competitor pages, paid research services or external analytics are dependencies of this proposal. Platform source access does not establish Play approval, security of an encryption implementation, guaranteed notifications, universal hardware support or a tested PDF export.

## I2. What was actually checked, and what was not

**Performed in this design work:** source-family inventory and edition comparison; source conflict analysis; an individually enumerated placement/disposition for all 732 source actions; 59-surface catalogue consistency; fixture date/count arithmetic; numerical contrast calculations for 30 specified role pairs; structural/link checks on the generated reading editions. The accompanying delivery audit records the exact mechanical checks and file hashes. Textual wireframes are layout specifications, not running Android screens.

**Not performed:** interviews, target-trade validation, measured task times, prototype usability trials, Android compilation, on-device TalkBack/Switch Access testing, actual permission or camera flows, low-storage/power-loss recovery tests, encryption review, PDF generation/print testing, performance profiling or Play submission. Those are explicit release gates, not disguised as accomplished work. The future tests below have observable outcomes but no claimed pass result.

## I3. Feasible native routes and costs of customization

| Proposed surface / behavior | Defensible route and user-visible limitation | Maintenance/accessibility cost and simpler fallback |
|---|---|---|
| Root navigation, tabs, ordinary forms and lists | Standard native Material components with the proposed tokens, scroll containers and window-aware navigation. | Low customization. A plain single-column list remains the fallback when a split view does not fit. Never fabricate a desktop dispatcher layout. |
| Context-rich selectable service row | Standard list item, independent checkbox and labelled linked-visit action with nonoverlapping targets. | Moderate semantic grouping/testing cost. Fallback: checkbox row plus a separate “Open visit” line, not an invisible nested target. |
| Status responses and numeric inspection | Native single-selection controls and persistent text fields, grouped by item; keyboard and text-entry date alternatives. | Moderate because four status meanings and dependent required reasons must stay coherent. Fallback: vertically stacked radio choices instead of cramped segments. |
| Sticky machine context / completion action region | Ordinary layout/inset APIs; one bounded context strip, content scroll and adaptively placed action region. | Must test tiny/IME windows and focus. Fallback: in-flow context/action blocks; no sticky region may hide errors or identity. |
| Reorder template items | Native list with visible Move up/Move down and state announcements. Optional drag is supplementary. | Avoid a bespoke gesture system. Two buttons are the complete fallback. |
| Photo/PDF viewer | Supported image/PDF rendering plus native scroll and explicit page/zoom controls; system camera/picker/share outside the app. | Rendering, memory limits and accessible navigation require testing. Fallback within CORE: text mode for report reading; a rendering error is not an invented successful preview. |
| File operations and replacement restore | User-selected document providers, staged inspection/progress/result surfaces and source-defined atomic replacement. | High integrity/security testing cost. Do not downgrade verification or hide incomplete copies to make the interface easier. Unsupported input is rejected before current data changes. |
| Passphrase protection | UI reflects C's protected portable package policy, not a cryptographic implementation selected here. | Security and recovery review required. Rejecting encryption is an owner-level X-01 scope choice with a different warning/unlock UI, not an implementer's silent fallback. |
| Focus/live save state | Supported semantic error/state/live-region facilities; explicit restoration at navigation boundaries. | Manual assistive-technology testing required. Fallback is an ordinary persistent state label with clear focusable recovery, not repeated modal errors or keystroke announcements. |

## I4. Conceptual stress review — hypothetical test loads, not product limits

| Test case | Expected behavior / likely failure to investigate | Current status |
|---|---|---|
| Empty / 3 customers / 500 customers, 5,000 machines | Zero state, compact familiar directory, then indexed/scoped search and position restoration. Avoid full-directory expansion and hundreds of visible nested cards. | Heuristic design checked; no performance result. These counts are hypothetical, not evidence of the market or new limits. |
| 30 machines and 90 selected services at one site | Working overview has compact group summaries and next incomplete entry; completion review remains individually auditable, no “fulfill all” shortcut. | Risk: long review and repeated identity. Prototype with search/jump within the visible group structure; do not add an unapproved bulk completion. |
| Two machines with identical names; missing serial/photo | Generated EQ references, make/model, site and position labels remain visible. Confirmation never silently chooses the first search result. | Test mistaken selection and recovery before any evidence is entered. |
| Long customer/site, 40% expansion, RTL, 200% font | Wrapped context, stacked outcomes/buttons, no clipped mandatory label/error, readable identifiers and no overlapping targets. | Layout proposal only; render/device tests required. |
| IME in 320dp short window; three-button navigation | Focused value+unit+error and Save are reachable; Back first dismisses IME. No accidental finalization on keyboard Done. | High-risk implementation test. |
| Return after six weeks | Home prioritizes actual unfinished visit, labels its old date, recomputes due status; no automatic date rollover or completion. | Date logic described; device/clock testing pending. |
| Permission denied / app force-stopped | Lists still accurate after return; reminder settings explain availability; no claim that missed notifications were sent. | Source limitation retained, not tested. |
| Cancel camera, share, contact picker or file provider | Originating context restored, previously saved content intact, no false handoff outcome. | OS integration test pending. |
| One missing photo / missing issued PDF / corrupt package | Named missing item and appropriate recovery; no substitution. Invalid restore never replaces current data. PDF retry never re-finalizes. | Integrity fault-injection testing required. |
| Storage exhausted during text write, photo copy, PDF write or import | Critical failure persistent, last durable checkpoint truthful, no half-import or favorable completion. FC-02 rescue does not promise recovery of already lost bytes. | Highest-risk resilience test, not passed here. |
| Interrupted encrypted backup / cancelled provider | Written/unverified/incomplete states distinct. Passphrase not retained in a draft or diagnostics. | Security and provider testing pending. |
| Mixed-result visit needing return | Two outstanding obligations remain visible; return visit excludes fulfilled plans unless deliberately added for separate work. Existing tasks remain explicit. | Fixture walkthrough checked, usability test pending. |
| Later fulfillment then correction of older visit | Schedule effect not replayed; current date ownership explained. Wrong-subject error uses void/recreate. | Guard design checked; transactional testing pending. |
| One-page and 12-page customer report | Fixed readable type, continuation context, selected evidence legible, grayscale distinction; no forced one-page shrinking. | Layout rules written, generated-PDF/print test pending. |

## I5. Focused usability-validation plan

Use an interactive prototype followed by a real-device pilot only after the owner accepts the functional baseline. Recruit people who actually perform independent equipment servicing, including varied eyesight/dexterity/assistive-technology use when feasible; do not invent their biographies or assume the fictional trade is the launch trade. A small initial round (proposed 5–8 participants) is diagnostic, not statistically representative. Record observations with consent; no paid recruitment or analytics product is implied.

| Task given without UI instructions | Observable success | High-risk failure / design response |
|---|---|---|
| Add a customer and similar machine; make its first future obligation | Correct customer/site/machine identified; plan appears with the chosen date; optional identity fields do not block. | Mistaking site for customer or due date for booking: revise field/context grouping before adding onboarding text. |
| Customer postpones the booked work | Appointment changes, overdue date stays; participant can explain both. | Says “it is no longer overdue” after rescheduling: strengthen dual-date presentation. |
| Inspect T-01, find belt damage, record a reading and a photo, then leave | Correct machine evidence; issue and task disposition retained; resumes durable checkpoint without believing service finalized. | Photo linked to T-02 or “Reviewed” interpreted as fault-free: change context/wording. |
| Finish the fixture's mixed outcomes | Only P-001/P-003 fulfilled, P-002/P-004 remain due, FU-002 staged then created, FU-001 deliberately retained. | Any unearned advancement or hidden unperformed work is a critical failure, not a minor learnability issue. |
| Give the customer a report after simulated PDF failure | Participant understands record saved; retries generation, reviews privacy, records only known handoff. | Repeats finalization, assumes share equals receipt, exposes private access details: block release and revise. |
| Correct the service date after a later valid service exists | Participant can identify which revision changes and why current due date does not automatically roll back. | Treats old record editing as rewriting all history: improve before/after and guard copy. |
| Recover on another phone using the selected snapshot | Identifies replacement, passphrase need, snapshot date and reminders-off result; chooses safety backup consciously. | Expects merge/sync or thinks unverified file is safe: revise stages and success screen. |
| Complete the above with TalkBack or large text as applicable | Every essential control reachable, identity and error semantics understandable, focus restored. | Missing controls or trapped focus are blockers even if a screenshot looks good. |

Collect task outcome, wrong-record selections, misunderstood states, abandoned steps, unprompted recovery attempts, required moderator help and subjective confidence separately. Time/navigation counts are observations only when actually measured; compare them with F11's estimates afterward. Prioritize correctness and recoverability before reducing a necessary confirmation. Revise and retest the highest-risk misunderstandings; do not average a catastrophic data-loss misconception into a favorable overall score.

<a id="J"></a>

# J. Full action coverage, policy detail and proposed changes

## J1. What constitutes coverage

The [individual-action appendix](ServiceLoop_UI_UX_Action_Coverage_v0_1.md) contains one row for **each of 357 C actions and 375 F actions**, including F's embedded finding/part editors. Each row identifies the original action, the exact visible location/control, applicable state, shared pattern, and whether its semantics are retained, relocated, explicitly proposed, or conflict with the provisional baseline. Source result/cancel/error contracts are retained alongside the placement so that a short label cannot conceal a behavior difference.

Every C:S01–S40 destination, C:D01–D13 shared surface, F:S01–S38 destination, F:U01–U08 shared surface and F:S19-D01/D02 embedded editor has a representation or explicit alternative disposition. The original identifiers are not renumbered. C and F actions with equal numbers are never joined merely by number. UI-54–UI-59 are explicit shared decompositions rather than new subsystems. The catalogue crosswalk in C and the appendix's parent summaries give both entry and control traceability.

**Presentation decisions retained:** three roots; task-prioritized Home; tabs within Work; direct scoped entry to machine/service; full editors for long input/consequences; compact short decisions; per-machine grouping; native pickers/handoffs; separately styled urgency, progress, history and errors; accessible text alternative to report rendering. These change where/how the existing task is presented, not whether it saves or advances an obligation.

**Functional proposals remain only FC-01, FC-02 and FC-03 in A3.** Source conflicts X-01–X-11 are owner-review boundaries. The appendix's “not active” F actions are not missing implementation tasks or hidden alternative menu items: they are deliberately not part of the C-based recommendation. A future adoption of F must update the affected policy and UI together.

<a id="J2"></a>

## J2. Exact input, import, export and draft-policy differences

### J2.1 C working input contract

Names/titles allow 120 characters; short answers 500; narrative notes 4,000; identifiers 120; numeric units 32. Show counters near limits, never truncate stored input silently. Trim only where the source defines normalization; preserve meaningful narrative line breaks and identifier leading zeros. Text fields are validated on deliberate Save/review and relevant blur, not by destructive input filtering. C does not specify a separate address-length cap: that remains an owner-level validation detail, not permission to import F's 1,000-character cap silently. Layouts wrap long addresses regardless of the eventual limit.

Site names must be unique within a customer. Active/paused plan names cannot duplicate on one machine; ended plan names can be reused. Active template names must be unique. Equipment identifier collisions show a possible-duplicate review, with an explicit separate-machine route; they are not an F-style hard unique internal-ID rule. Generated app references distinguish records when optional identifiers are absent. Template items default to Status and Required response on; numeric items require a unit. A template needs at least one item. Part quantity is positive and supports up to three decimals; unit is required, default **piece**. Numeric readings may be signed but must be finite; no arbitrary pass/fail threshold is invented.

New plan defaults: 6 Months, next due date blank/required, optional prior-service date not future, next due later than known prior date. Once a counted fulfillment exists, the previous-service reference is not a freely editable master field. Suggesting a date does not silently accept it. A one-off work editor starts with **One-off service**, then lets the user give it a useful name; it creates no recurring plan. A new timed booking defaults to 60 minutes; a date-only arrangement remains valid. No cross-midnight duration or new booking in the past under C; use Record past for actual historical work. Overlap warnings know only the app's relevant timed bookings, not the technician's external calendar.

Photo caps from C remain explicit: up to 20 visit photos per machine and 100 per visit; one master equipment photo and one business logo. Intake can optimize the long edge to at most 2,560 pixels without upscaling; retained orientation/caption and source-defined metadata treatment remain visible in privacy help. These are source limits, not recommendations to fill every visit with 100 pictures. A protected backup passphrase uses the C minimum of 12 characters, accepts spaces, requires confirmation for creation, and has no recovery/reset promise. It is not saved to an ordinary-form recovery buffer.

Ordinary editors use explicit Save plus recoverable input buffers; working/correction drafts autosave; child editors are staged until their parent accepts them. A backup includes the durable ordinary-editor recovery buffers **as buffers**, not as committed customer/plan edits. Unpersisted keystrokes cannot be promised in any backup. Discarding an ad-hoc working draft removes it; discarding a booking-origin working visit retains the cancelled booking shell. Final records and report versions are not individually deleted.

**X-11 — Finding/task description ambiguity inside C.** C:S23 describes a new corrective task as requiring title, date and description; C:S28 defines title/date/context and optional internal notes rather than a second required public description field. Working interpretation: the linked, required public finding description provides the problem description, while the task gets its required title/date/context and optional private notes. UI-23 shows the finding above **Create after finalization**; UI-28 shows its source. No new required private note or separate public task-description field is invented. Owner confirmation is needed before implementation; an alternative must explicitly specify visibility and report behavior.

### J2.2 C import: one fixed schema, no spreadsheet editor

UI-37 saves a blank file and fictional worked example, with these **22 exact headers** (order may vary):

```text
customer_ref,customer_name,customer_contact_name,customer_phone,customer_email,customer_notes_internal,
site_ref,site_name,site_address,site_use_customer_contact,site_contact_name,site_phone,site_email,site_access_notes_internal,
equipment_ref,equipment_name,equipment_type,make,model,serial_number,internal_identifier,equipment_notes_internal
```

The file is UTF-8, optional BOM, comma-separated with conventional quoted fields; exact headers, no extra columns. The UI explains that a spreadsheet may save a different delimiter and that the validation report—not in-app cell editing—is the repair route. A batch is capped at 20 MiB and 10,000 rows. Reference keys use 1–64 letters/digits/hyphens/underscores and are unique per entity type. A customer reference/name is required on every row; site reference/name is required where site/equipment data is supplied; equipment reference/name must occur together. Repeated parent references must supply consistent normalized fields. `site_use_customer_contact` is true/false, blank true; custom contact data while true is an error rather than a silent override.

A customer-only row is permitted. For a new customer with no explicit site anywhere in the batch, create its default **Main site**. Otherwise the first listed site is default. Do not create a phantom second Main site before seeing later rows for that customer. All existing unchanged references must match the defined supplied fields/parent; same key with changed data is a hard conflict, not an update. Different keys with similar identity become a possible duplicate: **Open existing**, **Create separate record**, **Skip row/group** with dependent branch count, or correct the file. An existing matching entity is labelled **Existing unchanged**, not Imported twice.

UI-37 separates **Errors**, **Warnings**, **New**, **Existing unchanged** with counts; UI-53 resolves warnings and deliberate coherent skips. A skipped parent includes its dependent proposed rows; review shows exactly what will not be imported. Commit requires zero unresolved errors and reviewed warnings in the included set. The import is atomic: cancellation/failure before commit leaves the dataset unchanged. The final result gives new/reused/skipped counts, **View imported customers** and **Equipment without active plans**. No service plans, photos, visit history or PDF bytes are imported. Importing a directory does not create future obligations until plans are configured.

### J2.3 C export choices and privacy

UI-36 offers **Directory CSV (import-compatible)** or **Records archive (CSV ZIP)**. Scope is all customers or one customer. Inactive records default included; private fields default excluded; superseded revisions default excluded. Each option names its actual contents; private fields have a deliberate warning and selection before export. The record archive contains a field-guide/readme plus these 14 tables:

```text
customers.csv       sites.csv          equipment.csv      service_plans.csv
visits.csv          work_items.csv     checklist_answers.csv
findings.csv        parts.csv          followups.csv      contact_notes.csv
changes.csv         report_index.csv   attachment_index.csv
```

The index files list identities/status, not embedded images/PDFs. Directory CSV uses J2.2's fixed format. The records archive is readable portability, **not** accepted by the directory importer and **not** a full restore package. Values use source-defined portable dates/numbers; spreadsheet-formula-like text is escaped using the documented reversible convention, never evaluated. Export snapshots do not rewrite business data. Save/share leaves copies outside app control. `Private fields excluded` remains visible on result; private inclusion is not inferred from a previous share.

### J2.4 F alternatives — inactive under this recommendation

F's import uses **21 different headers**, 25 MiB, 10,000 rows, keys 1–80 characters allowing periods, and an initial-only empty-business-dataset restriction (business profile/templates may exist):

```text
customer_key,customer_name,contact_name,phone,email,customer_notes,
site_key,site_name,site_address,site_contact_name,site_phone,site_email,site_access_notes,
equipment_key,equipment_name,equipment_type,make,model,serial_number,internal_id,equipment_notes
```

F requires a site on every row, infers custom site contacts rather than using C's Boolean column, blocks repeated nonblank internal IDs, and assigns app identity with a retained source-key mapping. F does not offer C's deliberate row/branch skipping and repeat unchanged-key import. Its **Save import result** mapping file is an additional action not silently included in C. F's record export is all-records/private-inclusive and has a different file contract. Accepting F changes UI-36/37 eligibility, warnings, examples, validation and result actions, not merely a title.

F limits addresses to 1,000 characters, uses different uniqueness rules and an optional part unit defaulting to **pcs**. Its ordinary Save-and-leave and working-discard shell policy differ from C. Its archive/retire/move cascades, current-interval completion, contact/task mutations, immediate reminder settings, customer-name notification option and seven-day backup snooze are specifically marked X in the appendix. No disabled switches advertising them appear in the proposed app.

## J3. Coverage interpretation, source integrity and open owner decisions

The source files remain unchanged; final file hashes and counts are recorded in `ServiceLoop_UI_UX_Delivery_Audit_v0_1.json`. The 732-row appendix counts **placements or explicit dispositions**, not 732 separately visible buttons and not 732 approved product features. Many actions share a surface or a control whose label changes by state. All source IDs remain individually enumerable and searchable.

The deliberate owner-review decisions are: adopt C or revise a named policy; protected/incomplete recovery policy and security burden (X-01/02); repeat import/export privacy/validation contract (X-03/07); lifecycle/cross-owner date carry (X-04); fulfillment/recurrence and task-description ambiguity (X-05/11); contact/history/report policy (X-06/08/10); reminder defaults/persistence (X-09); and FC-01/02/03. Recommended defaults are the working choices in A, not implied approvals. Confirm a reachable pilot trade, the fixed report's adequacy and single-authoritative-device acceptance before implementation; no new research questionnaire is necessary to review this design.

This proposal is complete as a **conceptual UI/UX review package with explicit unresolved policy decisions**, not a claim that those decisions are settled or that the app exists. The action appendix, lifecycle matrix, source contracts and verification limits are part of that package. A designer can build faithful mockups without inventing navigation or controls; an implementer must still obtain baseline approval and perform the security, persistence, device and accessibility validation identified here.


## J4. Source journey traceability

These references preserve both original journey sets, not just the attractive daily path. F alternatives inside a journey remain subject to the X register; demonstrating C's path is not acceptance of incompatible F semantics.

| Source journey | Demonstration in this proposal / principal UI surfaces |
|---|---|
| C:J01 | F1; UI-01, UI-07, UI-09, UI-11, UI-14, UI-03. Customer/default site/machine/two distinct plans. |
| C:J02 | F2/F4; UI-03, UI-45, UI-29, UI-18, UI-19, UI-24. Contact and postponed appointment leave due dates intact. |
| C:J03 | F3/F4/F5; UI-20, UI-21, UI-22, UI-23, UI-24, UI-25. Mixed services, independent fulfillment and corrective return. |
| C:J04 | F3; UI-44, UI-58, UI-02, UI-20. Captured evidence, interruption, last durable checkpoint. |
| C:J05 | F6/F7/G; UI-17, UI-09, UI-25, UI-39. Changed master data never silently changes issued records. |
| C:J06 | F6; UI-18, UI-26, UI-57. History-only entry and guarded correction/date ownership. |
| C:J07 | F5/F10/H1; UI-50, UI-32, UI-45, UI-59, UI-39. Denied notifications, absent handlers, cancelled sharing. |
| C:J08 | F1/F9/J2; UI-37, UI-53, UI-34, UI-35, UI-51. Initial/repeat create-only import and protected full/incomplete recovery distinction. |
| F:J01 | F1; UI-07, UI-11, UI-14, UI-03. C source validation/defaults explicitly govern. |
| F:J02 | F2/F4; UI-45, UI-29, UI-18, UI-19, UI-24. X-08/10 contact/booking differences remain marked. |
| F:J03 | F3/F4; UI-21, UI-22, UI-23, UI-24. X-05/06 C reviewed/outcome/evidence semantics retained. |
| F:J04 | F3/H1; UI-44, UI-58, UI-20. X-07 C draft and removal policies retained. |
| F:J05 | F6/F7/G; UI-16, UI-17, UI-25, UI-39. X-06 archived-template difference does not rewrite history. |
| F:J06 | F6; UI-26, UI-57, UI-40. X-10 correction differences exposed beside actual before/after. |
| F:J07 | F5/F10; UI-32, UI-45, UI-59, UI-39. No external success inference. |
| F:J08 | F9/J2; UI-37, UI-34, UI-35. X-01/02/03 formats/security/eligibility not treated as compatible. |
| F:J09 | F8; UI-12, UI-43, UI-25, UI-40. C dependency/cross-owner carry policy, historical privacy, FC-03 correction guard. |
