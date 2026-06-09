package com.maomaocake.grafanaalloyplugin.validator

import com.intellij.openapi.util.SystemInfo

/**
 * Where the validator can / can't run. `alloy validate` ships in the macOS and Linux
 * binaries only — Windows builds don't include the subcommand. Callers should check
 * [isSupportedOs] before surfacing any validator UI; the settings page shows a banner
 * when it's false so users know why the feature is hidden.
 */
object AlloyValidatorAvailability {
    val isSupportedOs: Boolean
        get() = SystemInfo.isMac || SystemInfo.isLinux
}
