package com.impulsive.app.frontend.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HelpFaqItem(
    val question: String,
    val answer: String,
)

private data class HelpFaqCategory(
    val title: String,
    val items: List<HelpFaqItem>,
)

private val helpFaqCategories = listOf(
    HelpFaqCategory(
        title = "Getting started",
        items = listOf(
            HelpFaqItem(
                question = "What is Impulsive?",
                answer = "Impulsive is a behaviour-change support app that helps you interrupt a difficult moment, create a pause, choose a safer next action, and understand your progress.",
            ),
            HelpFaqItem(
                question = "What should I set up first?",
                answer = "Complete onboarding, choose the apps you want to protect, and enable the requested protection permissions. You can review or change these choices later in Settings.",
            ),
            HelpFaqItem(
                question = "What happens during a difficult moment?",
                answer = "When Impulsive detects an attempted protected-app opening, it can interrupt the automatic route and offer a Gaming Task, a Reading Task, or the option to return home.",
            ),
            HelpFaqItem(
                question = "Is Impulsive a medical or therapy service?",
                answer = "No. Impulsive is a behaviour-change support tool. It is not a medical device, diagnosis tool, therapy service, crisis service, or replacement for qualified professional support.",
            ),
        ),
    ),
    HelpFaqCategory(
        title = "Protection and blocking",
        items = listOf(
            HelpFaqItem(
                question = "Why does Impulsive need usage access?",
                answer = "Usage access allows Impulsive to recognise when a selected protected app is opened so it can respond at the correct moment.",
            ),
            HelpFaqItem(
                question = "Why are additional protection permissions needed?",
                answer = "Android requires specific permissions for Impulsive to display an intervention quickly and keep protection active when the app is not already open.",
            ),
            HelpFaqItem(
                question = "Can I change my protected apps?",
                answer = "Yes. Open Settings and use the protection controls to review or change which apps are protected.",
            ),
            HelpFaqItem(
                question = "What should I check if protection stops working?",
                answer = "Open Settings and check usage access, interruption or display permission, notification permission, and battery restrictions. Android device settings can sometimes disable background protection.",
            ),
        ),
    ),
    HelpFaqCategory(
        title = "Focus Mode",
        items = listOf(
            HelpFaqItem(
                question = "What does Focus Mode do?",
                answer = "Focus Mode runs a timed focus session and strengthens protection during the period you selected.",
            ),
            HelpFaqItem(
                question = "Why does Focus show a persistent notification?",
                answer = "The notification helps Android keep the active Focus protection service running and lets you see that the session is still being guarded.",
            ),
            HelpFaqItem(
                question = "What happens when the Focus timer finishes?",
                answer = "The active session ends, the normal clock display returns, and the app records the completed Focus session.",
            ),
            HelpFaqItem(
                question = "What if Focus protection stops in the background?",
                answer = "Check notification permission and battery restrictions for Impulsive. Some Android devices aggressively stop background services unless the app is allowed to continue running.",
            ),
        ),
    ),
    HelpFaqCategory(
        title = "Reset Reading",
        items = listOf(
            HelpFaqItem(
                question = "What is Reset Reading?",
                answer = "Reset Reading gives you a guided reading pause. A valid session requires at least 90 seconds, reaching the end of the read, and completing the reflection.",
            ),
            HelpFaqItem(
                question = "Why do some reads include video or animation?",
                answer = "Videos, illustrations, and animations help explain the topic, hold attention, and provide a calmer distraction while the automatic urge loses strength.",
            ),
            HelpFaqItem(
                question = "How do Reset Reading rewards work?",
                answer = "A valid Reset Reading completion can receive its daily reward once per day. You can continue reading after that, but additional reads do not create extra daily Level Points.",
            ),
            HelpFaqItem(
                question = "Why does Impulsive sometimes ask whether a read was helpful?",
                answer = "Occasional helpfulness ratings help Impulsive understand which topics work better for you and recommend similar reads later.",
            ),
        ),
    ),
    HelpFaqCategory(
        title = "Games and progress",
        items = listOf(
            HelpFaqItem(
                question = "What is a Gaming Task?",
                answer = "A Gaming Task is a short interactive activity designed to interrupt autopilot and move your attention toward a controlled action.",
            ),
            HelpFaqItem(
                question = "How do I earn Level Points?",
                answer = "Level Points are awarded for valid task completions according to each task's reward rules. Some rewards are limited by daily or repeat-completion rules.",
            ),
            HelpFaqItem(
                question = "Why did a completed task give no extra points?",
                answer = "Some tasks limit repeated rewards to prevent point farming. The completion can still be recorded even when the available daily reward has already been earned.",
            ),
            HelpFaqItem(
                question = "Where can I see my progress?",
                answer = "Open the Progress section to review your level, completed activities, Reset Reading progress, and other available recovery statistics.",
            ),
        ),
    ),
    HelpFaqCategory(
        title = "Account, privacy, and data",
        items = listOf(
            HelpFaqItem(
                question = "What information does Impulsive store?",
                answer = "Your recovery data stays on your device. Reset sessions, journal notes, difficult moment records, levels, streaks, game history, protection setup, and blocked websites are stored only in encrypted storage on your phone. Impulsive's servers keep only account, subscription, and security records, plus crash reports that help us fix bugs.",
            ),
            HelpFaqItem(
                question = "Does Impulsive store my recovery progress on its servers?",
                answer = "No. Impulsive does not store recovery progress or behavioural history on its servers. You can read the full explanation in Settings under How your data is stored.",
            ),
            HelpFaqItem(
                question = "How does backup and restore work?",
                answer = "Cloud recovery creates an encrypted recovery copy protected by the password you choose. For accounts with Google, it is stored in private Google Drive app data; for other accounts, it is stored in your Impulsive account. Impulsive cannot read either copy. To restore, sign in to the same account and enter your recovery password. Android backup is separate: when Android backup is enabled and restores app data for the same Google account on a new or reinstalled device, it may restore selected Impulsive data automatically.",
            ),
            HelpFaqItem(
                question = "How do I export an encrypted backup?",
                answer = "Open Settings, go to the privacy and account section, and choose Export my Impulsive backup. You will pick a password and a place to save the file. The file is encrypted with your password and is never uploaded by Impulsive.",
            ),
            HelpFaqItem(
                question = "How do I import a backup on a new phone?",
                answer = "Install Impulsive, then open Settings and choose Import Impulsive backup before you start using the app. Pick your backup file and enter the password you chose when it was created. Import is only available before you begin using Impulsive on a device, so existing progress is never duplicated or overwritten.",
            ),
            HelpFaqItem(
                question = "What happens if I lose my backup password?",
                answer = "The backup file cannot be opened without it. Nobody can recover the file, including Impulsive, because the password never leaves your hands. That is a deliberate part of how your privacy is protected.",
            ),
            HelpFaqItem(
                question = "Does Impulsive see my browsing when website protection is on?",
                answer = "No. Website domain matching and blocking decisions happen directly on your device. When a site isn't blocked locally, Impulsive resolves the DNS request using encrypted DNS-over-HTTPS through Cloudflare (1.1.1.1 for Families), with AdGuard Family Protection as a fallback. Normal web traffic is not routed through an Impulsive remote VPN server, and these DNS requests are not sent to Impulsive servers. Blocked-site incidents are stored locally on your device.",
            ),
            HelpFaqItem(
                question = "How do I delete my account and data?",
                answer = "Open Settings, go to the privacy and account section, and choose the account deletion option. This deletes your account and subscription records from Impulsive's servers, removes your cloud recovery backup, and erases everything stored on this device. If Android backed up Impulsive through your Google account, you can clear that backup in your device's Google backup settings.",
            ),
            HelpFaqItem(
                question = "What should I include when reporting a problem?",
                answer = "Describe what you were doing, what you expected, what happened instead, and whether the issue repeats. The bug-report action also includes basic app and device information.",
            ),
        ),
    ),
)

