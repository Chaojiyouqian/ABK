package com.abk.kernel.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abk.kernel.MainActivity
import com.abk.kernel.data.model.RootGrantProfileRecoveryRecord
import com.abk.kernel.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DebugTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_TRIGGER_RECOVERY) return

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "com.example.test"
        val uid = intent.getIntExtra(EXTRA_UID, 10086)
        val label = intent.getStringExtra(EXTRA_LABEL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: packageName

        // Write to DataStore via PreferencesRepository (not directly — it owns the singleton DataStore)
        GlobalScope.launch(Dispatchers.IO) {
            val repo = PreferencesRepository(context)
            repo.savePendingRootGrantProfileRecovery(
                RootGrantProfileRecoveryRecord(
                    packageName = packageName,
                    uid = uid,
                    label = label
                )
            )
        }

        // Launch MainActivity (CLEAR_TOP destroys and recreates, triggering checkRoot -> handlePendingRootGrantProfileRecovery)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launchIntent)
    }

    companion object {
        const val ACTION_DEBUG_TRIGGER_RECOVERY = "com.abk.kernel.action.DEBUG_TRIGGER_RECOVERY"
        const val EXTRA_PACKAGE_NAME = "packageName"
        const val EXTRA_UID = "uid"
        const val EXTRA_LABEL = "label"
    }
}
