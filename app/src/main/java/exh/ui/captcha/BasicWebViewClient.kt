package exh.ui.captcha

import android.webkit.WebView
import android.webkit.WebViewClient

open class BasicWebViewClient(
    protected val activity: BrowserActionActivity,
    protected val verifyComplete: (String) -> Boolean,
    private val injectScript: String?,
) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        if (verifyComplete(url)) {
            activity.finish()
        } else {
            if (injectScript != null) {
                view.evaluateJavascript("(function() {$injectScript})();", null)
            }
        }
    }
}
