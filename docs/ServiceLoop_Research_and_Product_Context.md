# ServiceLoop — Research and Product Context

**Version:** 0.1 · **Compiled:** 5 September 2026  
**Purpose:** Portable background for the ServiceLoop conceptual app-map task.  
**Status:** Research handover and proposed product direction; not an approved specification, build authorization, or commercial validation.  
**Companion task:** `ServiceLoop_Functional_App_Map_Prompt_v1_1.md`

## 1. Read this first

ServiceLoop is the working name for an Android-first service book for independent technicians maintaining **customers’ equipment**. The proposed value is the complete connection between a future service obligation, customer contact, the visit, its recorded outcome, a customer-ready report, and the next obligation.

> An offline service book for independent technicians: know which customer equipment needs attention, record the work, and leave the customer with a professional report.

Felix selected this direction for further exploration after reviewing a broader Android-opportunity study and a different AI model’s ServiceLoop proposal. He said he could personally relate to this problem, unlike most of the alternatives, and would like to do it. He explicitly raised the risk of complexity growing as he tries to make the app useful. **This does not establish that Felix operates an equipment-service business or that target customers have been interviewed.**

The immediate requested deliverable is a **conceptual app map covering everything within the proposed product boundary, screen by screen, button by button, and function by function**. Technical explanations should be brief and limited to defending feasibility and honest user-facing behavior. The current task is not to implement the app or restart the broad search for business ideas.

The important conclusion carried forward is **“viable enough to investigate and pilot,” not “proven to be a low-competition market.”** The later assistant assessment did not endorse the other model’s 88/100 score, its high willingness-to-pay claim, or its narrow-market competition rating.

## 2. Provenance, evidence status, and authority

### 2.1 What this handover is based on

| ID | Material available in the originating conversation | How it is used here |
|---|---|---|
| H1 | The user’s original Android-opportunity research brief | Research purpose and solo-development constraints. |
| H2 | The assistant’s 5 September 2026 Top 10 study and its `Android_opportunity_screening_2026-09-05.md` ledger | Earlier research conclusions, relevant neighboring opportunities, and source pointers. The unmodified ledger is included in the handover ZIP’s `research_archive` directory. |
| H3 | The other model’s **ServiceLoop #1 entry**, pasted by Felix | Its proposal, reasoning, scope, estimates, and score. The supplied entry is reproduced in full in Appendix A, with spacing normalized. Its full study, citations, and model identity were not supplied. |
| H4 | The assistant’s subsequent ServiceLoop viability assessment and bare-bone feature list | Competitive cautions, the narrower single-technician proposal, and differences from H3. |
| H5 | The generated `ServiceLoop_Functional_App_Map_Prompt.md` | Detailed task instructions and proposed CORE/LATER/OUT scope. The companion v1.1 adds a context-reading section without changing the functional task. |
| H6 | Felix’s visible messages selecting this direction and requesting the app map and this context handover | Explicit user intent, distinguished from assistant recommendations. |

This file **preserves earlier research; it does not report a new market-research pass**. External pages were not reopened during this handover compilation. The external source register preserves links cited in H2/H4 and explains what the earlier answers used them to support. Treat product capabilities, prices, platform documentation, and dated user reports as historical research leads until checked again when they materially affect a new decision.

The research ledger explicitly describes desk research of varying depth, not installed-app testing. A user complaint can establish that a person experienced a problem, but not how prevalent it is. A vendor’s advertised capability does not prove its usability. Conversely, omission from a vendor’s landing page does not prove the feature is absent. Agreement between two AI assessments is not independent market evidence, especially when they may rely on the same discussions. [H2–H4]

### 2.2 Status labels to preserve

**USER-STATED:** Felix’s expressed direction or request.  
**REPORTED EVIDENCE:** A finding from the earlier desk research, with a source pointer; not newly reverified here.  
**ANALYST JUDGMENT:** An interpretation, score, forecast, proposed price, or feasibility estimate.  
**PROPOSED BASELINE:** A suggested design choice used to make the app-map exercise concrete, not a user-approved feature commitment.  
**OPEN:** Requires further product review, source checking, or validation with technicians.

