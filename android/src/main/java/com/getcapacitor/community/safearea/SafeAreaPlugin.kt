package com.getcapacitor.community.safearea

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.PluginThread
import com.getcapacitor.annotation.CapacitorPlugin
import java.util.Locale
import java.util.regex.Pattern

@CapacitorPlugin(name = "SafeArea")
public class SafeAreaPlugin : Plugin() {
    private var webViewMajorVersion = 0

    // Written by SafeAreaWebViewListener
    public var hasMetaViewportCover: Boolean = true

    // Use an initial value of `null`, so this plugin doesn't override any existing behavior by default
    private var statusBarStyle: SystemBarsStyle? = null

    // Use an initial value of `null`, so this plugin doesn't override any existing behavior by default
    private var navigationBarStyle: SystemBarsStyle? = null

    // Declare variable at this scope to help prevent adding multiple listeners.
    private var webViewListener: SafeAreaWebViewListener? = null

    override fun load() {
        super.load()

        warnAboutUnsupportedConfigurationValues()

        webViewMajorVersion = getWebViewMajorVersion()

        val statusBarStyleString = config.configJSON.optString("statusBarStyle")
        if (!statusBarStyleString.isBlank()) {
            statusBarStyle = getSystemBarsStyleFromString(statusBarStyleString)
        }

        val navigationBarStyleString = config.configJSON.optString("navigationBarStyle")
        if (!navigationBarStyleString.isBlank()) {
            navigationBarStyle = getSystemBarsStyleFromString(navigationBarStyleString)
        }

        updateSystemBarsStyle()

        hasMetaViewportCover = config.configJSON.optBoolean("initialViewportFitCover", true)

        setupSafeAreaInsets()
    }

    override fun handleOnStart() {
        super.handleOnStart()

        val detectViewportFitCoverChanges = config.configJSON.optBoolean("detectViewportFitCoverChanges", true)

        if (detectViewportFitCoverChanges) {
            if (webViewListener == null) {
                webViewListener = SafeAreaWebViewListener(bridge).also { bridge.addWebViewListener(it) }
            }
        }
    }

    private fun getWebViewMajorVersion(): Int {
        // A device without a WebView package, or one without a version name, has always failed here with a
        // NullPointerException.
        val packageInfo = WebViewCompat.getCurrentWebViewPackage(bridge.context)!!
        val matcher = Pattern.compile("(\\d+)").matcher(packageInfo.versionName!!)

        if (!matcher.find()) {
            return 0
        }

        return matcher.group(0).toInt()
    }

    private fun warnAboutUnsupportedConfigurationValues() {
        val systemBarsInsetsHandling = bridge.config.getPluginConfiguration("SystemBars").configJSON.optString("insetsHandling")
        if (systemBarsInsetsHandling != "disable") {
            Log.e(
                "SafeAreaPlugin",
                "You should set `SystemBars.insetsHandling` to `disable` in your `capacitor.config.json`. " +
                    "Other values can lead to unexpected behavior."
            )
        }

        val keyboardResizeOnFullScreen =
            bridge.config.getPluginConfiguration("Keyboard").configJSON.optBoolean("resizeOnFullScreen", false)
        if (keyboardResizeOnFullScreen) {
            Log.e(
                "SafeAreaPlugin",
                "You should omit `Keyboard.resizeOnFullScreen` in your `capacitor.config.json`. " +
                    "Other values can lead to unexpected behavior."
            )
        }
    }

    private fun setupSafeAreaInsets() {
        val view = activity.window.decorView

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val shouldPassthroughInsets = webViewMajorVersion >= WEBVIEW_VERSION_WITH_SAFE_AREA_CORE_FIX && hasMetaViewportCover

            val systemBarsInsets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            if (shouldPassthroughInsets) {
                // We need to correct for a possible shown IME
                v.setPadding(0, 0, 0, if (keyboardVisible) imeInsets.bottom else 0)

                return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                        Insets.of(
                            systemBarsInsets.left,
                            systemBarsInsets.top,
                            systemBarsInsets.right,
                            getBottomInset(systemBarsInsets, keyboardVisible)
                        )
                    ).build()
            }

            // We need to correct for a possible shown IME
            v.setPadding(
                systemBarsInsets.left,
                systemBarsInsets.top,
                systemBarsInsets.right,
                if (keyboardVisible) imeInsets.bottom else systemBarsInsets.bottom
            )

