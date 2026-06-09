package com.maomaocake.grafanaalloyplugin.run

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.messages.Topic
import com.maomaocake.grafanaalloyplugin.validator.AlloyValidateAction
import com.maomaocake.grafanaalloyplugin.validator.AlloyValidatorSettings
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.deleteRecursively

/**
 * Owns the `alloy run` process for the current project. One process per project at a time —
 * starting a new run stops any previous run cleanly.
 *
 * Lifecycle as seen from the UI:
 *
 *  - `Stopped`   — nothing running.
 *  - `Starting`  — process launched, waiting for the HTTP server to accept a request.
 *  - `Ready`     — `<listenAddr>` responded; the embedded browser can load it.
 *  - `Failed`    — process exited (or never reached Ready in time).
 *
 * State changes broadcast on [TOPIC] so the Alloy UI tool window swaps between empty / loading /
 * browser views without polling.
 *
 * Process output is streamed into a Run-tool-window console keyed off [DefaultRunExecutor], so
 * users see the same Stop/Rerun toolbar they get from any Run Configuration. Closing the tab
 * stops the process via the descriptor.
 */
@Service(Service.Level.PROJECT)
class AlloyRunService(private val project: Project) : Disposable {

    private val LOG = Logger.getInstance(AlloyRunService::class.java)

    sealed class State {
        object Stopped : State()
        data class Starting(val listenAddr: String) : State()
        data class Ready(val listenAddr: String) : State()
        data class Failed(val reason: String) : State()
    }

    private val current = AtomicReference<State>(State.Stopped)
    private var processHandler: OSProcessHandler? = null
    private var storagePath: Path? = null

    val state: State get() = current.get()

    fun listenAddr(): String? = when (val s = current.get()) {
        is State.Starting -> s.listenAddr
        is State.Ready -> s.listenAddr
        else -> null
    }

