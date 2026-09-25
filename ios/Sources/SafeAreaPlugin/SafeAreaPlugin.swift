import Foundation
import UIKit
import Capacitor

@objc(SafeAreaPlugin)
public class SafeAreaPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "SafeAreaPlugin"
    public let jsName = "SafeArea"
    // The methods stay synchronous: the bridge queue runs them in the order of the calls, and each hands its UIKit
    // work to the main queue in that order (the bridge's status bar setters do it themselves), so the last call wins.
    // Async methods would not keep that order.
    public let pluginMethods: [CAPPluginMethod] = [
        .none("setSystemBarsStyle", SafeAreaPlugin.setSystemBarsStyle(_:)),
        .none("showSystemBars", SafeAreaPlugin.showSystemBars),
        .none("hideSystemBars", SafeAreaPlugin.hideSystemBars)
    ]

    public private(set) var hideHomeIndicator: Bool = false

    // Use an initial value of `nil`, so this plugin doesn't override any existing behavior by default
    private var statusBarStyle: SystemBarsStyle? = nil;

    override public func load() {
        if let statusBarStyleString = getConfig().getString("statusBarStyle") {
            statusBarStyle = getSystemBarsStyleFromString(statusBarStyleString)
        }

        updateSystemBarsStyle()
    }

    enum SystemBarsStyle: String {
        case dark = "DARK"
        case light = "LIGHT"
        case defaultStyle = "DEFAULT"
    }

    private func getSystemBarsStyleFromString(_ value: String?) -> SystemBarsStyle {
        let systemBarsStyle = SystemBarsStyle(rawValue: value?.uppercased() ?? "")
        return systemBarsStyle ?? SystemBarsStyle.defaultStyle
    }

    enum SystemBarsType: String {
        case statusBar = "STATUS_BAR"
        case navigationBar = "NAVIGATION_BAR"
    }

    private func getSystemBarsTypeFromString(_ value: String?) -> SystemBarsType? {
        return SystemBarsType(rawValue: value?.uppercased() ?? "")
    }

    func setSystemBarsStyle(_ call: CAPPluginCall) {
        let style = call.getString("style")
        let type = call.getString("type")

        let systemBarsStyle = getSystemBarsStyleFromString(style)
        let systemBarsType = getSystemBarsTypeFromString(type)

        if systemBarsType == nil || systemBarsType == SystemBarsType.statusBar {
            statusBarStyle = systemBarsStyle
        }

        // home indicator cannot be styled on iOS, so we don't handle that here

        updateSystemBarsStyle()
        call.resolve()
    }

    func updateSystemBarsStyle() {
        if let statusBarStyle = statusBarStyle {
            setSystemBarsStyle(style: statusBarStyle);
        }
    }

    func setSystemBarsStyle(style: SystemBarsStyle) {
        bridge?.statusBarStyle = switch style {
            case .dark:
                UIStatusBarStyle.lightContent
            case .light:
                UIStatusBarStyle.darkContent
            case .defaultStyle:
                UIStatusBarStyle.default
        }
    }

    func showSystemBars(_ call: CAPPluginCall) {
        let type = call.getString("type")
        let systemBarsType = getSystemBarsTypeFromString(type)

        DispatchQueue.main.async {
            self.setSystemBarsHidden(hidden: false, type: systemBarsType)
            call.resolve()
        }
    }

    func hideSystemBars(_ call: CAPPluginCall) {
        let type = call.getString("type")
        let systemBarsType = getSystemBarsTypeFromString(type)

        DispatchQueue.main.async {
            self.setSystemBarsHidden(hidden: true, type: systemBarsType)
            call.resolve()
        }
    }

    private func setSystemBarsHidden(hidden: Bool, type: SystemBarsType?) {
        if hidden {
            if type == nil || type == SystemBarsType.statusBar {
                bridge?.statusBarVisible = false
            }
            if type == nil || type == SystemBarsType.navigationBar {
                hideHomeIndicator = true
                bridge?.viewController?.setNeedsUpdateOfHomeIndicatorAutoHidden()
            }
            return
        }

        if type == nil || type == SystemBarsType.statusBar {
            bridge?.statusBarVisible = true
        }
        if type == nil || type == SystemBarsType.navigationBar {
            hideHomeIndicator = false
            bridge?.viewController?.setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }
}

extension CAPBridgeViewController {
    override public var prefersHomeIndicatorAutoHidden: Bool {
        if let systemBarPlugin = self.bridge?.plugin(withName: "SafeArea") as? SafeAreaPlugin {
            return systemBarPlugin.hideHomeIndicator
        }

        return false
    }
}
