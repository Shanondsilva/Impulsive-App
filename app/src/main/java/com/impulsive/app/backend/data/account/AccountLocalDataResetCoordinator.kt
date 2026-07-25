package com.impulsive.app.backend.data.account

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.impulsive.app.backend.data.UserDataManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

enum class AccountLocalDataResetFailureStage {
    DeleteLocalData,
    RestartApp,
}

sealed interface AccountLocalDataResetResult {
    data object RestartRequested : AccountLocalDataResetResult
    data object AlreadyRunning : AccountLocalDataResetResult
    data object SessionChanged : AccountLocalDataResetResult

    data class Failed(
        val stage: AccountLocalDataResetFailureStage,
        val cause: Throwable,
    ) : AccountLocalDataResetResult
}

internal data class AccountLocalDataResetAccount(
    val uid: String,
    val isAnonymous: Boolean,
)

internal fun interface AccountLocalDataResetAccountProvider {
    fun currentAccount(): AccountLocalDataResetAccount?
}

internal interface AccountLocalDataResetOperations {
    suspend fun deleteAllData()
    fun restartApp()
}

class AccountLocalDataResetCoordinator internal constructor(
    private val accountProvider: AccountLocalDataResetAccountProvider,
    private val operations: AccountLocalDataResetOperations,
) {
    constructor(
        context: Context,
        firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    ) : this(
        accountProvider = FirebaseAccountLocalDataResetProvider(firebaseAuth),
        operations = AndroidAccountLocalDataResetOperations(
            UserDataManager(context.applicationContext),
        ),
    )

    private val operationMutex = Mutex()

    fun currentAuthenticatedAccountUid(): String? {
        val account = accountProvider.currentAccount() ?: return null

        if (account.isAnonymous) {
            return null
        }

        return account.uid
            .trim()
            .takeIf(String::isNotBlank)
    }

    suspend fun eraseAndRestart(
        expectedAccountUid: String,
    ): AccountLocalDataResetResult {
        val expectedUid = expectedAccountUid.trim()

        if (expectedUid.isBlank()) {
            return AccountLocalDataResetResult.SessionChanged
        }

        if (!operationMutex.tryLock()) {
            return AccountLocalDataResetResult.AlreadyRunning
        }

        return try {
            /*
             * Re-check immediately before destructive work. This prevents a
             * stale dialog callback from wiping data after the authenticated
             * account changed or signed out.
             */
            val currentAccount = accountProvider.currentAccount()

            if (
                currentAccount == null ||
                currentAccount.isAnonymous ||
                currentAccount.uid != expectedUid
            ) {
                return AccountLocalDataResetResult.SessionChanged
            }

            try {
                operations.deleteAllData()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                return AccountLocalDataResetResult.Failed(
                    stage = AccountLocalDataResetFailureStage.DeleteLocalData,
                    cause = error,
                )
            }

            try {
                /*
                 * UserDataManager intentionally does not clear Firebase Auth
                 * persistence. The authenticated account therefore survives
                 * this local reset and is observed again after cold restart.
                 */
                operations.restartApp()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                return AccountLocalDataResetResult.Failed(
                    stage = AccountLocalDataResetFailureStage.RestartApp,
                    cause = error,
                )
            }

            AccountLocalDataResetResult.RestartRequested
        } finally {
            operationMutex.unlock()
        }
    }
}

private class FirebaseAccountLocalDataResetProvider(
    private val firebaseAuth: FirebaseAuth,
) : AccountLocalDataResetAccountProvider {
    override fun currentAccount(): AccountLocalDataResetAccount? =
        firebaseAuth.currentUser?.let { user ->
            AccountLocalDataResetAccount(
                uid = user.uid,
                isAnonymous = user.isAnonymous,
            )
        }
}

private class AndroidAccountLocalDataResetOperations(
    private val userDataManager: UserDataManager,
) : AccountLocalDataResetOperations {
    override suspend fun deleteAllData() {
        userDataManager.deleteAllData()
    }

    override fun restartApp() {
        userDataManager.restartApp()
    }
}