Use current explicit user instructions first. Respect explicit approvals when they actually exist. Otherwise use the companion prompt’s proposed baseline as the starting point for the design exercise, while keeping it labelled as a proposal. The historical other-model entry explains the opportunity; it must not silently override the later scope proposals.

Do not infer approval because Felix asked for a more detailed app map. Do not invent a finalized app name, launch trade, country, supported languages, pricing, SDK/toolchain, UI layout, backend decision for all future versions, or complete V1 specification. Do not rely on access to the old thread, account memory, or the Routine Repeater repository.

## 3. Original research brief and its conclusions

### 3.1 Research objective

The brief requested underserved, recurring, real-world problems appropriate to a focused native Android app that one capable developer could build largely through AI-assisted coding and maintain afterward. Routine Repeater supplied a scope reference: local persistence, several screens, polished UX, and manageable Android integrations, without a large backend, proprietary dataset, marketplace, or content operation. “Several weeks of intensive development” was the original investigation assumption, not a delivery promise for the later ServiceLoop scope. [H1]

The research was to start from user problems, investigate actual alternatives and complaints, and actively reject weak opportunities. The requested priorities were pain, competitive gap, Android fit, feasibility, discoverability, realistic monetization, and maintainability—not originality or an AI label. [H1]

### 3.2 Broad study result

The preserved ledger records **56 screened problem areas**. The final ranking was a list of research priorities, not ten validated businesses. The broad study initially recommended investigating Runbook first; ServiceLoop was proposed by the other model and selected by Felix afterward. Do not retrospectively describe ServiceLoop as the original study’s #1. [H2]

| Original rank | Concept | Original opportunity score /100 |
|---:|---|---:|
| 1 | Runbook — reusable checklists with durable run histories | 76.2 |
| 2 | Return Ledger — tracking returns until the money arrives | 73.9 |
| 3 | FreezerBatch — labelled physical-package inventory | 73.8 |
| 4 | PetCare Ledger — care and observation records | 71.2 |
| 5 | MixedAsset Service Ledger — maintenance records for heterogeneous business equipment | 68.9 |
| 6 | Candle Test Bench — formulation and test records | 66.2 |
| 7 | SoloRound Book — recurring customer rounds and money owed | 65.8 |
| 8 | SpoolShelf — standalone filament inventory | 63.8 |
| 9 | Maker Job Card — bespoke commission specifications | 60.2 |
| 10 | BinFinder — spatial retrieval of workshop parts | 57.3 |

The scores are analyst judgments, not probabilities; small differences are not statistically meaningful. The study explicitly withheld a current build recommendation for #9 and #10. Its central warning was that an attractive workflow and a technically small app can still face an excellent incumbent. Complaints about subscriptions or complexity alone were not treated as proof of an opening. [H2]

### 3.3 Findings directly relevant to ServiceLoop

**Equipment-service history — the closest original candidate.** MixedAsset Service Ledger addressed an owner-operator maintaining heterogeneous equipment: lost maintenance history, service dates, parts references, photographs, and date/usage counters. User discussions described machine notes and shared spreadsheets; the direct competitor check found LookOver already covering important small-engine maintenance functions. The surviving hypothesis concerned a narrower workflow for mixed equipment, not a generic maintenance-app gap. It was assessed at complexity 5/10, with the explicit risk that the actual users might need shared updates. [H2; E03–E04; C04]

**Customer equipment is a meaningful change of target.** ServiceLoop is not simply a new name for that candidate. It moves from “maintain the assets my business owns” to “retain and execute recurring service work on machines my customers own.” Customer/site relationships, contact and booking, multi-machine visits, and customer reports become central. Do not transfer the original 68.9 score or its complexity estimate to this different scope. [H3–H4]

**Reusable execution records — a useful design connection.** Runbook’s thesis was to keep a reusable template separate from each actual run, preserve partial or skipped work, and retain history when a template changes. That reasoning is relevant to an equipment inspection, but it is not a request to embed a full second checklist product inside ServiceLoop. The study acknowledged substantial checklist competition. [H2; E05; C07]

