package org.polyfrost.polyplus.client

import org.polyfrost.oneconfig.internal.ui.themes.UIBranding
import org.polyfrost.oneconfig.internal.ui.themes.UITheme

// Java mixins cannot call Kotlin's mangled copy method
internal object ThemeBrandingUtil {
    private val branding = UIBranding("assets/polyplus/brand/oneclient.svg")

    @JvmStatic
    fun branded(theme: UITheme): UITheme = theme.copy(branding = branding)
}
