package org.polyfrost.polyplus.client.launcher

enum class MsaAuthStep {
    DeviceCodeRequest,
    DeviceCodePoll,
    AuthCodeExchange,
    XblAuthenticate,
    XstsAuthorize,
    MinecraftToken,
    MinecraftProfile,
}

class MicrosoftAuthException(
    val whatHappened: String,
    val stepsToFix: List<String>,
    cause: Throwable? = null,
) : Exception(whatHappened, cause)

object MicrosoftAuthErrors {
    fun friendlyXboxError(code: Long): String? = when (code) {
        2_148_916_227L -> "This account has been banned or suspended from Xbox."
        2_148_916_229L -> "This account is a child account that must be added to a Family group by an adult before signing in."
        2_148_916_233L -> "This Microsoft account does not have an Xbox profile yet. Create one at xbox.com, then try again."
        2_148_916_234L -> "This account has not accepted the Xbox Terms of Service. Sign in at xbox.com to accept them first."
        2_148_916_235L -> "Xbox Live is not available in your country or region, so sign-in is blocked."
        2_148_916_236L, 2_148_916_237L -> "This account requires adult verification (South Korea) before it can sign in."
        2_148_916_238L -> "This is a child account. An adult must add it to a Microsoft Family group before it can sign in."
        else -> null
    }

    fun forXerr(code: Long, cause: Throwable? = null): MicrosoftAuthException = when (code) {
        2_148_916_222L -> build(
            "This account requires age verification to comply with UK regulations before it can sign in.",
            listOf(
                "Go to the Minecraft login page and sign in (https://www.minecraft.net/en-us/login)",
                "Follow the instructions to verify your age",
                "Once verified, try signing in again",
                "For more help see UK age verification on Xbox (https://support.xbox.com/en-GB/help/family-online-safety/online-safety/UK-age-verification)",
            ),
            cause,
        )
        2_148_916_227L -> build(
            "This account was suspended for violating Xbox Community Standards.",
            listOf(
                "Visit Xbox Support and review the enforcement details (https://support.xbox.com)",
                "Submit an appeal if one is available",
            ),
            cause,
        )
        2_148_916_229L -> build(
            "This account is restricted and does not have permission to play online.",
            listOf(
                "Have a guardian sign in to Microsoft Family (https://account.microsoft.com/family/)",
                "Update the online play permissions",
                "Once finished, try signing in again",
            ),
            cause,
        )
        2_148_916_233L -> build(
            "This account does not have an Xbox profile set up, or does not own Minecraft.",
            listOf(
                "Make sure Minecraft is purchased on this account",
                "Visit the Minecraft login page and sign in (https://www.minecraft.net/en-us/login)",
                "Complete Xbox profile setup if prompted",
                "Once finished, try signing in again",
            ),
            cause,
        )
        2_148_916_234L -> build(
            "This account has not accepted Xbox's Terms of Service.",
            listOf(
                "Visit Xbox and sign in (https://www.xbox.com)",
                "Accept the Terms if prompted",
                "Once finished, try signing in again",
            ),
            cause,
        )
        2_148_916_235L -> build(
            "Xbox Live is not available in your region, so sign-in is blocked.",
            listOf(
                "Xbox services must be supported in your country before you can sign in",
                "Check Xbox availability for supported regions (https://www.xbox.com/en-US/regions)",
            ),
            cause,
        )
        2_148_916_236L, 2_148_916_237L -> build(
            "This account requires adult verification under South Korean regulations.",
            listOf(
                "Visit Xbox and sign in (https://www.xbox.com)",
                "Complete the identity verification process",
                "Once finished, try signing in again",
            ),
            cause,
        )
        2_148_916_238L -> build(
            "This account is underage and not linked to a Microsoft family group.",
            listOf(
                "Review the Minecraft Family Setup guide (https://help.minecraft.net/hc/en-us/articles/4408968616077)",
                "Join or create a family group as instructed",
                "Once finished, try signing in again",
            ),
            cause,
        )
        else -> forStep(MsaAuthStep.XstsAuthorize, cause)
    }

    fun forService(step: MsaAuthStep, statusCode: Int, cause: Throwable? = null): MicrosoftAuthException {
        if (step == MsaAuthStep.MinecraftToken) {
            if (statusCode == 429) {
                return build(
                    "Microsoft or Minecraft temporarily blocked the sign-in because there were too many recent attempts.",
                    listOf(
                        "Wait about an hour before trying again",
                        "Restart Minecraft after waiting",
                        "Try signing in once more",
                        "If it keeps happening, wait longer before retrying so the temporary limit can clear",
                    ),
                    cause,
                )
            }
            if (statusCode in 500..599) {
                return build(
                    "Minecraft's authentication service is returning a server error, so sign-in cannot finish right now.",
                    listOf(
                        "Wait a few minutes and try signing in again",
                        "Check Xbox status for current service issues (https://support.xbox.com/xbox-live-status)",
                        "Try the official Minecraft Launcher to confirm whether Minecraft sign-in is affected there too (https://www.minecraft.net/en-us/download)",
                        "If the service is healthy and this keeps happening, contact support with the debug information",
                    ),
                    cause,
                )
            }
        }
        return forStep(step, cause)
    }

