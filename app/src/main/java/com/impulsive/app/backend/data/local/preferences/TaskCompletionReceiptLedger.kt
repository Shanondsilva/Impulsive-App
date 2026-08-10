package com.impulsive.app.backend.data.local.preferences

import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import com.impulsive.app.backend.domain.model.tasks.TaskCompletionResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted proof that one logical task completion has already been applied.
 *
 * Tokens are opaque local identifiers. Game tokens contain only game type and
 * the existing score-session ID.
 */
internal data class TaskCompletionReceipt(
    val completionToken: String,
    val result: TaskCompletionResult,
)

internal object TaskCompletionReceiptLedger {
    const val MaximumReceiptCount = 200
    const val MaximumTokenLength = 160

    fun normalizeToken(completionToken: String?): String? {
        if (completionToken == null) {
            return null
        }

        val normalized = completionToken.trim()

        require(normalized.isNotEmpty()) {
            "Task completion token must not be blank."
        }

        require(normalized.length <= MaximumTokenLength) {
            "Task completion token is too long."
        }

        return normalized
    }

    fun decode(encoded: String?): List<TaskCompletionReceipt> {
        if (encoded.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            val array = JSONArray(encoded)

            val parsed = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue

                    val token = item.optString("token").trim()

                    if (token.isEmpty() || token.length > MaximumTokenLength) {
                        continue
                    }

                    val taskTypeId = item.optString("taskType")

                    val taskType = PsychologyTaskType.entries.firstOrNull { it.id == taskTypeId }
                        ?: continue

                    val waitReductionMinutes = item.optInt("waitReductionMinutes", -1)
                    val levelPointsAwarded = item.optInt("levelPointsAwarded", -1)
                    val currentLevel = item.optInt("currentLevel", -1)
                    val currentLevelPoints = item.optInt("currentLevelPoints", -1)
                    val pointsNeededForNextLevel = item.optInt("pointsNeededForNextLevel", -1)

                    if (
                        waitReductionMinutes < 0 ||
                        levelPointsAwarded < 0 ||
                        currentLevel < 1 ||
                        currentLevelPoints < 0 ||
                        pointsNeededForNextLevel < 1
                    ) {
                        continue
                    }

                    add(
                        TaskCompletionReceipt(
                            completionToken = token,
                            result = TaskCompletionResult(
                                taskType = taskType,
                                taskTitle = taskType.taskTitle,
                                waitReductionMinutes = waitReductionMinutes,
                                levelPointsAwarded = levelPointsAwarded,
                                currentLevel = currentLevel,
                                currentLevelPoints = currentLevelPoints,
                                pointsNeededForNextLevel = pointsNeededForNextLevel,
                            ),
                        ),
                    )
                }
            }

            parsed.fold(emptyList<TaskCompletionReceipt>()) { current, receipt ->
                upsert(receipts = current, receipt = receipt)
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun encode(receipts: List<TaskCompletionReceipt>): String {
        val array = JSONArray()

        receipts.takeLast(MaximumReceiptCount).forEach { receipt ->
            array.put(
                JSONObject()
                    .put("token", receipt.completionToken)
                    .put("taskType", receipt.result.taskType.id)
                    .put("waitReductionMinutes", receipt.result.waitReductionMinutes)
                    .put("levelPointsAwarded", receipt.result.levelPointsAwarded)
                    .put("currentLevel", receipt.result.currentLevel)
                    .put("currentLevelPoints", receipt.result.currentLevelPoints)
                    .put("pointsNeededForNextLevel", receipt.result.pointsNeededForNextLevel),
            )
        }

        return array.toString()
    }

    fun upsert(
        receipts: List<TaskCompletionReceipt>,
        receipt: TaskCompletionReceipt,
    ): List<TaskCompletionReceipt> =
        (
            receipts.filterNot { it.completionToken == receipt.completionToken } +
                receipt
            )
            .takeLast(MaximumReceiptCount)
}
