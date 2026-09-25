import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
    static let nufoGreen = Color(hex: 0x2E7D32)
    static let nufoGreenDeep = Color(hex: 0x1B5E20)
    /// Adaptive tokens (light / dark) defined in Assets.xcassets.
    static let nufoBackground = Color("Background")
    static let nufoCard = Color("Card")
    static let nufoSecondaryText = Color("SecondaryText")
}

enum Grade {
    static func color(_ g: String?) -> Color {
        switch g?.lowercased() {
        case "a": Color(hex: 0x1B5E20)
        case "b": Color(hex: 0x4CAF50)
        case "c": Color(hex: 0xFFC107)
        case "d": Color(hex: 0xFF9800)
        case "e": Color(hex: 0xF44336)
        default: Color(hex: 0x9CA3AF)
        }
    }
    static func nova(_ n: Int?) -> Color {
        switch n { case 1: color("a"); case 2: color("b"); case 3: color("d"); case 4: color("e"); default: color(nil) }
    }
    static func score(_ s: Int) -> Color {
        s >= 80 ? color("a") : s >= 60 ? color("b") : s >= 40 ? color("c") : s >= 20 ? color("d") : color("e")
    }
}

extension Animation {
    /// One spring for everything that moves (positions); fades use Motion curves.
    static let nufo = Animation.spring(response: 0.45, dampingFraction: 0.82)
}

/// The one motion system, mirroring Android Motion.kt: nothing picks its own duration or curve.
enum Motion {
    /// Presses, small toggles, anything leaving.
    static let fast = 0.15
    /// Screens, crossfades, list entrances.
    static let base = 0.25
    /// First-run moments only.
    static let slow = 0.40
    /// Charts filling to their value.
    static let reveal = 0.70
    /// Gap between items entering one after another.
    static let stagger = 0.045
    /// Strong ease-out for anything entering or leaving.
    static func easeOut(_ duration: Double = base) -> Animation { .timingCurve(0.23, 1, 0.32, 1, duration: duration) }
    /// Strong ease-in-out for on-screen movement.
    static func easeInOut(_ duration: Double = base) -> Animation { .timingCurve(0.77, 0, 0.175, 1, duration: duration) }
}

private struct AppReadyKey: EnvironmentKey { static let defaultValue = true }
extension EnvironmentValues {
    /// False until the opening animation hands over, so first-screen entrances play in view.
    var appReady: Bool {
        get { self[AppReadyKey.self] }
        set { self[AppReadyKey.self] = newValue }
    }
}

/// Formats an amount stored in grams: g at or above 1 (and exactly 0), else mg / µg.
func formatGrams(_ g: Double) -> (String, String) {
    if g == 0 || g >= 1 { return (fmt(g), "g") }
    if g >= 0.001 { return (fmt(g * 1000), "mg") }
    return (fmt(g * 1_000_000), "µg")
}

func fmt(_ v: Double) -> String {
    let s = v >= 100 ? String(format: "%.0f", v) : String(format: "%.1f", v)
    return s.hasSuffix(".0") ? String(s.dropLast(2)) : s
}