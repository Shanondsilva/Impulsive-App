package com.impulsive.app.backend.service.protection

internal data class InterruptionNotificationIncidentId(
    val packageName: String,
    val startedAtMillis: Long,
    val isWebsiteIncident: Boolean,
    val isFocusSession: Boolean,
)

internal class InterruptionNotificationReminderCoordinator(
    private val nowMillis: () -> Long,
    private val schedule: (delayMillis: Long, action: () -> Unit) -> Unit,
    private val log: (String) -> Unit = {},
) {
    private data class ActiveIncident(
        val id: InterruptionNotificationIncidentId,
        val generation: Long,
        var lastSubmittedStage: InterruptionNotificationStage? = null,
        val dismissedStages: MutableSet<InterruptionNotificationStage> = mutableSetOf(),
    )

    private val lock = Any()
    private var generation = 0L
    private var activeIncident: ActiveIncident? = null

    fun startOrContinue(
        incidentId: InterruptionNotificationIncidentId,
        onStage: (InterruptionNotificationStage) -> Unit,
    ): Boolean {
        val active = synchronized(lock) {
            if (activeIncident?.id == incidentId) {
                return false
            }

            generation += 1L
            ActiveIncident(
                id = incidentId,
                generation = generation,
            ).also { created ->
                activeIncident = created
            }
        }

        InterruptionNotificationStage.entries.forEach { stage ->
            val elapsedMillis =
                (nowMillis() - incidentId.startedAtMillis).coerceAtLeast(0L)
            val delayMillis =
                (stage.elapsedThresholdMillis - elapsedMillis).coerceAtLeast(0L)

            log(
                "scheduled stage=$stage incident=$incidentId delayMillis=$delayMillis",
            )
            schedule(delayMillis) {
                log("evaluated stage=$stage incident=$incidentId")
                val shouldPost = synchronized(lock) {
                    val current = activeIncident
                    if (
                        current?.id != incidentId ||
                        current.generation != active.generation ||
                        current.lastSubmittedStage?.ordinal?.let { it >= stage.ordinal } == true
                    ) {
                        false
                    } else {
                        current.lastSubmittedStage = stage
                        true
                    }
                }

                if (shouldPost) {
                    onStage(stage)
                } else {
                    log(
                        "cancelled stage=$stage incident=$incidentId " +
                            "reason=incident inactive or stage already submitted",
                    )
                }
            }
        }

        return true
    }

    fun recordDismissed(
        incidentId: InterruptionNotificationIncidentId,
        stage: InterruptionNotificationStage,
    ) {
        synchronized(lock) {
            activeIncident
                ?.takeIf { active -> active.id == incidentId }
                ?.dismissedStages
                ?.add(stage)
        }
    }

    fun cancel(
        incidentId: InterruptionNotificationIncidentId? = null,
        reason: String = "incident ended",
    ) {
        val cancelledIncident = synchronized(lock) {
            if (incidentId != null && activeIncident?.id != incidentId) {
                return
            }

            generation += 1L
            activeIncident?.id.also {
                activeIncident = null
            }
        }

        if (cancelledIncident != null) {
            log("cancelled incident=$cancelledIncident reason=$reason")
        }
    }

    fun isActive(incidentId: InterruptionNotificationIncidentId): Boolean =
        synchronized(lock) {
            activeIncident?.id == incidentId
        }
}
