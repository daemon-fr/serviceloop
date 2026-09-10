# Phosphor Icons vendor source

This directory contains the complete locally supplied Phosphor Icons SVG source tree: six style directories (`bold`, `duotone`, `fill`, `light`, `regular`, and `thin`) with 1,512 SVG files each. The supplied tree does not encode an upstream package version, so no version is asserted here.

The source was supplied directly by the ServiceLoop owner for local vendoring. It is licensed under the MIT license preserved in `LICENSE`. Product UI uses only the `fill` family. `serviceloop-icons.json` is the checked semantic selection manifest, and `phosphor_tool.py generate` deterministically creates the small Android resource subset used by the application.

The complete source tree is tooling/vendor material. It must remain outside Android resources and assets and must not be packaged into the APK.
