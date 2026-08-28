package com.geckoflux.engine.delegates

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.geckoflux.config.AppConfiguration
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Intercepts link clicks and navigation requests.
 * Keeps YouTube / Google Auth in-app, and routes external links to the default Android browser.
 */
class AppNavigationDelegate(
    private val activity: Activity,
    private val config: AppConfiguration,
    private val onHistoryStateChanged: ((canGoBack: Boolean, canGoForward: Boolean) -> Unit)? = null
) : GeckoSession.NavigationDelegate {

    companion object {
        private const val TAG = "AppNavDelegate"
    }

    var canGoBack: Boolean = false
        private set
    var canGoForward: Boolean = false
        private set

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny>? {
        val uri = Uri.parse(request.uri)
        val host = uri.host?.lowercase() ?: ""
        val scheme = uri.scheme?.lowercase() ?: ""

        // Allow data URIs, blob URIs, about:blank, or extension URIs
        if (scheme == "data" || scheme == "blob" || scheme == "about" || scheme == "resource") {
            return GeckoResult.allow()
        }

        // Check if the domain is in the allowed list
        val isAllowed = config.allowedDomains.any { allowedDomain ->
            host == allowedDomain || host.endsWith(".$allowedDomain")
        }

        return if (isAllowed) {
            Log.d(TAG, "Allowing in-app navigation to: ${request.uri}")
            GeckoResult.allow()
        } else {
            // Security: Only forward valid web URLs (http/https) to external browser
            if (scheme == "http" || scheme == "https") {
                Log.d(TAG, "Routing external URL to system browser: ${request.uri}")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch external browser for URI: ${request.uri}", e)
                }
            } else {
                Log.w(TAG, "Blocked navigation to unapproved scheme: $scheme")
            }
            GeckoResult.deny()
        }
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        this.canGoBack = canGoBack
        onHistoryStateChanged?.invoke(this.canGoBack, this.canGoForward)
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        this.canGoForward = canGoForward
        onHistoryStateChanged?.invoke(this.canGoBack, this.canGoForward)
    }

    override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
        session.loadUri(uri)
        return null
    }
}