**Reporting alone did not survive the competitive screen.** Job-photo reporting and standalone field-inspection reports were screened out because tools such as Report and Run and Site Audit Pro already provided credible alternatives. The implication for ServiceLoop is that the customer report matters as the end of the recurring-service workflow, not as a supposedly novel PDF generator. [H2; C05–C06]

**Recurring customer work has broader competitors.** SoloRound Book explored a narrow operator ledger, but Squeegee and other current alternatives weakened an “offline but simpler” claim. This reinforces the need to compare the complete target workflow with existing systems rather than equating “not enterprise” with “unserved.” [H2; C08]

## 4. The other model’s ServiceLoop proposal

H3 describes a recurring equipment-service manager for one-person and very small maintenance businesses. Its core formulation is:

> Customer → site → equipment → service interval → visit checklist → signed/reportable history → next due date.

Its proposed users span gym equipment, water treatment, HVAC-adjacent work, coffee machines, fire safety, gates/doors, generators, workshop equipment, and pool equipment. Its recurring-service range is 3–24 months. These were examples and positioning assumptions, not a selected launch vertical or evidence that one form/report can serve all of them.

The proposal connects two kinds of pain: losing future service opportunities as the installed base grows, and assembling equipment-specific inspection records and customer-facing reports from fragmented tools. Its financial argument is that a missed service can mean lost revenue.

It rates competition low to moderate at the narrow end; estimates complexity at 6/10 and 1.2–1.5 times Routine Repeater; calls willingness to pay high; suggests €20–50 once or €3–6/month; assigns discoverability 8/10 and an overall score of 88/100. **All of these ratings and prices remain H3’s judgments, not verified project facts.**

H3 recommends excluding sales pipelines, dispatch, workforce management, payroll, accounting, route optimization, and enterprise asset hierarchy. Its V1 nevertheless includes date or runtime-based recurrence and broad calendar/contact/share integration; its workflow mentions signed/reportable history. It identifies horizontal over-generalization as the main risk and suggests beginning with a strong vertical.

Appendix A preserves the complete supplied proposal rather than rewriting its stronger claims to match the later assessment.

## 5. The later viability assessment: what was supported and what was challenged

### 5.1 Problem evidence reported in H4

The assistant reported locating an installer’s discussion about equipment due for maintenance every 6–12 months and a solo fitness-equipment technician’s older request for equipment-specific checklists, locations, serial numbers, findings, and reports. These were cited as evidence of the two connected problems, not proof that current alternatives fail every such business. [E01–E02]

The earlier assessment explicitly distinguished an older workflow request from evidence about the latest competitor versions. This handover does not supply independently checked post dates or repeat the assertion that both posts are recent.

### 5.2 Competitive counterevidence reported in H4

| Product | Earlier assessment’s reported capabilities or limitation | Implication carried forward |
|---|---|---|
| ServiceM8 | Customer asset records, equipment types, service history, forms and inspection follow-ups; Android positioned as ServiceM8 Lite rather than full iOS parity. A free solo plan was reported, with Asset Management not present in every plan. | Android workflow completeness could be relevant, but customer assets and forms are not new features. Do not assume every alternative is expensive or enterprise-only. [C01] |
| Workever | Customer-linked assets, job associations, history, next-visit dates, service reminders and due-date filtering. | A direct workflow comparator, not merely an adjacent CRM. [C02] |
| MaintainX | Recurring work orders, procedures and offline fieldwork; tier-dependent limits including on repeating work orders. | Recurrence, offline access and checklists are not individually sufficient differentiation. [C03] |

These are historical descriptions from H4, not fresh feature/price guarantees. Verify the relevant plan, Android availability, and actual interaction sequence before using a claimed gap as the basis for development.

### 5.3 Revised opportunity thesis

The assistant’s recommendation was not “nobody has solved it.” It was that **some solo technicians may prefer this complete workflow without adopting a larger field-service system, depending on a vendor cloud, or moving part of their work to a desktop**. The proposed advantage is a short, coherent Android workflow and user-owned records. Whether this beats existing products in practice is OPEN. [H4]

