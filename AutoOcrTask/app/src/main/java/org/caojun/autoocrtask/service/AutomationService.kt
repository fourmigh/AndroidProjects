package org.caojun.autoocrtask.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.caojun.autoocrtask.R
import org.caojun.autoocrtask.automation.AutomationLauncher
import org.caojun.autoocrtask.automation.AutomationLocator
import org.caojun.autoocrtask.automation.AutomationResult
import org.caojun.autoocrtask.automation.AutomationRunner
import org.caojun.autoocrtask.automation.FailureReason
import org.caojun.autoocrtask.automation.StepDescriptions
import org.caojun.autoocrtask.data.AppNavigation
import org.caojun.autoocrtask.data.AutomationTask
import org.caojun.autoocrtask.data.AutomationTaskStore
import org.caojun.autoocrtask.ui.MainActivity
import org.caojun.shizuku.RikkaShizukuGateway
import org.caojun.shizuku.automation.ShizukuUiAutomation
import org.caojun.shotocr.ocr.OcrEngine

class AutomationService : LifecycleService() {

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val ACTION_STOP = "org.caojun.autoocrtask.action.STOP_TASK"

        private const val CHANNEL_ID = "automation_task"
        private const val NOTIFICATION_ID = 2001
        private const val LAUNCH_SETTLE_MS = 1_200L
    }

    @Volatile
    private var stopRequested = false

    private var started = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.run_preparing), ongoing = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            updateNotification(getString(R.string.run_stopped), ongoing = true)
            if (!started) finish(getString(R.string.run_stopped))
            return START_NOT_STICKY
        }

        if (!started) {
            started = true
            val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
            if (taskId.isNullOrBlank()) {
                finish(getString(R.string.run_task_missing))
                return START_NOT_STICKY
            }
            lifecycleScope.launch { runTask(taskId) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRequested = true
        super.onDestroy()
    }

    private suspend fun runTask(taskId: String) {
        val task = AutomationTaskStore(this).find(taskId)
        if (task == null) {
            finish(getString(R.string.run_task_missing))
            return
        }

        updateNotification(getString(R.string.run_task, task.name), ongoing = true)

        val gateway = RikkaShizukuGateway(this)
        if (!gateway.isRunning() || !gateway.isPermissionGranted()) {
            finish(getString(R.string.run_no_shizuku))
            return
        }

        if (!AutomationLauncher.isInstalled(this, task.packageName)) {
            finish(getString(R.string.run_package_missing))
            return
        }

        if (stopRequested) {
            finish(getString(R.string.run_stopped))
            return
        }

        if (!AutomationLauncher.launch(this, task)) {
            finish(getString(R.string.run_launch_failed))
            return
        }
        delay(LAUNCH_SETTLE_MS)

        var ocr: OcrEngine? = null
        val ocrProvider: suspend () -> OcrEngine? = {
            if (ocr == null) {
                val engine = OcrEngine(applicationContext)
                withContext(Dispatchers.Default) { engine.initialize() }
                ocr = engine
            }
            ocr
        }

        var failure: String? = null
        val result: AutomationResult? = try {
            ShizukuUiAutomation(this).withSession { session ->
                val locator = AutomationLocator(session) { ocrProvider() }
                val runner = AutomationRunner(
                    context = this@AutomationService,
                    session = session,
                    locator = locator,
                    isStopped = { stopRequested },
                    onProgress = { current, total, step ->
                        updateNotification(
                            getString(
                                R.string.run_step,
                                current,
                                total,
                                StepDescriptions.describe(this@AutomationService, step)
                            ),
                            ongoing = true
                        )
                    }
                )
                runner.run(task)
            }
        } catch (t: Throwable) {
            failure = t.message ?: t.javaClass.simpleName
            null
        } finally {
            runCatching { withContext(Dispatchers.IO) { ocr?.release() } }
        }

        if (failure != null) {
            finish(getString(R.string.run_error, failure))
            return
        }
        handleResult(task, result)
    }

    private fun handleResult(task: AutomationTask, result: AutomationResult?) {
        when {
            result == null -> finish(getString(R.string.run_start_failed))
            result.success -> finish(getString(R.string.run_done, task.name))
            result.stopped -> finish(getString(R.string.run_stopped))
            else -> finish(failureText(task, result))
        }
    }

    private fun failureText(task: AutomationTask, result: AutomationResult): String {
        val step = result.failedStep
        val base = when (result.reason) {
            FailureReason.TIMEOUT -> {
                val target = step?.text?.takeIf { it.isNotBlank() }
                    ?: step?.packageName.orEmpty()
                getString(R.string.run_timeout, target)
            }

            FailureReason.TAP_FAILED -> getString(R.string.run_tap_failed)
            FailureReason.PACKAGE_MISSING -> getString(R.string.run_package_missing)
            FailureReason.STOPPED -> getString(R.string.run_stopped)
            null -> getString(R.string.run_failed, task.name)
        }
        return if (step != null) {
            "${StepDescriptions.typeLabel(this, step.type)}: $base"
        } else {
            getString(R.string.run_failed, task.name)
        }
    }

    private fun finish(text: String) {
        updateNotification(text, ongoing = false)
        runCatching { stopForeground(Service.STOP_FOREGROUND_DETACH) }
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        returnToApp()
        stopSelf()
    }

    /** 任务结束（完成/失败/停止）后自动回到任务列表；被拦截时保留通知可点击返回。 */
    private fun returnToApp() {
        runCatching {
            AppNavigation.requestTasks(this)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    private fun updateNotification(text: String, ongoing: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(text, ongoing))
    }

    private fun buildNotification(text: String, ongoing: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.run_notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .apply {
                if (ongoing) {
                    addAction(0, getString(R.string.run_stop), stopIntent)
                }
            }
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.run_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.run_channel_desc)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
