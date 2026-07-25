package com.impulsive.app.backend.service.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

internal enum class InAppReviewFlowResult {
    FlowCompleted,
    RequestFailed,
    LaunchFailed,
    ActivityUnavailable,
}

internal const val ImpulsivePlayStoreApplicationId =
    "com.impulsive.app"

private const val GooglePlayPackageName =
    "com.android.vending"

internal fun buildImpulsivePlayStoreListingUrl(): String {
    return "https://play.google.com/store/apps/details" +
        "?id=$ImpulsivePlayStoreApplicationId"
}

internal fun Context.findHostActivity(): Activity? {
    return when (this) {
        is Activity -> this

        is ContextWrapper ->
            baseContext.findHostActivity()

        else -> null
    }
}

internal fun launchImpulsiveInAppReview(
    activity: Activity,
    onFinished: (InAppReviewFlowResult) -> Unit = {},
) {
    if (
        activity.isFinishing ||
        activity.isDestroyed
    ) {
        onFinished(InAppReviewFlowResult.ActivityUnavailable)
        return
    }

    val manager = runCatching {
        ReviewManagerFactory.create(activity)
    }.getOrElse {
        onFinished(InAppReviewFlowResult.RequestFailed)
        return
    }

    val request = runCatching {
        manager.requestReviewFlow()
    }.getOrElse {
        onFinished(InAppReviewFlowResult.RequestFailed)
        return
    }

    request.addOnCompleteListener { requestTask ->
        if (!requestTask.isSuccessful) {
            onFinished(InAppReviewFlowResult.RequestFailed)
            return@addOnCompleteListener
        }

        if (
            activity.isFinishing ||
            activity.isDestroyed
        ) {
            onFinished(InAppReviewFlowResult.ActivityUnavailable)
            return@addOnCompleteListener
        }

        val launchTask = runCatching {
            manager.launchReviewFlow(
                activity,
                requestTask.result,
            )
        }.getOrElse {
            onFinished(InAppReviewFlowResult.LaunchFailed)
            return@addOnCompleteListener
        }

        launchTask.addOnCompleteListener { completedTask ->
            onFinished(
                if (completedTask.isSuccessful) {
                    InAppReviewFlowResult.FlowCompleted
                } else {
                    InAppReviewFlowResult.LaunchFailed
                },
            )
        }
    }
}

internal fun openImpulsivePlayStoreListing(
    context: Context,
): Boolean {
    val listingUri = Uri.parse(
        buildImpulsivePlayStoreListingUrl(),
    )

    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        listingUri,
    ).apply {
        setPackage(GooglePlayPackageName)
        addNewTaskFlagWhenNeeded(context)
    }

    if (
        startActivitySafely(
            context = context,
            intent = playStoreIntent,
        )
    ) {
        return true
    }

    val browserIntent = Intent(
        Intent.ACTION_VIEW,
        listingUri,
    ).apply {
        addNewTaskFlagWhenNeeded(context)
    }

    return startActivitySafely(
        context = context,
        intent = browserIntent,
    )
}

private fun Intent.addNewTaskFlagWhenNeeded(
    context: Context,
) {
    if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun startActivitySafely(
    context: Context,
    intent: Intent,
): Boolean {
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
