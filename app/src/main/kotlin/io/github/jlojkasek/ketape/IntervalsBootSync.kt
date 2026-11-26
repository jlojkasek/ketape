package io.github.jlojkasek.ketape

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

private const val BOOT_SYNC_JOB = 41
private const val BOOT_SYNC_MAX_ATTEMPTS = 3
private const val TAG = "KetapeBootSync"

internal class BootSyncAttempts(context: Context) {
    private val preferences = context.getSharedPreferences("intervals_boot_sync", Context.MODE_PRIVATE)

    fun reset() {
        preferences.edit().remove("attempts").apply()
    }

    fun start(): Int? {
        val started = preferences.getInt("attempts", 0)
        if (started >= BOOT_SYNC_MAX_ATTEMPTS) return null
        return (started + 1).also { preferences.edit().putInt("attempts", it).apply() }
    }
}

class IntervalsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val records = IntervalsRecords(context)
        if (!records.connected || records.keyRejected) return
        BootSyncAttempts(context).reset()
        val job = JobInfo.Builder(BOOT_SYNC_JOB, ComponentName(context, IntervalsBootSync::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) {
            Log.i(TAG, "Boot sync scheduled")
        } else {
            Log.w(TAG, "Could not schedule boot sync")
        }
    }
}

class IntervalsBootSync : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running: Job? = null
    private var canRetry = false

    override fun onStartJob(params: JobParameters): Boolean {
        canRetry = false
        val attempt = BootSyncAttempts(this).start() ?: return false
        canRetry = attempt < BOOT_SYNC_MAX_ATTEMPTS
        running = scope.launch {
            val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val retry = refreshIntervalsSinceBoot(IntervalsRecords(this@IntervalsBootSync),
                ReservePreferences(this@IntervalsBootSync), bootTime)
            Log.i(TAG, when {
                !retry -> "Boot sync finished"
                canRetry -> "Boot sync will retry"
                else -> "Boot sync attempt limit reached"
            })
            running = null
            jobFinished(params, retry && canRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        running = null
        return canRetry
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

internal suspend fun refreshIntervalsSinceBoot(
    records: IntervalsRecords,
    reserve: ReservePreferences,
    bootTime: Long
): Boolean {
    if (!records.connected || records.keyRejected) return false
    if (ReserveMonitor.rideInProgress) return true
    val retryReferences = retryBootSync("Palmarès") { records.sync(refreshSince = bootTime) }
    val retryReserve = if (!reserve.manual && records.connected && !records.keyRejected) {
        retryBootSync("Réserve") { records.syncReserve(bootTime) }
    } else false
    return records.connected && !records.keyRejected && (retryReferences || retryReserve)
}

private suspend fun retryBootSync(section: String, sync: suspend () -> Unit): Boolean = try {
    sync()
    Log.i(TAG, "$section up to date for this boot")
    false
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Log.i(TAG, "$section sync deferred or unavailable")
    when (error) {
        is IntervalsKeyRejected -> false
        is IntervalsHttpError -> error.status == 408 || error.status == 429 || error.status >= 500
        is ReserveSyncError -> error.messageRes == R.string.reserve_sync_after_ride
        is IOException -> true
        else -> false
    }
}
