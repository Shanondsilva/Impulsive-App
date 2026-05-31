package com.impulsive.app.backend.data.local.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.impulsive.app.backend.domain.model.protection.ProtectedAppCandidate
import com.impulsive.app.backend.domain.model.protection.ProtectedAppCategory
import com.impulsive.app.backend.domain.model.protection.ProtectedAppRiskBand

class InstalledAppScanner(
    private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager

    fun getLaunchableAppCandidates(): List<ProtectedAppCandidate> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolveInfos
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    .ifBlank { packageName }
                classify(packageName = packageName, label = label)
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<ProtectedAppCandidate> { it.riskBand.ordinal }
                    .thenBy { it.appLabel.lowercase() }
            )
    }

    private fun classify(packageName: String, label: String): ProtectedAppCandidate {
        val normalizedPackage = packageName.lowercase()
        val normalizedLabel = label.lowercase()
        val source = "$normalizedPackage $normalizedLabel"

        KnownRecommendedRules.firstOrNull { rule ->
            normalizedPackage == rule.packageName || source.contains(rule.matchToken)
        }?.let { rule ->
            return ProtectedAppCandidate(packageName, label, rule.category, ProtectedAppRiskBand.Recommended, rule.reason)
        }

        if (SafeUtilityPackageNames.any { normalizedPackage == it } || SafeUtilityTokens.any { source.contains(it) }) {
            return ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.UtilitySystem, ProtectedAppRiskBand.HiddenSafe,
                "This looks like a phone utility, not a normal trigger app.",
            )
        }

        return when {
            BrowserTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.BrowserSearch, ProtectedAppRiskBand.Recommended,
                "Browsers and search apps can open blocked content directly.",
            )
            SocialTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.SocialFeed, ProtectedAppRiskBand.Recommended,
                "Social feeds can create visual and scrolling triggers.",
            )
            ShortVideoTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.ShortVideo, ProtectedAppRiskBand.Recommended,
                "Short-video feeds can quickly pull you into autopilot.",
            )
            VideoTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.VideoMedia, ProtectedAppRiskBand.Recommended,
                "Video platforms can become a trigger source for some users.",
            )
            MessagingDatingTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.MessagingDating, ProtectedAppRiskBand.Recommended,
                "Messaging or dating apps can involve image-heavy trigger loops.",
            )
            GameTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.Games, ProtectedAppRiskBand.Review,
                "Games are not automatically risky, but you can protect this if it leads to the loop.",
            )
            else -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.Unknown, ProtectedAppRiskBand.HiddenSafe,
                "Not suggested by default.",
            )
        }
    }

    private data class KnownRule(
        val packageName: String,
        val matchToken: String,
        val category: ProtectedAppCategory,
        val reason: String,
    )

    private companion object {
        val KnownRecommendedRules = listOf(
            KnownRule("com.instagram.android", "instagram", ProtectedAppCategory.SocialFeed, "Instagram is a visual social feed and common trigger source."),
            KnownRule("com.zhiliaoapp.musically", "tiktok", ProtectedAppCategory.ShortVideo, "TikTok is a short-video feed and common trigger source."),
            KnownRule("com.google.android.youtube", "youtube", ProtectedAppCategory.VideoMedia, "YouTube can become a visual or search trigger."),
            KnownRule("com.reddit.frontpage", "reddit", ProtectedAppCategory.SocialFeed, "Reddit can contain adult communities and search loops."),
            KnownRule("com.android.chrome", "chrome", ProtectedAppCategory.BrowserSearch, "Chrome can open blocked websites directly."),
            KnownRule("com.brave.browser", "brave", ProtectedAppCategory.BrowserSearch, "Brave can open blocked websites directly."),
            KnownRule("org.mozilla.firefox", "firefox", ProtectedAppCategory.BrowserSearch, "Firefox can open blocked websites directly."),
            KnownRule("com.sec.android.app.sbrowser", "samsung internet", ProtectedAppCategory.BrowserSearch, "Samsung Internet can open blocked websites directly."),
            KnownRule("com.microsoft.emmx", "edge", ProtectedAppCategory.BrowserSearch, "Edge can open blocked websites directly."),
            KnownRule("com.twitter.android", "twitter", ProtectedAppCategory.SocialFeed, "X/Twitter can contain visual and scrolling triggers."),
            KnownRule("com.facebook.katana", "facebook", ProtectedAppCategory.SocialFeed, "Facebook can contain visual and scrolling triggers."),
            KnownRule("com.snapchat.android", "snapchat", ProtectedAppCategory.SocialFeed, "Snapchat can involve image-heavy triggers."),
            KnownRule("com.pinterest", "pinterest", ProtectedAppCategory.SocialFeed, "Pinterest can become a visual search loop."),
            KnownRule("com.discord", "discord", ProtectedAppCategory.MessagingDating, "Discord can contain image-heavy communities."),
            KnownRule("org.telegram.messenger", "telegram", ProtectedAppCategory.MessagingDating, "Telegram can contain adult channels and image-heavy content."),
        )

        val SafeUtilityPackageNames = listOf(
            "com.android.settings",
            "com.android.camera",
            "com.sec.android.app.camera",
            "com.google.android.apps.photos",
            "com.sec.android.gallery3d",
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "com.samsung.android.samsungpass",
            "com.sec.android.easymover",
            "com.sec.android.app.myfiles",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.bixby.agent",
            "com.samsung.android.app.routines",
            "com.samsung.android.lool",
            "com.sec.android.app.launcher",
        )
        val SafeUtilityTokens = listOf(
            "bixby", "camera", "gallery", "photos", "play store", "galaxy store",
            "samsung pass", "smart switch", "my files", "file manager", "files",
            "phone", "dialer", "contacts", "settings", "calculator", "clock", "calendar",
            "messages", "recorder", "wallet", "authenticator", "keyboard", "launcher",
            "themes", "weather", "find my mobile", "device care", "secure folder",
            "nearby share", "print service", "android auto", "finder", "routines",
        )
        val BrowserTokens = listOf("browser", "chrome", "brave", "firefox", "opera", "edge", "duckduckgo", "kiwi", "tor", "internet", "search")
        val SocialTokens = listOf("instagram", "reddit", "twitter", "facebook", "snapchat", "pinterest", "tumblr")
        val ShortVideoTokens = listOf("tiktok", "shorts", "reels")
        val VideoTokens = listOf("youtube", "twitch", "vimeo", "dailymotion", "video")
        val MessagingDatingTokens = listOf("tinder", "bumble", "hinge", "grindr", "telegram", "discord")
        val GameTokens = listOf("game", "games")
    }
}
