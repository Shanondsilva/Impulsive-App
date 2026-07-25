package com.impulsive.app.backend.domain.model.legal

enum class ImpulsiveLegalDestination {
    PrivacyPolicy,
    TermsOfService,
    AccountDeletionHelp,
}

fun impulsiveLegalUrl(destination: ImpulsiveLegalDestination): String =
    when (destination) {
        ImpulsiveLegalDestination.PrivacyPolicy ->
            "https://useimpulsive.com/privacy"

        ImpulsiveLegalDestination.TermsOfService ->
            "https://useimpulsive.com/terms"

        ImpulsiveLegalDestination.AccountDeletionHelp ->
            "https://useimpulsive.com/delete-account"
    }
