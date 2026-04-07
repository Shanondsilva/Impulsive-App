package com.impulsive.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.impulsive.app.data.db.UserProfileDao
import com.impulsive.app.data.repository.ImpulsiveRepository
import com.impulsive.app.service.SessionTimerService
import com.impulsive.app.ui.MainScaffold
import com.impulsive.app.ui.onboarding.OnboardingHost
import com.impulsive.app.ui.relapse.RelapseActivity
import com.impulsive.app.ui.theme.ImpulsiveTheme
import com.impulsive.app.ui.timer.SessionTimerActivity
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val userProfileDao: UserProfileDao by inject()
    private val repository: ImpulsiveRepository by inject()

    // Resolved on first onCreate; survives if Activity already exists (singleTop)
    private var openCheckIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        openCheckIn = intent?.getStringExtra("navigate_to") == "weekly_check_in"

        // Crash recovery: restore session if the process was killed mid-session
        recoverSessionIfNeeded()

        setContent {
            ImpulsiveTheme {
                val profile by userProfileDao.observe().collectAsState(initial = null)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when {
                        profile?.onboardingComplete == true -> {
                            MainScaffold(
                                identityAnchor = profile?.identityAnchor.orEmpty(),
                                openCheckIn = openCheckIn
                            )
                        }
                        else -> {
                            OnboardingHost(onComplete = { recreate() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle notification tap when Activity is already running (singleTop / SINGLE_TOP)
        if (intent.getStringExtra("navigate_to") == "weekly_check_in") {
            openCheckIn = true
            recreate() // simplest way to re-trigger Compose state with new openCheckIn
        }
    }

    override fun onResume() {
        super.onResume()
        // Check for unrecovered bypass on every resume — show relapse screen if found
        lifecycleScope.launch {
            val bypass = repository.getLatestUnrecovered()
            if (bypass != null) {
                RelapseActivity.start(this@MainActivity)
            }
        }
    }

    private fun recoverSessionIfNeeded() {
        if (SessionTimerService.isSessionActive) return // Already running in-process

        val prefs = getSharedPreferences(SessionTimerService.PREFS_NAME, MODE_PRIVATE)
        val savedStart = prefs.getLong(SessionTimerService.PREF_SESSION_START, 0L)
        if (savedStart <= 0L) return

        val elapsed = System.currentTimeMillis() - savedStart
        if (elapsed < SessionTimerService.SESSION_DURATION_MS) {
            val pkg = prefs.getString(SessionTimerService.PREF_LOCKED_PACKAGE, "") ?: ""
            // Restore static state so the timer UI resumes correctly
            SessionTimerService.sessionStartTime = savedStart
            SessionTimerService.lockedPackage    = pkg
            SessionTimerService.isSessionActive  = true
            SessionTimerService.start(this, pkg)
            startActivity(
                Intent(this, SessionTimerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } else {
            // Session window expired while process was dead — clear stale prefs
            prefs.edit()
                .remove(SessionTimerService.PREF_SESSION_START)
                .remove(SessionTimerService.PREF_LOCKED_PACKAGE)
                .apply()
        }
    }
}
