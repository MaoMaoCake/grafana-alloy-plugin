package com.maomaocake.grafanaalloyplugin.run

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * "Alloy UI" tool window. Hosts a [JBCefBrowser] pointed at a running `alloy run` instance.
 * Subscribes to [AlloyRunService.TOPIC] and swaps between four cards:
 *
 *  - `EMPTY`  — no run in progress; explains how to start one.
 *  - `LOAD`   — process up, waiting for HTTP to come alive.
 *  - `WEB`    — JCEF browser at the listen address.
 *  - `NOJCEF` — JCEF unavailable in this runtime; offers to open in the system browser.
 *
 * Falls back gracefully to "Open in browser" when [JBCefApp.isSupported] is false (some
 * Linux distros and stripped-down IDE builds ship without JCEF). The empty/loading cards
 * stay text-only so they cost nothing on startup.
 */
class AlloyUiToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AlloyUiPanel(project)
        Disposer.register(toolWindow.disposable, panel)
        val content = ContentFactory.getInstance().createContent(panel.root, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class AlloyUiPanel(private val project: Project) : Disposable {

    private val cards = CardLayout()
    val root = JPanel(cards)

    private val emptyCard: JComponent = buildEmptyCard()
    private val loadingCard: JComponent = buildLoadingCard()
    private val webHolder = JPanel(BorderLayout())
    private val noJcefCard: JComponent = buildNoJcefCard()

    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private var loadedAddr: String? = null

    /**
     * Mutable nav state, updated by the JCEF load handler. We can't synchronously query
     * `canGoBack()` etc. because CEF events fire on a non-EDT thread; copying the booleans
     * here lets the toolbar's `update()` (which runs on EDT) read them lock-free.
     */
    @Volatile private var canGoBack = false
    @Volatile private var canGoForward = false
    @Volatile private var isLoading = false

    init {
        root.add(emptyCard, EMPTY)
        root.add(loadingCard, LOAD)
        root.add(webHolder, WEB)
        root.add(noJcefCard, NOJCEF)

        if (browser != null) {
            webHolder.add(buildBrowserToolbar(), BorderLayout.NORTH)
            webHolder.add(browser.component, BorderLayout.CENTER)
            Disposer.register(this, browser)
            browser.jbCefClient.addLoadHandler(
                object : CefLoadHandlerAdapter() {
                    override fun onLoadingStateChange(
                        b: CefBrowser?,
                        loading: Boolean,
                        back: Boolean,
                        forward: Boolean,
                    ) {
                        canGoBack = back
                        canGoForward = forward
                        isLoading = loading
                    }
                },
                browser.cefBrowser,
            )
        }

        cards.show(root, EMPTY)
        applyState(AlloyRunService.getInstance(project).state)

        project.messageBus.connect(this).subscribe(
            AlloyRunService.TOPIC,
            AlloyRunService.Listener { newState ->
                ApplicationManager.getApplication().invokeLater { applyState(newState) }
            },
        )
    }

    private fun applyState(state: AlloyRunService.State) {
        when (state) {
            is AlloyRunService.State.Stopped -> {
                cards.show(root, EMPTY)
                loadedAddr = null
            }
            is AlloyRunService.State.Starting -> {
                cards.show(root, LOAD)
            }
            is AlloyRunService.State.Ready -> {
                if (browser == null) {
                    cards.show(root, NOJCEF)
                } else {
                    if (loadedAddr != state.listenAddr) {
                        browser.loadURL(state.listenAddr)
                        loadedAddr = state.listenAddr
                    }
                    cards.show(root, WEB)
                }
            }
            is AlloyRunService.State.Failed -> {
                cards.show(root, EMPTY)
                loadedAddr = null
            }
        }
    }

    /**
     * Back / Forward / Reload / Home navigation strip above the browser. Home goes back to
     * the current Alloy listen address — important because the embedded UI links out to
     * grafana.com docs and there's no other way to recover from those without right-click.
     *
     * Caller-side note: actions are bare `AnAction` lambdas rather than registered IDs since
     * the toolbar is local to this panel and doesn't need to be invokable from anywhere else.
     */
    private fun buildBrowserToolbar(): JComponent {
        val back = object : AnAction("Back", "Go back", AllIcons.Actions.Back) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = browser != null && canGoBack
            }
            override fun actionPerformed(e: AnActionEvent) {
                browser?.cefBrowser?.goBack()
            }
        }
        val forward = object : AnAction("Forward", "Go forward", AllIcons.Actions.Forward) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = browser != null && canGoForward
            }
            override fun actionPerformed(e: AnActionEvent) {
                browser?.cefBrowser?.goForward()
            }
        }
        val reload = object : AnAction("Reload", "Reload the current page", AllIcons.Actions.Refresh) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = browser != null
            }
            override fun actionPerformed(e: AnActionEvent) {
                browser?.cefBrowser?.reload()
            }
        }
        val home = object : AnAction("Home", "Back to the Alloy UI", AllIcons.Nodes.HomeFolder) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = AlloyRunService.getInstance(project).listenAddr() != null
            }
            override fun actionPerformed(e: AnActionEvent) {
                val addr = AlloyRunService.getInstance(project).listenAddr() ?: return
                browser?.loadURL(addr)
            }
        }
        val group = DefaultActionGroup(back, forward, reload, home)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, /* horizontal = */ true)
        toolbar.targetComponent = webHolder
        return toolbar.component
    }

    private fun buildEmptyCard(): JComponent = JPanel(BorderLayout()).apply {
        add(
            JBLabel(
                "<html><div style='text-align:center;padding:24px;'>" +
                    "<b>No Alloy instance running.</b><br><br>" +
                    "Right-click an <code>.alloy</code> file or folder → <b>Run with Alloy</b>." +
                    "</div></html>",
                SwingConstants.CENTER,
            ),
            BorderLayout.CENTER,
        )
    }

    private fun buildLoadingCard(): JComponent = JPanel(BorderLayout()).apply {
        add(
            JBLabel(
                "<html><div style='text-align:center;padding:24px;'>" +
                    "<b>Starting Alloy…</b><br><br>" +
                    "<i>Waiting for the HTTP server to come up.</i>" +
                    "</div></html>",
                SwingConstants.CENTER,
            ),
            BorderLayout.CENTER,
        )
    }

    private fun buildNoJcefCard(): JComponent = JPanel(BorderLayout()).apply {
        val msg = JBLabel(
            "<html><div style='text-align:center;padding:24px;'>" +
                "<b>Embedded browser unavailable.</b><br><br>" +
                "<i>This IDE runtime doesn't ship JCEF. " +
                "Open the Alloy UI in your system browser instead.</i>" +
                "</div></html>",
            SwingConstants.CENTER,
        )
        val openButton = JButton("Open Alloy UI in browser").apply {
            addActionListener {
                AlloyRunService.getInstance(project).listenAddr()?.let { BrowserUtil.browse(it) }
            }
        }
        val buttonRow = JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(openButton) }
        add(msg, BorderLayout.CENTER)
        add(buttonRow, BorderLayout.SOUTH)
    }

    override fun dispose() {}

    private companion object {
        const val EMPTY = "empty"
        const val LOAD = "load"
        const val WEB = "web"
        const val NOJCEF = "nojcef"
    }
}
