package com.impulsive.app.eval

import android.content.Context
import com.impulsive.app.data.db.BypassEvent
import com.impulsive.app.data.db.EvalMetrics
import com.impulsive.app.data.db.TriggerLog
import com.impulsive.app.data.db.UserProfile
import com.impulsive.app.data.db.WeeklyTarget
import com.impulsive.app.data.repository.ImpulsiveRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports all evaluation data to a JSON file in app-specific external storage.
 * Uses manual JSONObject construction — no extra dependencies required.
 */
class EvalExporter(
    private val context: Context,
    private val repo: ImpulsiveRepository
) {

    /**
     * Queries every table and writes a single JSON file.
     * Returns the output [File] on success.
     */
    suspend fun exportToJson(): File {
        val profile     = repo.getProfile()
        val weeklyRows  = repo.getAllWeeklyTargets()
        val triggerRows = repo.getAllTriggerLogs()
        val evalRows    = repo.getAllEvalMetrics()
        val bypassRows  = repo.getAllBypassEvents()

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val root = JSONObject().apply {
            put("exportDate", isoFormat.format(Date()))
            put("appVersion", "1.0.0")
            put("userProfile", profile?.toJson() ?: JSONObject.NULL)
            put("weeklyTargets",  JSONArray(weeklyRows.map  { it.toJson() }))
            put("triggerLogs",   JSONArray(triggerRows.map  { it.toJson(isoFormat) }))
            put("evalMetrics",   JSONArray(evalRows.map     { it.toJson(isoFormat) }))
            put("bypassEvents",  JSONArray(bypassRows.map   { it.toJson(isoFormat) }))
        }

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val timestamp = System.currentTimeMillis()
        val file = File(dir, "impulsive_export_$timestamp.json")
        file.writeText(root.toString(2))
        return file
    }

    // ---- JSON helpers ----

    private fun UserProfile.toJson() = JSONObject().apply {
        put("id", id)
        put("baselineSessionsPerWeek", baselineSessionsPerWeek)
        put("path", path)
        put("identityAnchor", identityAnchor)
        put("triggers", triggers)
        put("onboardingComplete", onboardingComplete)
        put("monitoredApps", monitoredApps)
    }

    private fun WeeklyTarget.toJson() = JSONObject().apply {
        put("weekStartDate", weekStartDate)
        put("allowedSessions", allowedSessions)
        put("usedSessions", usedSessions)
        put("stallReason", stallReason)
    }

    private fun TriggerLog.toJson(fmt: SimpleDateFormat) = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("timestampIso", fmt.format(Date(timestamp)))
        put("triggerType", triggerType)
        put("outcome", outcome)
        put("holdDurationSeconds", holdDurationSeconds)
    }

    private fun EvalMetrics.toJson(fmt: SimpleDateFormat) = JSONObject().apply {
        put("id", id)
        put("phaseNumber", phaseNumber)
        put("metricName", metricName)
        put("metricValue", metricValue)
        put("timestamp", timestamp)
        put("timestampIso", fmt.format(Date(timestamp)))
    }

    private fun BypassEvent.toJson(fmt: SimpleDateFormat) = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("timestampIso", fmt.format(Date(timestamp)))
        put("type", type)
        put("recovered", recovered)
    }
}
