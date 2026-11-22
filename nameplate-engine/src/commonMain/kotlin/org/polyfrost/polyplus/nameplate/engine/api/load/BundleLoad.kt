package org.polyfrost.polyplus.nameplate.engine.api.load

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue

sealed interface BundleLoad {
    data class Loaded(val bundle: NameplateBundle) : BundleLoad

    data class Skipped(val reason: String) : BundleLoad

    data class Invalid(val issues: List<BundleIssue>) : BundleLoad
}
