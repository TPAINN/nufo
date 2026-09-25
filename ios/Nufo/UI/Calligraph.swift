import SwiftUI

/*
 * Native port of Calligraph (calligraph.raphaelsalaja.com): fluid text transitions.
 * - Calligraph: each character rises in with a short stagger when the text appears or changes.
 * - CalligraphNumber: digit slots roll toward the new value (SwiftUI's numericText transition).
 * VoiceOver reads the plain string, never the per-glyph pieces.
 */
struct Calligraph: View {
    let text: String
    var font: Font = .body
    var color: Color = .primary
    var stagger = 0.028
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    /// Characters grouped per word, each keeping its index in the whole text for the stagger.
    private var words: [[(offset: Int, element: Character)]] {
        var out: [[(offset: Int, element: Character)]] = [[]]
        for (i, ch) in text.enumerated() {
            out[out.count - 1].append((i, ch))
            if ch == " " { out.append([]) }
        }
        return out.filter { !$0.isEmpty }
    }

    var body: some View {
        // Words (with their trailing space) are the wrapping unit, so long names break like normal text.
        FlowLayout(spacing: 0) {
            ForEach(Array(words.enumerated()), id: \.offset) { _, word in
                HStack(spacing: 0) {
                    ForEach(Array(word), id: \.offset) { i, ch in
                        Text(String(ch)).font(font).foregroundStyle(color)
                            .opacity(shown ? 1 : 0)
                            .offset(y: shown ? 0 : 10)
                            .scaleEffect(shown ? 1 : 0.9, anchor: .bottom)
                            .animation(reduceMotion ? nil : .spring(response: 0.42, dampingFraction: 0.78).delay(Double(i) * stagger), value: shown)
                    }
                }
            }
        }
        .id(text) // a new string replays the entrance
        .accessibilityElement().accessibilityLabel(text)
        .onAppear { shown = true }
    }
}

struct CalligraphNumber: View {
    let value: Double
    var format = "%.0f"
    var font: Font = .title2.weight(.bold)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = 0.0

    var body: some View {
        Text(String(format: format, shown))
            .font(font).monospacedDigit()
            .contentTransition(.numericText(value: shown))
            .onAppear { withAnimation(reduceMotion ? nil : .spring(response: 0.7, dampingFraction: 0.85)) { shown = value } }
            .onChange(of: value) { _, v in withAnimation(reduceMotion ? nil : .nufo) { shown = v } }
            .accessibilityLabel(String(format: format, value))
    }
}