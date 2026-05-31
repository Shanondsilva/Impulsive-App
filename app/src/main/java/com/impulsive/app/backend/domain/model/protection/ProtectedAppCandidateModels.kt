package com.impulsive.app.backend.domain.model.protection

enum class ProtectedAppCategory(
    val label: String,
) {
    BrowserSearch("Browser or search"),
    SocialFeed("Social feed"),
    ShortVideo("Short video"),
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
    HiddenSafe("Hidden safe/system app"),
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

    val isHiddenSafe: Boolean
        get() = riskBand == ProtectedAppRiskBand.HiddenSafe
}

data class ProtectedAppSuggestionGroups(
    val selected: List<ProtectedAppCandidate>,
    val recommended: List<ProtectedAppCandidate>,
    val review: List<ProtectedAppCandidate>,
    val hiddenSafe: List<ProtectedAppCandidate>,
)

fun List<ProtectedAppCandidate>.toSuggestionGroups(
    selectedPackageNames: Set<String>,
    searchQuery: String,
    showMoreApps: Boolean,
): ProtectedAppSuggestionGroups {
    val normalizedQuery = searchQuery.trim().lowercase()
    val hasSearch = normalizedQuery.isNotEmpty()

    val selected = filter { it.packageName in selectedPackageNames }
        .sortedBy { it.appLabel.lowercase() }
    val recommended = filter {
        it.riskBand == ProtectedAppRiskBand.Recommended &&
            it.packageName !in selectedPackageNames &&
            (!hasSearch || it.matchesQuery(normalizedQuery))
    }.sortedBy { it.appLabel.lowercase() }
    val review = filter {
        it.riskBand == ProtectedAppRiskBand.Review &&
            it.packageName !in selectedPackageNames &&
            (showMoreApps || hasSearch) &&
            (!hasSearch || it.matchesQuery(normalizedQuery))
    }.sortedBy { it.appLabel.lowercase() }
    val hiddenSafe = filter {
        it.riskBand == ProtectedAppRiskBand.HiddenSafe &&
            it.packageName !in selectedPackageNames &&
            hasSearch &&
            it.matchesQuery(normalizedQuery)
    }.sortedBy { it.appLabel.lowercase() }

    return ProtectedAppSuggestionGroups(
        selected = selected,
        recommended = recommended,
        review = review,
        hiddenSafe = hiddenSafe,
    )
}

private fun ProtectedAppCandidate.matchesQuery(query: String): Boolean =
    appLabel.lowercase().contains(query) || packageName.lowercase().contains(query)
