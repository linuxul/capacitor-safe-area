package com.getcapacitor.community.safearea

import android.webkit.WebView
import com.getcapacitor.Bridge
import com.getcapacitor.WebViewListener

public class SafeAreaWebViewListener(private val bridge: Bridge) : WebViewListener() {
    override fun onPageCommitVisible(view: WebView?, url: String?) {
        val safeAreaPlugin = getSafeAreaInstance()

        if (safeAreaPlugin != null) {
            bridge.webView.evaluateJavascript(VIEWPORT_META_JS_FUNCTION) { res ->
                safeAreaPlugin.hasMetaViewportCover = res == "true"

                // Request new execution tree of `setOnApplyWindowInsetsListener`
                bridge.webView.requestApplyInsets()
            }
        }

        super.onPageCommitVisible(view, url)
    }

    private fun getSafeAreaInstance(): SafeAreaPlugin? = bridge.getPlugin("SafeArea")?.instance as SafeAreaPlugin?

    private companion object {
        private val VIEWPORT_META_JS_FUNCTION =
            """
            function capacitorSafeAreaCheckMetaViewport() {
                const meta = document.querySelectorAll("meta[name=viewport]");
                if (meta.length == 0) {
                    return false;
                }
                // get the last found meta viewport tag
                const metaContent = meta[meta.length - 1].content;
                return metaContent.includes("viewport-fit=cover");
            }

            capacitorSafeAreaCheckMetaViewport();
            """.trimIndent()
    }
}