            // Returning `WindowInsetsCompat.CONSUMED` breaks recalculation of safe area insets
            // So we have to explicitly set insets to `0`
            // See: https://issues.chromium.org/issues/461332423
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(0, 0, 0, 0)
                ).build()
        }
    }

    private fun getBottomInset(systemBarsInsets: Insets, keyboardVisible: Boolean): Int {
        if (webViewMajorVersion < WEBVIEW_VERSION_WITH_SAFE_AREA_KEYBOARD_FIX) {
            // This is a workaround for webview versions that have a bug
            // that causes the bottom inset to be incorrect if the IME is visible
            // See: https://issues.chromium.org/issues/457682720

            if (keyboardVisible) {
                return 0
            }
        }

        return systemBarsInsets.bottom
    }

    public enum class SystemBarsStyle(public val value: String) {
        DARK("DARK"),
        LIGHT("LIGHT"),
        DEFAULT("DEFAULT")
    }

    private fun getSystemBarsStyleFromString(value: String?): SystemBarsStyle {
        if (value != null) {
            try {
                return SystemBarsStyle.valueOf(value.uppercase(Locale.US))
            } catch (error: IllegalArgumentException) {
                // invalid value
            }
        }

        return SystemBarsStyle.DEFAULT
    }

    public enum class SystemBarsType(public val value: String) {
        STATUS_BAR("STATUS_BAR"),
        NAVIGATION_BAR("NAVIGATION_BAR")
    }

    private fun getSystemBarsTypeFromString(value: String?): SystemBarsType? {
        if (value != null) {
            try {
                return SystemBarsType.valueOf(value.uppercase(Locale.US))
            } catch (error: IllegalArgumentException) {
                // invalid value
            }
        }

        return null
    }

    // The methods change the window, so they run on the main thread, in the order of the calls.
    @PluginMethod(returnType = PluginMethod.RETURN_NONE, thread = PluginThread.MAIN)
    public fun setSystemBarsStyle(call: PluginCall) {
        val style = call.getString("style")
        val type = call.getString("type")

        val systemBarsStyle = getSystemBarsStyleFromString(style)
        val systemBarsType = getSystemBarsTypeFromString(type)

        if (systemBarsType == null || systemBarsType == SystemBarsType.STATUS_BAR) {
            statusBarStyle = systemBarsStyle
        }

        if (systemBarsType == null || systemBarsType == SystemBarsType.NAVIGATION_BAR) {
            navigationBarStyle = systemBarsStyle
        }

        updateSystemBarsStyle()
        call.resolve()
    }

    override fun handleOnConfigurationChanged(newConfig: Configuration?) {
        super.handleOnConfigurationChanged(newConfig)
        bridge.executeOnMainThread { updateSystemBarsStyle() }
    }

    private fun updateSystemBarsStyle() {
        statusBarStyle?.let { setSystemBarsStyle(activity, it, SystemBarsType.STATUS_BAR) }
        navigationBarStyle?.let { setSystemBarsStyle(activity, it, SystemBarsType.NAVIGATION_BAR) }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE, thread = PluginThread.MAIN)
    public fun showSystemBars(call: PluginCall) {
        val systemBarsType = getSystemBarsTypeFromString(call.getString("type"))

        setSystemBarsHidden(false, systemBarsType)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE, thread = PluginThread.MAIN)
    public fun hideSystemBars(call: PluginCall) {
        val systemBarsType = getSystemBarsTypeFromString(call.getString("type"))

        setSystemBarsHidden(true, systemBarsType)
        call.resolve()
    }

    private fun setSystemBarsHidden(hidden: Boolean, type: SystemBarsType?) {
        val window = activity.window
        val windowInsetsControllerCompat = WindowCompat.getInsetsController(window, window.decorView)

        if (hidden) {
            if (type == null || type == SystemBarsType.STATUS_BAR) {
                windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.statusBars())
            }
            if (type == null || type == SystemBarsType.NAVIGATION_BAR) {
                windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.navigationBars())
            }
            return
        }

        if (type == null || type == SystemBarsType.STATUS_BAR) {
            windowInsetsControllerCompat.show(WindowInsetsCompat.Type.systemBars())
        }
        if (type == null || type == SystemBarsType.NAVIGATION_BAR) {
            windowInsetsControllerCompat.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    public companion object {
        // https://issues.chromium.org/issues/40699457
        private const val WEBVIEW_VERSION_WITH_SAFE_AREA_CORE_FIX = 140

        // https://issues.chromium.org/issues/457682720
        private const val WEBVIEW_VERSION_WITH_SAFE_AREA_KEYBOARD_FIX = 144

        @JvmStatic
        @JvmOverloads
        public fun setSystemBarsStyle(activity: Activity, style: SystemBarsStyle, type: SystemBarsType? = null) {
            val resolvedStyle = if (style == SystemBarsStyle.DEFAULT) getStyleForTheme(activity) else style

            val window = activity.window
            val windowInsetsControllerCompat = WindowCompat.getInsetsController(window, window.decorView)
            if (type == null || type == SystemBarsType.STATUS_BAR) {
                windowInsetsControllerCompat.isAppearanceLightStatusBars = resolvedStyle != SystemBarsStyle.DARK
            }

            if (type == null || type == SystemBarsType.NAVIGATION_BAR) {
                windowInsetsControllerCompat.isAppearanceLightNavigationBars = resolvedStyle != SystemBarsStyle.DARK
            }

            if (resolvedStyle == SystemBarsStyle.DARK) {
                window.decorView.setBackgroundColor(Color.BLACK)
            } else {
                window.decorView.setBackgroundColor(Color.WHITE)
            }
        }

        private fun getStyleForTheme(activity: Activity): SystemBarsStyle {
            val currentNightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode != Configuration.UI_MODE_NIGHT_YES) {
                return SystemBarsStyle.LIGHT
            }
            return SystemBarsStyle.DARK
        }
    }
}