    fun deviceExpired(cause: Throwable? = null): MicrosoftAuthException = build(
        "The sign-in code expired before the Microsoft sign-in was finished.",
        listOf(
            "Start the sign-in again to get a fresh code",
            "Open the link and enter the code promptly, before it expires",
            "Once the Microsoft sign-in finishes, you'll be signed in automatically",
        ),
        cause,
    )

    fun deviceFailed(message: String, cause: Throwable? = null): MicrosoftAuthException = build(
        "The Microsoft sign-in could not be completed ($message).",
        listOf(
            "Start the sign-in again",
            "Open the link and enter the code, then approve the sign-in",
            "Make sure you are signing in with the Microsoft account that owns Minecraft",
        ),
        cause,
    )

    fun forStep(step: MsaAuthStep, cause: Throwable? = null): MicrosoftAuthException = when (step) {
        MsaAuthStep.DeviceCodeRequest -> build(
            "PolyPlus could not start the Microsoft sign-in, so no sign-in code could be created.",
            listOf(
                "Check that your internet connection is working",
                "Wait a moment and try signing in again",
                "If it keeps happening, try the browser sign-in instead",
            ),
            cause,
        )
        MsaAuthStep.DeviceCodePoll -> deviceExpired(cause)
        MsaAuthStep.AuthCodeExchange -> build(
            "Your saved Microsoft sign-in has expired or was revoked, so your Minecraft session could not be renewed.",
            listOf(
                "Start the sign-in again",
                "Complete the Microsoft sign-in in your browser",
                "Once the new sign-in finishes, try again",
            ),
            cause,
        )
        MsaAuthStep.XblAuthenticate -> build(
            "Xbox rejected the first sign-in step. This is most often caused by your system clock or time zone being out of sync, or by a temporary Xbox block.",
            listOf(
                "Open your system date and time settings",
                "Turn on automatic time zone and automatic time, if available",
                "Use the sync option in your settings to synchronise the clock",
                "Restart Minecraft and try signing in again",
                "If it persists, check Xbox status (https://support.xbox.com/xbox-live-status)",
            ),
            cause,
        )
        MsaAuthStep.XstsAuthorize -> build(
            "Xbox rejected the request to authorize this account for Minecraft, but did not return a specific account restriction we recognise.",
            listOf(
                "Sign in with the official Minecraft Launcher (https://www.minecraft.net/en-us/download)",
                "Complete any prompts shown by Microsoft, Xbox, or Minecraft",
                "Try signing in again",
                "If the official launcher also fails, follow the error shown there or contact Xbox Support",
            ),
            cause,
        )
        MsaAuthStep.MinecraftProfile -> build(
            "Minecraft services could not return a Java Edition profile for this account. This usually means the game was purchased recently, the Java profile is not finished being created, or the wrong Microsoft account is being used.",
            listOf(
                "Sign in with the official Minecraft Launcher and launch Java Edition once (https://www.minecraft.net/en-us/download)",
                "Wait up to an hour if the purchase or profile setup was recent",
                "Make sure you are using the Microsoft account that owns Minecraft",
                "Try signing in again",
            ),
            cause,
        )
        MsaAuthStep.MinecraftToken -> build(
            "Minecraft's authentication service could not finish signing you in.",
            listOf(
                "Wait a few minutes and try signing in again",
                "Check Xbox status for current service issues (https://support.xbox.com/xbox-live-status)",
            ),
            cause,
        )
    }

    fun network(cause: Throwable? = null): MicrosoftAuthException = build(
        "PolyPlus could not connect to a Microsoft, Xbox, or Minecraft service needed for sign-in. This is usually a local network, DNS, proxy, firewall, hosts file, VPN, or antivirus issue.",
        listOf(
            "Check that your internet connection is working",
            "Allow Minecraft through your firewall, antivirus, proxy, VPN, and hosts file rules",
            "Try a different network, or temporarily disable VPN/proxy software if you use one",
            "If routing or DNS is the issue, a service like Cloudflare WARP can sometimes help",
            "Restart Minecraft and try signing in again",
        ),
        cause,
    )

    private fun build(whatHappened: String, stepsToFix: List<String>, cause: Throwable?) =
        MicrosoftAuthException(whatHappened, stepsToFix, cause)
}