H4 recommended investigating ServiceLoop ahead of another generic utility in light of this direction and Felix’s interest. It did not issue a new scored ranking, guarantee commercial success, or claim that desk research replaces field validation.

### 5.4 Explicit differences between H3 and H4/H5

| Topic | Other-model proposal (H3) | Later proposed handling (H4/H5) |
|---|---|---|
| Overall confidence | 88/100 | Viable for investigation/pilot; no endorsement or replacement score. |
| Competition | Low to moderate at the narrow end | Direct competitors already cover much of the chain; test a specific workflow advantage. |
| Target operating model | One-person and small maintenance businesses | One technician, one business, one authoritative local dataset on one device for the initial product. |
| Complexity | 6/10; 1.2–1.5× Routine Repeater | Roughly 6–7/10 as a planning judgment; precise multiplier rejected as unsupported. |
| Recurrence | Date or runtime-based V1 | Date-based initial proposal; runtime/cycles and combined rules deferred. |
| Signatures/calendar | Signed/reportable workflow and calendar integration mentioned | A customer-ready report is core; acknowledgement/signature and calendar export deferred. Ordinary contact/share handoffs remain in scope. |
| Pricing | High willingness to pay; €20–50 once or €3–6/month | A trial and €39–49 one-time unlock suggested for testing, not a decision or established willingness to pay. |
| Interview kill criterion | More than 7/10 satisfied with alternatives or below roughly 50 assets | Direct comparison and observed behavior preferred; fewer than 50 assets is not itself a rejection because service value and reporting burden vary. |
| Launch focus | Potentially one strong vertical | Validate with one accessible trade while keeping the conceptual model reasonably general; no vertical selected. |
| Later sync | Optional sync mentioned | A separate product/architecture decision, not a small switch added to a local product. |

None of the later proposals becomes an explicit user approval by appearing in this table.

## 6. Current proposed product shape and why it exists

### 6.1 Planning boundary

**PROPOSED BASELINE:** One technician, one business, one authoritative local dataset on one device; core fieldwork available offline without a mandatory ServiceLoop account or backend. This restriction controls the first product’s scope; it is not evidence that every small service business will accept it. [H4–H5]

The proposed relationship is:

**Customer → site → equipment → service plan → visit → service record/report → next obligation.**

A customer may have multiple sites; a site multiple machines; a machine multiple service plans; a visit several machines and applicable plans at one site. These relationships prevent the common workflow from collapsing into a single machine/date note, but do not justify enterprise asset hierarchies. [H4–H5]

The three functions the later assessment would protect from scope cuts are **the due-work list, equipment service history, and the customer-ready report**. Without them, the proposal risks reverting to a reminder or generic form app. [H4]

### 6.2 Proposed functional backbone, not a second app-map specification

| Area | Purpose retained from the later proposal |
|---|---|
| Business profile | Identify the business/technician in reports without extensive setup. |
| Customers/sites | Associate equipment and visits with the right customer and physical location; make a default site easy. |
| Equipment | Identify the machine, retain its notes/photograph and history, and archive retirement without erasing past work. |
| Service plans | Keep distinct date-based obligations, their next dates and optional checklist templates separate from appointments. |
| Due-work dashboard | Bring overdue/due-soon obligations, booked or unfinished visits, and unresolved follow-ups into an actionable view. |
| Contact and booking | Connect due work to real customer contact and a visit without building a CRM or automated messaging system. |
| Inspection templates | Reuse status, text and numeric-reading items; record the inspection actually performed. |
| Working visit | Handle multiple machines, findings, photographs, work and parts notes; survive ordinary interruptions. |
| Completion/follow-up | Identify the obligations actually fulfilled, propose next dates, and retain unfinished work or unresolved findings. |
| History/report | Preserve finalized records and produce one practical, customer-ready PDF format with safe content selection. |
| Recovery/import/export | Keep the professional record portable and recoverable, including photographs and reports; support a limited, validated initial CSV import. |

These are H4/H5 recommendations. The companion prompt is the detailed task definition; it expands this backbone into first-use, settings, screen states, actions, automatic behavior, and edge cases without requiring this context file to duplicate every button.

### 6.3 Scope proposals to keep visible

