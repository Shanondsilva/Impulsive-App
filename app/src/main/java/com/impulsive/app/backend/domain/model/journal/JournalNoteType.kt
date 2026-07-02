package com.impulsive.app.backend.domain.model.journal

enum class JournalNoteType(
    val storageValue: String,
    val label: String,
) {
    Text(
        storageValue = "TEXT",
        label = "Note",
    ),
    Checklist(
        storageValue = "CHECKLIST",
        label = "List",
    ),
    Sketch(
        storageValue = "SKETCH",
        label = "Drawing",
    ),
    Reminder(
        storageValue = "REMINDER",
        label = "Reminder",
    );

    companion object {
        fun fromStorage(
            value: String,
        ): JournalNoteType {
            return entries.firstOrNull {
                it.storageValue == value
            } ?: Text
        }
    }
}
