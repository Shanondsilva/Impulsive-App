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
        val normalPackage = packageName.lowercase()
        val normalLabel = label.lowercase()
        val source = "$normalPackage $normalLabel"

        KnownRecommendedRules.firstOrNull { rule ->
            normalPackage == rule.packageName || source.contains(rule.matchToken)
        }?.let { rule ->
            return ProtectedAppCandidate(packageName, label, rule.category, ProtectedAppRiskBand.Recommended, rule.reason)
        }

        return when {
            BrowserTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.BrowserSearch, ProtectedAppRiskBand.Recommended,
                "Browser or search apps can bypass protection if left unprotected.",
            )
            SocialTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.SocialFeed, ProtectedAppRiskBand.Recommended,
                "Social feeds are commonly linked to visual and scrolling triggers.",
            )
            VideoTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.VideoMedia, ProtectedAppRiskBand.Review,
                "Video apps may be fine for some users and risky for others.",
            )
            MessagingDatingTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.MessagingDating, ProtectedAppRiskBand.Review,
                "Only protect this if messages, dating, or image sharing trigger the loop.",
            )
            GameTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.Games, ProtectedAppRiskBand.Review,
                "Games are not automatically risky, but can become avoidance loops.",
            )
            UtilitySafeTokens.any(source::contains) -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.UtilitySystem, ProtectedAppRiskBand.UsuallySafe,
                "This looks like a utility or system app.",
            )
            else -> ProtectedAppCandidate(
                packageName, label, ProtectedAppCategory.Unknown, ProtectedAppRiskBand.Review,
                "Review this manually if it usually leads to the loop.",
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
            KnownRule("com.instagram.android", "instagram", ProtectedAppCategory.SocialFeed, "Known social feed trigger."),
            KnownRule("com.zhiliaoapp.musically", "tiktok", ProtectedAppCategory.ShortVideo, "Short-video feed trigger."),
            KnownRule("com.google.android.youtube", "youtube", ProtectedAppCategory.VideoMedia, "Video recommendation trigger."),
            KnownRule("com.reddit.frontpage", "reddit", ProtectedAppCategory.SocialFeed, "Forum and image-feed trigger."),
            KnownRule("com.android.chrome", "chrome", ProtectedAppCategory.BrowserSearch, "Browser and search access."),
            KnownRule("com.brave.browser", "brave", ProtectedAppCategory.BrowserSearch, "Browser and private search access."),
            KnownRule("com.twitter.android", "twitter", ProtectedAppCategory.SocialFeed, "Social feed trigger."),
            KnownRule("com.facebook.katana", "facebook", ProtectedAppCategory.SocialFeed, "Social feed trigger."),
        )

        val BrowserTokens = listOf("browser", "chrome", "brave", "firefox", "edge", "opera", "duckduckgo", "samsung internet")
        val SocialTokens = listOf("instagram", "tiktok", "reddit", "facebook", "twitter", "snapchat", "pinterest", "tumblr")
        val VideoTokens = listOf("youtube", "vimeo", "netflix", "prime video", "disney", "twitch")
        val MessagingDatingTokens = listOf("whatsapp", "telegram", "messenger", "signal", "tinder", "bumble", "hinge")
        val GameTokens = listOf("game", "games", "clash", "royale", "candy", "roblox", "minecraft")
        val UtilitySafeTokens = listOf("settings", "calendar", "calculator", "clock", "phone", "contacts", "maps", "files")
    }
}
