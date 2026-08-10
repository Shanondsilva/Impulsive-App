package com.impulsive.app.frontend.screens.protection

internal enum class BrowserSecureDnsGuide {
    Chrome,
    Brave,
    OtherBrowsers,
}

internal object BrowserSecureDnsGuidancePolicy {

    fun requiredGuides(
        protectedBrowserPackageNames: Set<String>,
    ): List<BrowserSecureDnsGuide> {
        val selectedPackages =
            protectedBrowserPackageNames
                .asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet()

        if (selectedPackages.isEmpty()) {
            return emptyList()
        }

        return buildList {
            if (
                selectedPackages.any {
                    it in ChromePackageNames
                }
            ) {
                add(
                    BrowserSecureDnsGuide.Chrome,
                )
            }

            if (
                selectedPackages.any {
                    it in BravePackageNames
                }
            ) {
                add(
                    BrowserSecureDnsGuide.Brave,
                )
            }

            if (
                selectedPackages.any {
                    it !in ChromePackageNames &&
                        it !in BravePackageNames
                }
            ) {
                add(
                    BrowserSecureDnsGuide.OtherBrowsers,
                )
            }
        }
    }

    private val ChromePackageNames =
        setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
        )

    private val BravePackageNames =
        setOf(
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.brave.browser_nightly",
        )
}