**LATER candidates:** Runtime/hour/cycle recurrence and combined rules; QR/barcode identification; customer acknowledgement/signature; user-confirmed calendar export; additional report layouts or specialist fields; optional cross-device backup automation. [H4–H5]

**OUT for the proposed first product:** Quotes, invoices, payments, accounting, payroll, sales pipelines, stock purchasing, dispatch, team permissions, simultaneous editing, live location, route optimization, customer portals, automated communication campaigns, full message-history ingestion, sensor integrations, predictive maintenance, proprietary manufacturer databases, and regulatory-certification claims. [H4–H5]

Multi-technician editing and cloud synchronization are not quietly included under LATER “backup.” Licensing is unresolved and should be isolated as a commercial proposal if the map covers it. Runtime-based service has deliberately moved out of the initial scope even though it was prominent in the original MixedAsset candidate and H3; do not accidentally restore it through a generic “service interval” field. [H2–H5]

## 7. Conceptual distinctions that must survive handover

The following are proposed design rules in H4/H5, not descriptions of implemented behavior.

**Due date is not appointment date.** Booking or moving a visit does not itself fulfill service or erase an overdue obligation. Contacted, booked, performed and unresolved are different facts.

**Visit completion is not universal service completion.** One visit can cover several machines/plans, with different outcomes. An inspection may be completed while discovering a fault that needs later work. Do not advance every service merely because a visit was closed.

**A reusable template is not the historical inspection.** A completed record must retain the questions, answers and identification used at the time. Later edits to a template or customer address must not silently alter an issued report. Corrections require a visible policy; this does not make the system legally tamper-proof.

**Proposed recurrence default is completion date plus interval.** Show and confirm the next date, allow deliberate override, and resolve backdating, repeated completion, month ends and paused plans. Fixed-anniversary or contractual rules remain open rather than covertly added. Merely snoozing a notification must not count as service.

**Local-first is not the same as backed up or synchronized.** Draft saving, a local database, a readable CSV export, a complete restorable backup and live shared editing serve different purposes. A photo-bearing service history needs a tested recovery workflow, not just an export label.

**External handoff is not delivery confirmation.** Opening a dialer, message composer, navigation app or share sheet must not create a false “customer contacted,” “message delivered,” or “report received” record. Use explicit technician confirmation where the app cannot know the outcome.

**Due records are the source of truth; notifications are assistance.** The app must remain useful when notifications are unavailable. Do not inherit Routine Repeater’s exact-alarm design or delivery expectations just because it was the development-scope reference.

**Internal information is not automatically report content.** Site access notes and private technician notes should not leak into customer documents. Sharing an exported report also has different control limits from editing an internal record.

These distinctions explain why the app map must cover cancellation, partial work, read-only history, denied permissions, failed exports and recovery—not only the happy path.

## 8. Android feasibility: carry forward the cautions, not unverified promises

Felix explicitly asked that every included capability be defensible under Android coding standards. H5 operationalizes that as supported Android behavior, proportionate permissions, current official documentation/Play requirements, and a manageable maintenance burden. It is not a claim that an AI-produced conceptual map certifies an implementation or guarantees Play approval. [H6; H5]

The earlier assessment proposed ordinary Android mechanisms for notifications, camera/photo intake, document generation and sharing, external contact/navigation actions, and user-owned files. It cautioned against unnecessary exact-alarm complexity for obligations measured in months. It also warned that a photographic service history requires an explicit backup strategy rather than assuming operating-system backup is sufficient. These are points for verification, not a locked architecture. [H4; T01–T05]

For a material dependency, the new map should explain **the user-visible behavior, a practical supported route, its limitation, and the fallback**. Use current official Android/Play sources when checking notifications, background/lifecycle behavior, attachments, files/PDFs, external handoffs, backup/restore, or billing if included. Do not reopen the broad market ranking merely to make a conceptual map.

No Kotlin framework, database implementation, min/target SDK, build toolchain, or exact delivery mechanism has been approved for ServiceLoop. Routine Repeater’s repository, UI, activity/routine model and tooling are not dependencies of this handover.

