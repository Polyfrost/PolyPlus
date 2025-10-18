package org.polyfrost.polyplus.client

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.polyplus.PolyPlus

object Config : Config("${PolyPlus.ID}.json", PolyPlus.NAME, Category.OTHER) {

    @JvmStatic
    @Switch(title = "Discord RPC")
    var rpcEnabled = true // The default value for the boolean Switch

//    @JvmStatic
//    @Slider(title = "Example Slider", min = 0f, max = 100f, step = 10f)
//    var exampleSlider = 50f // The default value for the float Slider
//
//    @Dropdown(title = "Example Dropdown", options = ["Option 1", "Option 2", "Option 3", "Option 4"])
//    var exampleDropdown = 1 // Default option (in this case, "Option 2")

    @Text(title = "API URL", description = "The url for the polyplus api. Only change if you know what you're doing.", placeholder = "https://plus.polyfrost.org/")
    var apiUrl: String = "https://plus-staging.polyfrost.org/"
}
