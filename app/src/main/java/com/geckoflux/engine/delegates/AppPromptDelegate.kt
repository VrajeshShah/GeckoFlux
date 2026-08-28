package com.geckoflux.engine.delegates

import android.app.Activity
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AlertPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ButtonPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSession.PromptDelegate.TextPrompt

/**
 * Material 3 PromptDelegate handling JavaScript alerts, confirmations, prompts,
 * and authentication dialogs cleanly within native Android dialogs.
 */
class AppPromptDelegate(private val activity: Activity) : PromptDelegate {

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: AlertPrompt
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()

        activity.runOnUiThread {
            MaterialAlertDialogBuilder(activity)
                .setTitle(prompt.title ?: "Alert")
                .setMessage(prompt.message)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    result.complete(prompt.dismiss())
                }
                .setOnCancelListener {
                    result.complete(prompt.dismiss())
                }
                .show()
        }

        return result
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: ButtonPrompt
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()

        activity.runOnUiThread {
            MaterialAlertDialogBuilder(activity)
                .setTitle(prompt.title ?: "Confirm")
                .setMessage(prompt.message)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    result.complete(prompt.confirm(ButtonPrompt.Type.POSITIVE))
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                    result.complete(prompt.confirm(ButtonPrompt.Type.NEGATIVE))
                }
                .setOnCancelListener {
                    result.complete(prompt.dismiss())
                }
                .show()
        }

        return result
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: TextPrompt
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()

        activity.runOnUiThread {
            val input = EditText(activity).apply {
                setText(prompt.defaultValue)
            }

            MaterialAlertDialogBuilder(activity)
                .setTitle(prompt.title ?: "Input")
                .setMessage(prompt.message)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    result.complete(prompt.confirm(input.text.toString()))
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                    result.complete(prompt.dismiss())
                }
                .setOnCancelListener {
                    result.complete(prompt.dismiss())
                }
                .show()
        }

        return result
    }
}