## 9. Validation and unresolved decisions

### 9.1 What has not been demonstrated

The available materials do not contain target-user interviews performed for this project, observed live ServiceLoop use, payments, conversion/retention data, a working prototype, installed-competitor comparisons, a validated market size, or a selected launch trade. The phrase “high willingness to pay” remains H3’s hypothesis. No source supplied here establishes that independent technicians broadly reject local-only operation or broadly prefer it. [H2–H6]

### 9.2 Validation direction already recommended

H4 recommended a small real-user pilot, ideally within a trade Felix can reach, comparing the entire cycle: bring existing customer/equipment records in, identify due work, contact/book, perform a visit, issue a report and establish the next obligation. Test whether users actually leave their current tools behind rather than merely approving screenshots.

Kill or materially revise the proposition if a current competitor performs that target workflow with comparable ease, if technicians will not maintain the underlying records, if the required report becomes a specialist certification system, or if simultaneous multi-person editing is indispensable to the first target group. These are operational tests, not an automatic asset-count rule. [H4]

### 9.3 Decisions the app-map exercise must expose, not conceal

| Open topic | Starting point from H4/H5 |
|---|---|
| First trade and accessible pilot users | Validate in one reachable trade; no trade has been selected. |
| Single-device acceptance | Use it as the initial design constraint, then test actual working practices. |
| Real recurrence rules | Completion date plus interval as a proposed default; identify genuine fixed-anniversary needs before including them. |
| Forms/report usefulness | Simple item types and one report layout; identify which customer-visible information is actually required. |
| Partial/corrective work | Distinguish obligations, performed inspections, equipment findings and follow-ups. |
| Data migration and recovery | Limited validated CSV import and full backup/restore; no automatic multi-dataset merge. |
| Price/licensing | Trial plus one-time unlock was proposed, not selected; no approved price, limits or billing implementation. |
| Platform and implementation | Verify material capabilities through current official sources; no inherited toolchain decision. |
| Geography, language and compliance | Unspecified. Trade examples are not authority to claim certification or universal regulatory coverage. |

The app-map response should recommend coherent, reversible defaults, label them, and collect genuinely consequential decisions for review rather than stopping for a large preliminary questionnaire.

## 10. How to use this handover

Read this context, including Appendix A, then execute `ServiceLoop_Functional_App_Map_Prompt_v1_1.md`. The context preserves **why this opportunity was selected, where the evidence comes from, where assessments disagree, and what has not been approved**. The prompt specifies **the work to produce**.

The optional `research_archive/Android_opportunity_screening_2026-09-05.md` preserves the complete original screening register and scoring framework. It is background, not a competing feature backlog, and it is not necessary to rerun every candidate before designing ServiceLoop.

Use readable source names and the durable links below in portable documents. Do not depend on sandbox paths or conversation-specific citation tokens remaining meaningful in a different thread. A future product decision log and a user-approved app map may supersede proposals here; until then, avoid labelling this handover an authoritative finished product specification.

## 11. External source register

**Status for every entry:** cited in the earlier study or reassessment; preserved here without reopening or freshly verifying it. Descriptions explain its earlier evidentiary role. They are not a current audit of the linked page. The complete other-model research bibliography was not provided.

### User-problem evidence

