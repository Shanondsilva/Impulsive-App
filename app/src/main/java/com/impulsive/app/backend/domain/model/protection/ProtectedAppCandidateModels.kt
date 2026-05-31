package com.impulsive.app.backend.domain.model.protection

enum class ProtectedAppCategory(
    val label: String,
) {
    SocialFeed("Social feed"),
    ShortVideo("Short video"),
    BrowserSearch("Browser or search"),
    VideoMedia("Video or media"),
    MessagingDating("Messaging or dating"),
    Games("Games"),
    UtilitySystem("Utility or system"),
    Unknown("Other app"),
}

enum class ProtectedAppRiskBand(
    val label: String,
) {
    Recommended("Recommended to protect"),
    Review("Review if it triggers you"),
    UsuallySafe("Usually safe"),
}

data class ProtectedAppCandidate(
    val packageName: String,
    val appLabel: String,
    val category: ProtectedAppCategory,
    val riskBand: ProtectedAppRiskBand,
    val reason: String,
) {
    val isRecommended: Boolean
        get() = riskBand == ProtectedAppRiskBand.Recommended
}
