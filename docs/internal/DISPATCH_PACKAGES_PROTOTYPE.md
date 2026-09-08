# Dispatch packages prototype

**EXPERIMENTAL — NOT PRODUCT BASELINE — NOT APPROVED FOR MERGE**

This isolated prototype tests asynchronous coordinator-to-technician work handoff through readable, unencrypted `.slwork` JSON files and Android sharing. It adds no backend, accounts, live synchronization, shared database, status-return protocol, or separate Desk product.

Coordinator tools are an off-by-default local preference. Package composition does not create local Visits. Import validates and previews first, then atomically creates ordinary Booked Visits and records deterministic `DISPATCH_IMPORTED` provenance. Exact safe plan matches may claim the current obligation; every unsafe match becomes explicit one-off work without recurrence effects. Completed eligible PDFs may be handed to a configured office email through the Android chooser; existing void/superseded sharing rules remain authoritative.
