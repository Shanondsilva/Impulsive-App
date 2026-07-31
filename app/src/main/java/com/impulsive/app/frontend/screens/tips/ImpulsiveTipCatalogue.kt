package com.impulsive.app.frontend.screens.tips

import android.content.Context
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.impulsive.app.R
import com.impulsive.app.backend.domain.tips.ImpulsiveTip
import com.impulsive.app.backend.domain.tips.ImpulsiveTipId
import com.impulsive.app.backend.domain.tips.TipAction
import com.impulsive.app.backend.domain.tips.TipAudienceTag
import com.impulsive.app.backend.domain.tips.TipCategory
import com.impulsive.app.backend.domain.tips.TipFeature
import com.impulsive.app.backend.domain.tips.TipSource

private const val REVIEWED_DATE = "30 July 2026"
private const val ANDROID_WELLBEING_SOURCE =
    "https://support.google.com/android/answer/9346420"
private const val ANDROID_MODES_SOURCE =
    "https://support.google.com/android/answer/9069335"
private const val ANDROID_NOTIFICATIONS_SOURCE =
    "https://support.google.com/android/answer/9079661"
private const val ANDROID_HOME_SOURCE =
    "https://support.google.com/android/answer/9450271"
private const val INSTAGRAM_SLEEP_SOURCE =
    "https://about.fb.com/news/2023/01/instagram-quiet-mode-manage-your-time-and-focus/"
private const val INSTAGRAM_RECOMMENDATIONS_SOURCE =
    "https://about.fb.com/news/2024/11/introducing-recommendations-reset-instagram/"
private const val INSTAGRAM_CONTROL_SOURCE =
    "https://about.fb.com/news/2022/08/testing-ways-to-control-what-you-see-on-instagram/"
private const val INTERNAL_SOURCE = "Impulsive v28 on-device feature guidance"

private data class TipTemplate(
    val id: String,
    val category: TipCategory,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    @param:ArrayRes val steps: Int,
    @param:StringRes val why: Int,
    val tags: Set<TipAudienceTag>,
    val action: TipAction,
    @param:StringRes val sourceName: Int,
    val sourceReference: String,
    val external: Boolean,
    val priority: Int,
    val requiredFeature: TipFeature? = null,
)

class ImpulsiveTipCatalogue(private val context: Context) {
    val tips: List<ImpulsiveTip> by lazy {
        templates.map { template ->
            ImpulsiveTip(
                id = ImpulsiveTipId(template.id),
                category = template.category,
                title = context.getString(template.title),
                summary = context.getString(template.summary),
                overviewSteps = context.resources.getStringArray(template.steps).toList(),
                whyThisMayHelp = context.getString(template.why),
                audienceTags = template.tags,
                action = template.action,
                source = TipSource(
                    name = context.getString(template.sourceName),
                    reference = template.sourceReference,
                    lastReviewedDate = REVIEWED_DATE,
                ),
                isExternalInstruction = template.external,
                priority = template.priority,
                requiredFeature = template.requiredFeature,
            )
        }
    }

    fun find(id: ImpulsiveTipId): ImpulsiveTip? = tips.firstOrNull { it.id == id }

