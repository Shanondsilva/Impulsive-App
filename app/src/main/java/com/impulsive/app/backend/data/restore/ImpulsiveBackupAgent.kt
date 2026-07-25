package com.impulsive.app.backend.data.restore

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor

class ImpulsiveBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        AndroidRestoreProvenanceStore(this).markRestorePending()
    }
}