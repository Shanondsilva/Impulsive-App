package com.impulsive.app.backend.domain.model.journal

enum class JournalNoteType(val storageValue: String, val label: String) {
    Text("TEXT", "Note"),
    Checklist("CHECKLIST", "List"),
    Sketch("SKETCH", "Drawing"),
    Reminder("REMINDER", "Reminder");

    companion object {
        fun fromStorage(value: String): JournalNoteType {
            return entries.firstOrNull { it.storageValue == value } ?: Text
        }
    }
}