@Composable
fun HelpFaqScreen(
    onBack: () -> Unit,
    onContactSupport: () -> Unit,
    onReportBug: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HelpFaqHeader(onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Find clear answers about protection, Focus Mode, Reset Reading, tasks, progress, and your data.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            helpFaqCategories.forEach { category ->
                HelpFaqCategoryCard(category = category)
            }

            StillNeedHelpCard(
                onContactSupport = onContactSupport,
                onReportBug = onReportBug,
            )
        }
    }
}

@Composable
private fun HelpFaqHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Text(
            text = "Help & FAQs",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun HelpFaqCategoryCard(
    category: HelpFaqCategory,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 2.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                category.items.forEachIndexed { index, item ->
                    HelpFaqQuestionRow(item = item)

                    if (index != category.items.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpFaqQuestionRow(
    item: HelpFaqItem,
) {
    var expanded by rememberSaveable(item.question) {
        mutableStateOf(false)
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "faq-arrow",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .semantics {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) {
                    expanded = !expanded
                }
                .padding(horizontal = 6.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.question,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse answer" else "Expand answer",
                modifier = Modifier.rotate(arrowRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = item.answer,
                modifier = Modifier.padding(
                    start = 6.dp,
                    end = 34.dp,
                    bottom = 16.dp,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StillNeedHelpCard(
    onContactSupport: () -> Unit,
    onReportBug: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Still need help?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Contact support for help using Impulsive, or send a bug report when something is not working correctly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onContactSupport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.MailOutline,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Contact support")
            }

            OutlinedButton(
                onClick = onReportBug,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Report a bug")
            }
        }
    }
}