- **E01 — Annual customer-equipment maintenance.** Installer discussion used in H4 to support recurring service obligations becoming difficult to track through spreadsheets/calendar. [Reddit: How do you keep track of annual maintenance?](https://www.reddit.com/r/smallbusiness/comments/1u779tc/how_do_you_keep_track_of_annual_maintenance/)
- **E02 — Solo technician inspection and reporting workflow.** Older discussion used in H4 concerning fitness-centre equipment, equipment-specific checklists, identifiers and customer reports. [Reddit: Checklist/customer report software for solo…](https://www.reddit.com/r/smallbusiness/comments/o9w8n5/checklistcustomer_report_software_for_solo/)
- **E03 — Maintenance logs.** Used by H2 for practical machine-history needs. [Reddit: Custodians — maintenance logs](https://www.reddit.com/r/Custodians/comments/1jr2787/maintenance_logs/)
- **E04 — How maintenance is actually tracked.** Used by H2 for fragmented/shared maintenance-record workflows. [Reddit: Manufacturing discussion](https://www.reddit.com/r/manufacturing/comments/1q28aib/how_are_you_guys_actually_tracking_maintenance/)
- **E05 — Reusable checklists and retained history.** Used by H2’s Runbook candidate for completed/incomplete records across resets. [Reddit: Checklist app which resets…](https://www.reddit.com/r/productivity/comments/ve3nig/do_you_know_any_checklist_app_which_resets_the/)

These discussions are anecdotal problem evidence. Do not infer prevalence, payment intent or current competitor deficiencies from their existence alone.

### Competitor and substitute references

- **C01 — ServiceM8.** H4’s customer-asset/form competitor and free-plan counterexample. [Asset Management](https://www.servicem8.com/features-asset-management) · [Free plan](https://www.servicem8.com/uk/free-plan). The Android Lite observation is retained from H4; the carried-over answer did not preserve a separate Android-Lite documentation link, so specifically verify parity before relying on it.
- **C02 — Workever.** H4’s direct comparator for assets, service history and due work. [Asset Management overview](https://help.workever.com/en/articles/2831603-asset-management-overview)
- **C03 — MaintainX.** H4’s comparator for work orders, recurring work, procedures and offline functionality; features/limits vary by plan. [Pricing and feature comparison](https://www.getmaintainx.com/pricing)
- **C04 — LookOver.** H2’s counterexample to a generic small-engine maintenance gap. [Small-engine maintenance app](https://lookover.app/small-engine-maintenance-app/)
- **C05 — Report and Run.** H2’s counterexample to a simple inexpensive field-photo/PDF-reporting opportunity. [Product site](https://www.reportandrun.com/)
- **C06 — Site Audit Pro.** H2’s substitute in standalone field inspections/reporting. [Product site](https://siteauditpro.com/)
- **C07 — Checklist alternatives.** H2’s checks against a generic reusable-checklist gap. [Check Off on Google Play](https://play.google.com/store/apps/details?hl=en&id=name.obrien.dave.lister) · [Checklist.com on Google Play](https://play.google.com/store/apps/details?hl=en_GB&id=com.checklist.android) · [Checklist: Reusable Lists](https://getchecklist.app/)
- **C08 — Squeegee.** H2’s recurring customer-work counterexample. [Pricing](https://squeeg.ee/pricing)

Vendor pages are useful for verifying advertised capabilities, not independent proof of customer satisfaction. Earlier omissions or subscription complaints must not substitute for direct workflow testing.

### Official platform references retained for targeted checking

- **T01 — Android alarms and timing.** [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule)
- **T02 — Android persistent background work.** [Persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent)
- **T03 — Android external actions.** [Common intents](https://developer.android.com/guide/components/intents-common)
- **T04 — Android backup.** [Large Backups documentation](https://developer.android.com/identity/data/large-backups). H4 used this page while discussing backup limitations. Recheck the relevant supported backup mechanism and limits rather than assuming any OS mechanism is a complete recovery design.
- **T05 — Google Play user data.** [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- **T06 — Notification permission.** [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- **T07 — Selected media and documents.** [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker) · [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)

These pointers do not constitute implementation approval. Follow current official documentation when a capability is actually designed.

---

# Appendix A — Other model’s proposal as supplied by Felix

**Attribution and status:** The following is the complete ServiceLoop entry pasted by the user. Spacing/list indentation is normalized; its substantive wording, recommendations, ratings and estimates are preserved. It is source material, not an endorsed or approved specification. In particular, the competition, willingness-to-pay, complexity and score claims below must be read alongside Sections 4–5, not substituted for them.

## #1 — ServiceLoop

### Recurring equipment-service manager for very small service businesses

**Pitch:** An Android-first tool for one-person and small maintenance businesses that remembers every installed machine, when each customer is next due for service, walks the technician through the appropriate inspection, and produces a customer-ready service report.

**Target user:** Someone who installs or maintains equipment requiring service every 3–24 months: gym equipment technicians, water-treatment installers, HVAC-adjacent specialists, coffee-machine technicians, fire-safety contractors, gate/door installers, generator specialists, workshop-equipment technicians, pool-equipment technicians and similar microbusinesses.

### The problem

The work itself is not necessarily difficult to manage. The problem is remembering **hundreds of future obligations attached to different customers and machines**.

A recent small-business owner described precisely this: installed equipment requires maintenance every 6–12 months, spreadsheets and calendar reminders become messy as the customer list grows, and customers risk simply disappearing from the future service pipeline.

Another solo maintenance technician servicing fitness-centre equipment described needing equipment-specific checklists, serial numbers, inspection results and customer-facing reports; generic spreadsheets, Airtable and field-service systems each failed some important part of that workflow.

This is high-quality pain because failure costs **revenue**, not merely inconvenience.

### Current workaround

Excel/Sheets + calendar + customer folders + Word/PDF reports + photos.

At larger scale, users move toward CRMs or full field-service suites, but those products solve vastly more problems than a one-person technician needs.

### Competition

**Low to moderate at the narrow end; high if you broaden it into field-service management.**

There are many CMMS/field-service products. There are surprisingly few polished mobile tools whose proposition is simply:

**“Which customer machines are due, what do I inspect, and give me the report.”**

Generic tool-maintenance apps exist, but very new Play listings have tiny adoption and concentrate on maintaining the owner's own tools rather than recurring customer service.

### Competitive wedge

Not another CRM.

No:

- sales pipeline;
- dispatch centre;
- workforce management;
- payroll;
- accounting;
- route optimization;
- enterprise asset hierarchy.

Instead:

**Customer → site → equipment → service interval → visit checklist → signed/reportable history → next due date.**

That is a coherent product.

### V1

- customers/sites;
- equipment with photo, model, serial and notes;
- date or runtime-based service interval;
- “due soon / overdue” dashboard;
- reusable inspection templates by equipment type;
- visit workflow;
- photos and notes;
- parts replaced;
- PDF service report;
- mark complete → calculate next visit;
- local-first database;
- backup/export;
- calendar/contact/share integration.

### Android advantage

Strong. The technician is physically standing beside the machine.

Camera, file storage, share sheet, offline operation, notifications, contact actions, QR/barcode scanning and PDF generation all belong naturally on the phone.

### Complexity

**6/10.**

Roughly **1.2–1.5× Routine Repeater**.

The scheduling itself is easier than Routine Repeater. The additional complexity comes from relational data, templated inspections, photographs and PDF reports.

No backend is necessary for a single-tech MVP.

### Monetization

**High willingness to pay.**

This is one of the rare candidates where €20–50 as a one-time professional licence or perhaps €3–6/month can be completely rational if the software recovers even one forgotten service call.

I would initially favour **free trial + one-time Pro**, possibly adding optional sync later.

### Discoverability

**8/10.**

Search intent exists:

- equipment maintenance app
- service reminder customers
- maintenance log customers
- equipment service tracker
- field inspection report app
- maintenance checklist PDF
- service due reminder

Industry-specific landing pages could work particularly well.

### Market

**Healthy niche**, composed of many micro-niches.

### Why hasn't somebody won?

Because enterprise field-service software chases companies with technicians, dispatching and larger contracts. Tiny specialist businesses are too small individually to attract much enterprise attention.

Meanwhile, generic task managers lack the domain model.

### Biggest risk

The horizontal version may be too generic.

The product might need to launch under **one strong vertical**, such as fitness-equipment service or water-treatment equipment, before generalizing.

### Kill criterion

Interview ten target businesses. Kill it if more than seven say either:

1. their existing field-service system already handles this comfortably; or
2. they have fewer than ~50 recurring assets and calendar reminders genuinely remain sufficient.

### Scorecard

A Pain **9** · B Frequency **8** · C Value **10** · D Existing weakness **8** · E Competition **8** · F Android **9** · G Feasibility **8** · H Monetization **9** · I Discoverability **8** · J Market **7** · K Policy safety **10** · L Maintainability **9** · M Defensibility **6** · N Why-now **8**

**Opportunity score: 88/100**
