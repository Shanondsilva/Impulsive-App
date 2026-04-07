package com.impulsive.app.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.backup.BackupManager
import com.impulsive.app.data.repository.ImpulsiveRepository
import com.impulsive.app.eval.EvalExporter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class AppEntry(val label: String, val packageName: String)

// Full candidate list — only installed ones are shown
private val candidateApps = listOf(
    // Social
    AppEntry("Instagram",         "com.instagram.android"),
    AppEntry("TikTok",            "com.zhiliaoapp.musically"),
    AppEntry("Facebook",          "com.facebook.katana"),
    AppEntry("X (Twitter)",       "com.twitter.android"),
    AppEntry("Snapchat",          "com.snapchat.android"),
    AppEntry("Reddit",            "com.reddit.frontpage"),
    AppEntry("LinkedIn",          "com.linkedin.android"),
    AppEntry("Pinterest",         "com.pinterest"),
    AppEntry("BeReal",            "com.bereal.ft"),
    AppEntry("Discord",           "com.discord"),
    AppEntry("Threads",           "com.instagram.barcelona"),
    AppEntry("Telegram",          "org.telegram.messenger"),
    AppEntry("WhatsApp",          "com.whatsapp"),
    // Video / streaming
    AppEntry("YouTube",           "com.google.android.youtube"),
    AppEntry("YouTube Shorts",    "com.google.android.youtube"),
    AppEntry("Twitch",            "tv.twitch.android.app"),
    AppEntry("Netflix",           "com.netflix.mediaclient"),
    AppEntry("Disney+",           "com.disney.disneyplus"),
    AppEntry("Prime Video",       "com.amazon.avod.thirdpartyclient"),
    // Browsers
    AppEntry("Chrome",            "com.android.chrome"),
    AppEntry("Samsung Internet",  "com.sec.android.app.sbrowser"),
    AppEntry("Firefox",           "org.mozilla.firefox"),
    AppEntry("Brave",             "com.brave.browser"),
    AppEntry("Opera",             "com.opera.browser"),
    AppEntry("Microsoft Edge",    "com.microsoft.emmx"),
    // Shopping / other high-dopamine
    AppEntry("Amazon Shopping",   "com.amazon.mShop.android.shopping"),
    AppEntry("eBay",              "com.ebay.mobile"),
)

@Composable
fun SettingsScreen(
    repository: ImpulsiveRepository = koinInject(),
    evalExporter: EvalExporter = koinInject(),
    backupManager: BackupManager = koinInject()
) {
    val context = LocalContext.current
    val profile by repository.observeProfile().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var auditDismissed by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val twoHoursMs = 2 * 60 * 60 * 1000L
    val showAuditBanner = !auditDismissed &&
            (profile?.lastSessionCompleteTimestamp ?: 0L) > 0L &&
            (System.currentTimeMillis() - (profile?.lastSessionCompleteTimestamp ?: 0L)) < twoHoursMs

    // Filter to only apps installed on this device
    val installedApps = remember {
        val pm = context.packageManager
        candidateApps
            .distinctBy { it.packageName }
            .filter { app ->
                runCatching {
                    pm.getApplicationInfo(app.packageName, 0)
                    true
                }.getOrDefault(false)
            }
    }

    val monitored = profile?.monitoredApps
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp)
    ) {
        // Soft audit banner
        AnimatedVisibility(
            visible = showAuditBanner,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "HIGH-DOPAMINE STATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "You are in a high-dopamine state. Changes made now are often reactive, not rational.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { auditDismissed = true }
                    )
                    Text(
                        text = "Continue anyway",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { auditDismissed = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "SETTINGS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(24.dp))

        // Profile card
        profile?.let { p ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "${p.path} Path",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = p.identityAnchor.replace(",", ", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "MONITORED APPS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Only apps installed on your device are shown.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))

        if (installedApps.isEmpty()) {
            Text(
                text = "No supported apps detected on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                installedApps.forEach { app ->
                    val isEnabled = app.packageName in monitored
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    val current = profile ?: return@launch
                                    val updated = if (checked)
                                        (monitored + app.packageName).joinToString(",")
                                    else
                                        (monitored - app.packageName).joinToString(",")
                                    repository.saveProfile(current.copy(monitoredApps = updated))
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "DATA",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(12.dp))

        // Restore Data — file picker
        val restoreLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(uri) ?: return@launch
                    val tmp = java.io.File(context.cacheDir, "restore_tmp.enc")
                    tmp.outputStream().use { stream.copyTo(it) }
                    backupManager.restore(tmp)
                    tmp.delete()
                    Toast.makeText(context, "Restore complete. Restart app.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Export Data row
        SettingsActionRow(
            label = "Export Data",
            subtitle = "Save all eval data as JSON",
            onClick = {
                scope.launch {
                    try {
                        val file = evalExporter.exportToJson()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM,
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share export"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        Spacer(Modifier.height(4.dp))
        // Backup Data row
        SettingsActionRow(
            label = "Backup Data",
            subtitle = "Encrypted backup of full database",
            onClick = {
                scope.launch {
                    try {
                        val file = backupManager.backup()
                        Toast.makeText(context, "Backup saved: ${file.name}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        Spacer(Modifier.height(4.dp))
        // Restore Data row
        SettingsActionRow(
            label = "Restore Data",
            subtitle = "Restore from encrypted backup file",
            onClick = { restoreLauncher.launch("*/*") }
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = "ABOUT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(12.dp))
        SettingsActionRow(
            label = "Privacy Policy",
            subtitle = "No data ever leaves your device",
            onClick = { showPrivacyDialog = true }
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "VERSION 1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            letterSpacing = 2.sp
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
            title = {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Impulsive does not collect, transmit, or share any personal data. Everything stays on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "What is stored locally",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Your session logs, triggers, identity anchors, tapering history, and weekly targets are stored in an on-device database. No names, emails, or identifiers are ever stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "What Impulsive does not do",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No internet connection. No analytics. No crash reporting. No ads. No accounts. No third-party SDKs that transmit data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Deleting your data",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Go to Android Settings > Apps > Impulsive > Storage > Clear Data to permanently remove everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
