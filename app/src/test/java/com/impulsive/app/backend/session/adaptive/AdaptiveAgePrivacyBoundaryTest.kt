package com.impulsive.app.backend.session.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AdaptiveAgePrivacyBoundaryTest {
    @Test
    fun adaptiveAndSupportCycleContractsContainNoAgeOrBirthdayFields() {
        val roots = listOf(
            "src/main/java/com/impulsive/app/backend/domain/model/adaptive",
            "src/main/java/com/impulsive/app/backend/domain/repository/adaptive/AdaptiveSupportCycleRepository.kt",
        )
        val forbidden = Regex("\\b(age|birthday|birthdate|derivedAge)\\b", RegexOption.IGNORE_CASE)
        roots.flatMap { path ->
            val file = File(path)
            if (file.isDirectory) file.walkTopDown().filter { it.isFile }.toList() else listOf(file)
        }.forEach { file ->
            assertFalse("Age data is forbidden in ${file.path}", forbidden.containsMatchIn(file.readText()))
        }
    }

    @Test
    fun manifestAddsNoPeopleContactsOrBirthdayScope() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        listOf("READ_CONTACTS", "WRITE_CONTACTS", "people.googleapis.com", "birthday")
            .forEach { forbidden -> assertFalse(manifest.contains(forbidden, ignoreCase = true)) }
    }
}
