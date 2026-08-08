package org.polyfrost.polyplus.client

import org.polyfrost.oneconfig.internal.ui.themes.UIBranding
import org.polyfrost.oneconfig.internal.ui.themes.UITheme

// Java mixins cannot call Kotlin's mangled copy method
internal object ThemeBrandingUtil {
    @JvmStatic
    fun withBranding(theme: UITheme, branding: UIBranding): UITheme = theme.copy(branding = branding)
}
