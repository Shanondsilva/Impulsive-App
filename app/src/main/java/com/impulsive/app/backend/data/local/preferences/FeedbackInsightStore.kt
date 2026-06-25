package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.journal.FeedbackDayInsight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.feedbackInsightDataStore by preferencesDataStore(name = "feedback_insights")

/** Stores one analysed feedback insight per day, accumulating over time. */
class FeedbackInsightStore(private val context: Context) {

    val insights: Flow<List<FeedbackDayInsight>> =
        context.feedbackInsightDataStore.data.map { preferences ->
            decode(preferences[InsightsKey] ?: "[]")
        }

    suspend fun recordIfNewDay(insight: FeedbackDayInsight) {
        context.feedbackInsightDataStore.edit { preferences ->
            val current = decode(preferences[InsightsKey] ?: "[]").toMutableList()
            if (current.none { it.date == insight.date }) {
                current.add(insight)
                preferences[InsightsKey] = encode(current)
            }
        }
    }

    suspend fun hasDate(date: String): Boolean = insights.first().any { it.date == date }

    private fun encode(items: List<FeedbackDayInsight>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("date", item.date)
                    .put("feel", item.feel)
                    .put("timeNote", item.timeNote ?: "")
                    .put("text", item.text),
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<FeedbackDayInsight> {
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<FeedbackDayInsight>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val time = obj.optString("timeNote", "")
            result.add(
                FeedbackDayInsight(
                    date = obj.optString("date", ""),
                    feel = obj.optString("feel", "neutral"),
                    timeNote = time.ifBlank { null },
                    text = obj.optString("text", ""),
                ),
            )
        }
        return result
    }

    companion object {
        val InsightsKey = stringPreferencesKey("feedback_insights_json")
    }
}
