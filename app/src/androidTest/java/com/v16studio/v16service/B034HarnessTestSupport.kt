package com.v16studio.v16service

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/** Opt-in gates keep artifact-only and historical checks out of the ordinary release inventory. */
internal fun assumeRenderEvidenceSuite() {
    assumeTrue(
        "Render-evidence tests are opt-in; rerun with -e renderEvidence true",
        InstrumentationRegistry.getArguments().getString("renderEvidence") == "true",
    )
}

internal fun assumeHistoricalFixtureValidation() {
    assumeTrue(
        "Historical SL2 fixture validation is opt-in; rerun with -e historicalFixture true",
        InstrumentationRegistry.getArguments().getString("historicalFixture") == "true",
    )
}