    private val templates = listOf(
        TipTemplate(
            "android_focus_mode", TipCategory.Focus,
            R.string.tip_android_focus_title, R.string.tip_android_focus_summary,
            R.array.tip_android_focus_steps, R.string.tip_android_focus_why,
            setOf(TipAudienceTag.CompulsiveScrolling, TipAudienceTag.ReduceUse),
            TipAction.OpenAndroidSetting(Settings.ACTION_SETTINGS),
            R.string.tips_source_android, ANDROID_WELLBEING_SOURCE, true, 92,
            TipFeature.AndroidDigitalWellbeing,
        ),
        TipTemplate(
            "android_app_timers", TipCategory.Focus,
            R.string.tip_android_timers_title, R.string.tip_android_timers_summary,
            R.array.tip_android_timers_steps, R.string.tip_android_timers_why,
            setOf(TipAudienceTag.CompulsiveScrolling, TipAudienceTag.ReduceUse),
            TipAction.OpenAndroidSetting(Settings.ACTION_SETTINGS),
            R.string.tips_source_android, ANDROID_WELLBEING_SOURCE, true, 88,
            TipFeature.AndroidDigitalWellbeing,
        ),
        TipTemplate(
            "android_notification_categories", TipCategory.Notifications,
            R.string.tip_android_notifications_title, R.string.tip_android_notifications_summary,
            R.array.tip_android_notifications_steps, R.string.tip_android_notifications_why,
            setOf(TipAudienceTag.SocialMedia, TipAudienceTag.Stress),
            TipAction.OpenAndroidSetting(Settings.ACTION_SETTINGS),
            R.string.tips_source_android, ANDROID_NOTIFICATIONS_SOURCE, true, 86,
            TipFeature.AndroidNotifications,
        ),
        TipTemplate(
            "android_do_not_disturb", TipCategory.Focus,
            R.string.tip_android_dnd_title, R.string.tip_android_dnd_summary,
            R.array.tip_android_dnd_steps, R.string.tip_android_dnd_why,
            setOf(TipAudienceTag.Stress, TipAudienceTag.LateNight),
            TipAction.OpenAndroidSetting(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            R.string.tips_source_android, ANDROID_MODES_SOURCE, true, 84,
            TipFeature.AndroidModes,
        ),
        TipTemplate(
            "android_bedtime_mode", TipCategory.Sleep,
            R.string.tip_android_bedtime_title, R.string.tip_android_bedtime_summary,
            R.array.tip_android_bedtime_steps, R.string.tip_android_bedtime_why,
            setOf(TipAudienceTag.LateNight, TipAudienceTag.TroubleSleeping),
            TipAction.OpenAndroidSetting(Settings.ACTION_SETTINGS),
            R.string.tips_source_android, ANDROID_MODES_SOURCE, true, 96,
            TipFeature.AndroidModes,
        ),
        TipTemplate(
            "android_move_apps", TipCategory.General,
            R.string.tip_android_move_apps_title, R.string.tip_android_move_apps_summary,
            R.array.tip_android_move_apps_steps, R.string.tip_android_move_apps_why,
            setOf(TipAudienceTag.CompulsiveScrolling, TipAudienceTag.Boredom),
            TipAction.None, R.string.tips_source_android, ANDROID_HOME_SOURCE, true, 80,
        ),
        TipTemplate(
            "instagram_sleep_mode", TipCategory.Sleep,
            R.string.tip_instagram_sleep_title, R.string.tip_instagram_sleep_summary,
            R.array.tip_instagram_sleep_steps, R.string.tip_instagram_sleep_why,
            setOf(TipAudienceTag.SocialMedia, TipAudienceTag.LateNight, TipAudienceTag.TroubleSleeping),
            TipAction.None, R.string.tips_source_meta, INSTAGRAM_SLEEP_SOURCE, true, 98, TipFeature.Instagram,
        ),
        TipTemplate(
            "instagram_hide_suggestion", TipCategory.SocialMedia,
            R.string.tip_instagram_hide_title, R.string.tip_instagram_hide_summary,
            R.array.tip_instagram_hide_steps, R.string.tip_instagram_hide_why,
            setOf(TipAudienceTag.SocialMedia, TipAudienceTag.CompulsiveScrolling),
            TipAction.None, R.string.tips_source_meta, INSTAGRAM_CONTROL_SOURCE, true, 94, TipFeature.Instagram,
        ),
        TipTemplate(
            "instagram_reset_suggestions", TipCategory.SocialMedia,
            R.string.tip_instagram_reset_title, R.string.tip_instagram_reset_summary,
            R.array.tip_instagram_reset_steps, R.string.tip_instagram_reset_why,
            setOf(TipAudienceTag.SocialMedia, TipAudienceTag.CompulsiveScrolling),
            TipAction.None, R.string.tips_source_meta, INSTAGRAM_RECOMMENDATIONS_SOURCE, true, 90, TipFeature.Instagram,
        ),
        TipTemplate(
            "social_notification_categories", TipCategory.Notifications,
            R.string.tip_social_notifications_title, R.string.tip_social_notifications_summary,
            R.array.tip_social_notifications_steps, R.string.tip_social_notifications_why,
            setOf(TipAudienceTag.SocialMedia, TipAudienceTag.Stress),
            TipAction.OpenAndroidSetting(Settings.ACTION_SETTINGS),
            R.string.tips_source_android, ANDROID_NOTIFICATIONS_SOURCE, true, 82,
            TipFeature.AndroidNotifications,
        ),
        TipTemplate(
            "impulsive_select_apps", TipCategory.ImpulsiveProtection,
            R.string.tip_impulsive_apps_title, R.string.tip_impulsive_apps_summary,
            R.array.tip_impulsive_apps_steps, R.string.tip_impulsive_apps_why,
            setOf(TipAudienceTag.General, TipAudienceTag.CompulsiveScrolling),
            TipAction.OpenImpulsiveFeature(TipFeature.AppProtection),
            R.string.tips_source_impulsive, INTERNAL_SOURCE, false, 76, TipFeature.AppProtection,
        ),
        TipTemplate(
            "impulsive_create_plan", TipCategory.MomentPlan,
            R.string.tip_impulsive_plan_title, R.string.tip_impulsive_plan_summary,
            R.array.tip_impulsive_plan_steps, R.string.tip_impulsive_plan_why,
            setOf(TipAudienceTag.DailyResetHabit, TipAudienceTag.Stress, TipAudienceTag.BeingAlone),
            TipAction.OpenImpulsiveFeature(TipFeature.MomentPlan),
            R.string.tips_source_impulsive, INTERNAL_SOURCE, false, 91, TipFeature.MomentPlan,
        ),
        TipTemplate(
            "impulsive_practise_plan", TipCategory.MomentPlan,
            R.string.tip_impulsive_practise_title, R.string.tip_impulsive_practise_summary,
            R.array.tip_impulsive_practise_steps, R.string.tip_impulsive_practise_why,
            setOf(TipAudienceTag.NoticeTriggers, TipAudienceTag.DailyResetHabit),
            TipAction.OpenImpulsiveFeature(TipFeature.MomentPlan),
            R.string.tips_source_impulsive, INTERNAL_SOURCE, false, 87, TipFeature.MomentPlan,
        ),
        TipTemplate(
            "impulsive_schedule", TipCategory.ImpulsiveProtection,
            R.string.tip_impulsive_schedule_title, R.string.tip_impulsive_schedule_summary,
            R.array.tip_impulsive_schedule_steps, R.string.tip_impulsive_schedule_why,
            setOf(TipAudienceTag.LateNight, TipAudienceTag.Morning),
            TipAction.OpenImpulsiveFeature(TipFeature.ProtectionSchedule),
            R.string.tips_source_impulsive, INTERNAL_SOURCE, false, 85, TipFeature.ProtectionSchedule,
        ),
        TipTemplate(
            "impulsive_website_protection", TipCategory.Browser,
            R.string.tip_impulsive_website_title, R.string.tip_impulsive_website_summary,
            R.array.tip_impulsive_website_steps, R.string.tip_impulsive_website_why,
            setOf(TipAudienceTag.BrowserSearch),
            TipAction.OpenImpulsiveFeature(TipFeature.WebsiteProtection),
            R.string.tips_source_impulsive, INTERNAL_SOURCE, false, 95, TipFeature.WebsiteProtection,
        ),
        TipTemplate(
            "impulsive_reset_reading", TipCategory.ResetReading,
            R.string.tip_impulsive_reading_title, R.string.tip_impulsive_reading_summary,
            R.array.tip_impulsive_reading_steps, R.string.tip_impulsive_reading_why,
            setOf(TipAudienceTag.Boredom, TipAudienceTag.Stress, TipAudienceTag.BeingAlone),
            TipAction.OpenImpulsiveFeature(TipFeature.ResetReading),
            R.string.tips_source_impulsive, INTERNAL_SOURCE, false, 89, TipFeature.ResetReading,
        ),
        TipTemplate(
            "impulsive_focus", TipCategory.Focus,
            R.string.tip_impulsive_focus_title, R.string.tip_impulsive_focus_summary,
            R.array.tip_impulsive_focus_steps, R.string.tip_impulsive_focus_why,
            setOf(TipAudienceTag.General, TipAudienceTag.ReduceUse),
            TipAction.OpenImpulsiveFeature(TipFeature.Focus),
            R.string.tips_source_impulsive, INTERNAL_SOURCE, false, 78, TipFeature.Focus,
        ),
        TipTemplate(
            "impulsive_what_works", TipCategory.General,
            R.string.tip_impulsive_what_works_title, R.string.tip_impulsive_what_works_summary,
            R.array.tip_impulsive_what_works_steps, R.string.tip_impulsive_what_works_why,
            setOf(TipAudienceTag.General, TipAudienceTag.NoticeTriggers),
            TipAction.OpenImpulsiveFeature(TipFeature.WhatWorksForMe),
            R.string.tips_source_impulsive, INTERNAL_SOURCE, false, 70, TipFeature.WhatWorksForMe,
        ),
    )
}
