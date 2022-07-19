package exh.ui.captcha

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element

class AutoSolvingWebViewClient(
    activity: BrowserActionActivity,
    verifyComplete: (String) -> Boolean,
    injectScript: String?,
    headers: Map<String, String>,
) : HeadersInjectingWebViewClient(activity, verifyComplete, injectScript, headers) {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // Inject our custom script into the recaptcha iframes
        val lastPathSegment = request.url.pathSegments.lastOrNull()
        if (lastPathSegment == "anchor" || lastPathSegment == "bframe") {
            val oReq = request.toOkHttpRequest()
            val response = activity.httpClient.newCall(oReq).execute()
            val doc = response.asJsoup()
            doc.body().appendChild(Element("script").appendChild(DataNode(BrowserActionActivity.CROSS_WINDOW_SCRIPT_INNER)))
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                doc.toString().byteInputStream().buffered(),
            )
        }
        return super.shouldInterceptRequest(view, request)
    }
}
