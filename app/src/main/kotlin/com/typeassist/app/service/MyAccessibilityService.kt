package com.typeassist.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import com.google.gson.Gson
import com.typeassist.app.api.CloudflareApiClient
import com.typeassist.app.api.GeminiApiClient
import com.typeassist.app.api.OpenRouterApiClient
import com.typeassist.app.data.AppConfig
import com.typeassist.app.data.HistoryManager
import okhttp3.OkHttpClient
import java.util.regex.Pattern

class MyAccessibilityService : AccessibilityService() {

    private val client = OkHttpClient()
    private val geminiApiClient = GeminiApiClient(client)
    private val cloudflareApiClient = CloudflareApiClient(client)
    private val openRouterApiClient = OpenRouterApiClient(client)

    private var windowManager: WindowManager? = null
    private var loadingView: FrameLayout? = null
    private var undoView: FrameLayout? = null

    private var lastNode: AccessibilityNodeInfo? = null
    private var originalTextCache: String = ""
    private var undoCacheTimestamp: Long = 0L

    private val undoHandler = Handler(Looper.getMainLooper())
    private val hideUndoRunnable = Runnable { hideUndoButton() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val inputNode = event.source ?: return
        val prefs = getSharedPreferences("GeminiConfig", Context.MODE_PRIVATE)
        val configJson = prefs.getString("config_json", null) ?: return

        try {
            val config = Gson().fromJson(configJson, AppConfig::class.java)
            if (!config.isAppEnabled) return

            val currentText = inputNode.text?.toString() ?: return

            /* ---------- INLINE COMMANDS ---------- */
            for (inlineCommand in config.inlineCommands) {
                val regex = Pattern.compile(buildRegexFromInlinePattern(inlineCommand.pattern))
                val matcher = regex.matcher(currentText)

                if (matcher.find()) {
                    val fullMatch = matcher.group(0) ?: return
                    val userPrompt = matcher.group(1) ?: return

                    originalTextCache = currentText
                    HistoryManager.add(originalTextCache)
                    lastNode = inputNode
                    undoCacheTimestamp = System.currentTimeMillis()

                    showLoading()
                    hideUndoButton()

                    performAICall(config, inlineCommand.prompt, userPrompt) { result ->
                        hideLoading()
                        result.onSuccess {
                            pasteText(inputNode, currentText.replace(fullMatch, it))
                            showUndoButton()
                        }.onFailure {
                            showToast(it.message ?: "AI Error")
                        }
                    }
                    return
                }
            }

            /* ---------- TRAILING TRIGGERS ---------- */
            for (trigger in config.triggers) {
                if (currentText.endsWith(trigger.pattern)) {
                    val textToProcess =
                        currentText.substring(
                            0,
                            currentText.length - trigger.pattern.length
                        ).trim()

                    if (textToProcess.length < 2) return

                    originalTextCache = textToProcess
                    HistoryManager.add(originalTextCache)
                    lastNode = inputNode
                    undoCacheTimestamp = System.currentTimeMillis()

                    showLoading()
                    hideUndoButton()

                    performAICall(config, trigger.prompt, textToProcess) { result ->
                        hideLoading()
                        result.onSuccess {
                            pasteText(inputNode, it)
                            showUndoButton()
                        }.onFailure {
                            showToast(it.message ?: "AI Error")
                        }
                    }
                    return
                }
            }

        } catch (_: Exception) {
            // silent fail
        }
    }

    /* ================= AI ROUTER ================= */

    private fun performAICall(
        config: AppConfig,
        prompt: String,
        userText: String,
        callback: (Result<String>) -> Unit
    ) {
        when (config.provider) {

            "cloudflare" -> {
                cloudflareApiClient.callCloudflare(
                    config.cloudflareConfig.accountId,
                    config.cloudflareConfig.apiToken,
                    config.cloudflareConfig.model,
                    prompt,
                    userText,
                    callback
                )
            }

            "openrouter" -> {
                openRouterApiClient.callOpenRouter(
                    apiKey = config.apiKey,
                    model = config.model.ifBlank {
                        "mistralai/mistral-7b-instruct"
                    },
                    systemPrompt = prompt,
                    userText = userText,
                    callback = callback
                )
            }

            else -> {
                geminiApiClient.callGemini(
                    config.apiKey,
                    config.model,
                    prompt,
                    userText,
                    config.generationConfig.temperature,
                    config.generationConfig.topP,
                    callback
                )
            }
        }
    }

    /* ================= UI HELPERS ================= */

    private fun pasteText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun showLoading() {
        Handler(Looper.getMainLooper()).post {
            if (loadingView != null) return@post
            loadingView = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    setColor(0x99000000.toInt())
                    cornerRadius = 40f
                }
                addView(ProgressBar(this@MyAccessibilityService))
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            windowManager?.addView(loadingView, params)
        }
    }

    private fun hideLoading() {
        Handler(Looper.getMainLooper()).post {
            loadingView?.let {
                windowManager?.removeView(it)
                loadingView = null
            }
        }
    }

    private fun showUndoButton() {
        Handler(Looper.getMainLooper()).post {
            if (undoView != null) return@post

            undoView = FrameLayout(this)
            val btn = Button(this).apply {
                text = "UNDO"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(0xEE333333.toInt())
                    cornerRadius = 50f
                }
                setOnClickListener { performUndo() }
            }
            undoView!!.addView(btn)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER

            windowManager?.addView(undoView, params)
            undoHandler.postDelayed(hideUndoRunnable, 5000)
        }
    }

    private fun hideUndoButton() {
        undoHandler.removeCallbacks(hideUndoRunnable)
        Handler(Looper.getMainLooper()).post {
            undoView?.let {
                windowManager?.removeView(it)
                undoView = null
            }
        }
    }

    private fun performUndo() {
        val elapsed = System.currentTimeMillis() - undoCacheTimestamp
        if (lastNode != null && originalTextCache.isNotEmpty() && elapsed < 120000) {
            pasteText(lastNode!!, originalTextCache)
            showToast("Undone")
        }
        hideUndoButton()
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildRegexFromInlinePattern(pattern: String): String {
        return Pattern.quote(pattern)
            .replace("%", "\\E(.+?)\\Q")
            .replace("\\Q\\E", "")
    }

    override fun onInterrupt() {
        hideLoading()
        hideUndoButton()
    }
}