    /**
     * Starts `alloy run <target>` on a fresh port (preferring 12345). Returns the chosen
     * `http://127.0.0.1:<port>` address, or null if start failed.
     *
     *  - [target] must be a `.alloy` file or a directory of `.alloy` files.
     *  - If a process is already running, it's stopped first.
     *  - The HTTP server reachability is probed off-EDT after start; the [State.Ready] event
     *    fires only when a connection succeeds.
     */
    fun start(target: File): String? {
        if (!SystemInfo.isMac && !SystemInfo.isLinux && !SystemInfo.isWindows) {
            broadcast(State.Failed("Unsupported OS"))
            notifyError("Run Alloy: unsupported OS")
            return null
        }
        stop()

        val port = AlloyPortAllocator.allocate(AlloyPortAllocator.DEFAULT_PORT)
        if (port == null) {
            val msg = "No free port in [${AlloyPortAllocator.DEFAULT_PORT}, " +
                "${AlloyPortAllocator.DEFAULT_PORT + 50}]"
            broadcast(State.Failed(msg))
            notifyError("Run Alloy: $msg")
            return null
        }
        val listenAddr = "http://127.0.0.1:$port"

        val settings = AlloyValidatorSettings.getInstance(project)
        val binary = settings.binaryPath.trim().ifEmpty { "alloy" }

        // Per-run tempdir for `alloy run`'s storage. Default behaviour is to write a
        // `data-alloy/` folder next to the config file, which clutters the project tree
        // and shows up in VCS diffs. Pointing storage at a tempdir keeps it out of sight
        // and lets us delete it cleanly on stop. We allocate before spawn so a failed
        // mkdtemp surfaces as a startup error rather than a silent revert to project dir.
        val tempStorage = try {
            Files.createTempDirectory("alloy-run-")
        } catch (t: Throwable) {
            LOG.warn("Failed to create temp storage dir for alloy run", t)
            broadcast(State.Failed(t.message ?: "couldn't create temp storage dir"))
            notifyError("Run Alloy: couldn't create temporary storage directory: ${t.message}")
            return null
        }

        val cmd = GeneralCommandLine(binary, "run").apply {
            withEnvironment("NO_COLOR", "1")
            withEnvironment("TERM", "dumb")
            addParameter("--server.http.listen-addr=127.0.0.1:$port")
            addParameter("--storage.path=${tempStorage.toAbsolutePath()}")
            addParameter("--stability.level=${settings.stabilityLevel.flag}")
            if (settings.communityComponentsEnabled) {
                addParameter("--feature.community-components.enabled")
            }
            addParameter(target.absolutePath)
            workDirectory = if (target.isDirectory) target else target.parentFile
        }

        val handler = try {
            OSProcessHandler(cmd)
        } catch (t: Throwable) {
            LOG.warn("Failed to start `$binary run`", t)
            cleanupStorage(tempStorage)
            val reason = t.message ?: t::class.java.simpleName
            broadcast(State.Failed(reason))
            notifyError(
                "Run Alloy: couldn't start `$binary run`. $reason. " +
                    "Check the binary path under Settings → Languages & Frameworks → Alloy → Validate.",
            )
            return null
        }
        ProcessTerminatedListener.attach(handler, project, "alloy run")
        storagePath = tempStorage
        broadcast(State.Starting(listenAddr))

        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                if (event.exitCode != 0) {
                    notifyError(
                        "Alloy exited with code ${event.exitCode}. " +
                            "Check the \"alloy run\" tab in the Run tool window for details.",
                    )
                }
                cleanupStorage(storagePath)
                storagePath = null
                broadcast(State.Failed("alloy run exited with code ${event.exitCode}"))
                processHandler = null
            }
        })

        // RunContentExecutor wires up the standard Run tool window: Stop button, Rerun
        // hook, kill-on-tab-close, console attached to the handler. Way less to maintain
        // than rolling our own RunContentDescriptor.
        ApplicationManager.getApplication().invokeLater {
            RunContentExecutor(project, handler)
                .withTitle("alloy run ${target.name}")
                .withActivateToolWindow(true)
                .withStop({ stop() }, { processHandler?.isProcessTerminated == false })
                .run()
        }
        processHandler = handler

        probeUntilReady(listenAddr, handler)
        return listenAddr
    }

    fun stop() {
        val handler = processHandler ?: run {
            cleanupStorage(storagePath)
            storagePath = null
            broadcast(State.Stopped)
            return
        }
        if (!handler.isProcessTerminated) {
            handler.destroyProcess()
            handler.waitFor(2_000)
        }
        // The process listener cleans storage on terminate, but if it never fired (e.g.
        // we got here before startNotify or the handler was attached late) sweep it now.
        cleanupStorage(storagePath)
        storagePath = null
        processHandler = null
        broadcast(State.Stopped)
    }

    /**
     * Recursive delete with `Throwable` swallowed — failure to clean a tempdir is annoying
     * but not user-actionable, and we don't want a transient lock on Windows to surface as
     * a notification storm. Logs at warn level so it's still discoverable.
     */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun cleanupStorage(path: Path?) {
        if (path == null) return
        try {
            path.deleteRecursively()
        } catch (t: Throwable) {
            LOG.warn("Failed to clean up alloy storage at $path", t)
        }
    }

    /**
     * Surfaces "the run never even got to a point where its output console was useful"
     * errors via a balloon. The console-based path takes over once the process is alive;
     * this only fires before that or when the process exits non-zero so users aren't left
     * staring at an unchanged tool window when something blew up silently.
     */
    private fun notifyError(message: String) {
        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup(AlloyValidateAction.NOTIFICATION_GROUP)
        group.createNotification(message, NotificationType.ERROR).notify(project)
    }

    /**
     * Hits `<listenAddr>/-/ready` (Alloy's standard readiness endpoint) every 250ms for up
     * to 30 s. Bumps state to [State.Ready] on first 2xx so the tool window can load the page.
     * Bails out if the process dies under us, surfacing a [State.Failed].
     */
    private fun probeUntilReady(listenAddr: String, handler: OSProcessHandler) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                if (handler.isProcessTerminated) return@executeOnPooledThread
                if (current.get() !is State.Starting) return@executeOnPooledThread
                if (httpReachable("$listenAddr/-/ready") || httpReachable(listenAddr)) {
                    broadcast(State.Ready(listenAddr))
                    return@executeOnPooledThread
                }
                Thread.sleep(250)
            }
            // Timed out without ever responding. Process might still be alive (very slow start);
            // we let it run but mark Failed so the UI doesn't sit on the spinner forever.
            if (current.get() is State.Starting) {
                broadcast(State.Failed("Alloy didn't accept a connection on $listenAddr within 30s"))
            }
        }
    }

    private fun httpReachable(url: String): Boolean = try {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 500
        conn.readTimeout = 500
        conn.requestMethod = "GET"
        val code = conn.responseCode
        conn.disconnect()
        code in 200..399
    } catch (_: Throwable) {
        false
    }

    private fun broadcast(next: State) {
        current.set(next)
        project.messageBus.syncPublisher(TOPIC).onStateChanged(next)
    }

    override fun dispose() {
        stop()
    }

    fun interface Listener {
        fun onStateChanged(state: State)
    }

    companion object {
        @JvmField
        val TOPIC: Topic<Listener> = Topic.create("Alloy run state", Listener::class.java)

        fun getInstance(project: Project): AlloyRunService =
            project.getService(AlloyRunService::class.java)
    }
}
