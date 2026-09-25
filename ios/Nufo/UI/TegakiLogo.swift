import SwiftUI

/// "Nufo" handwritten in Caveat, drawn stroke by stroke.
///
/// Strokes exported from Tegaki (github.com/gkurt/tegaki) with
/// `npx tegaki "Nufo" --font caveat --mode once --pressure 0`: glyph skeleton, pen width,
/// start and duration in seconds. The same data drives the Android version.
private struct TegakiStroke { let begin: Double; let duration: Double; let width: CGFloat; let points: [CGPoint] }

private func pts(_ s: String) -> [CGPoint] {
    let v = s.split(separator: " ").compactMap { Double($0) }
    return stride(from: 0, to: v.count - 1, by: 2).map { CGPoint(x: v[$0], y: v[$0 + 1]) }
}

private let strokes: [TegakiStroke] = [
    .init(begin: 0, duration: 0.68, width: 11.21, points: pts("31.59 125.82 31.59 124.72 35.12 115.46 36.89 109.07 42.62 93.85 48.13 81.72 52.1 70.26 53.2 68.49 56.73 66.51 56.95 63.64 56.73 66.73 59.16 68.71 59.38 70.26 59.38 96.94 62.46 113.7 64.67 119.87 65.99 121.85 69.08 122.74 71.5 121.41 75.25 113.25 79.66 99.58 83.85 83.93 85.18 75.77 90.03 54.16")),
    .init(begin: 0.78, duration: 0.22, width: 10.12, points: pts("101.58 89.11 101.38 90.44 98.82 95.46 96.87 100.07 95.23 104.98 94.62 108.16 94.41 112.66 94.72 113.89 96.05 115.63 97.18 116.15 98.31 116.25 102.09 115.43 103.73 114.61 107.21 111.85 108.75 110.21 111.93 108.98 113.05 105.5 115.3 100.38 116.53 96.79 116.94 93.92 117.86 91.77 118.07 89.73")),
    .init(begin: 1.16, duration: 0.04, width: 10.03, points: pts("111.93 108.98 112.54 109.49 112.54 113.59 113.15 116.15 114.08 117.99 116.02 120.34")),
    .init(begin: 1.3, duration: 0.18, width: 10.17, points: pts("128.24 130.6 127.6 126.35 128.24 119.34 130.79 106.6 133.55 96.19 136.52 93.21 144.39 92.58 149.7 91.51")),
    .init(begin: 1.62, duration: 0.18, width: 10.4, points: pts("136.74 93 136.52 91.73 137.37 86.42 142.9 73.67 146.72 67.72 150.12 63.9 154.58 60.29 157.34 59.65 160.1 60.29 160.95 61.35 161.17 63.05 159.89 68.78")),
    .init(begin: 1.9, duration: 0.28, width: 9.68, points: pts("158.66 112.76 158.96 108.78 159.75 106.29 162.54 100.71 166.82 94.74 169.31 92.15 171.8 90.26 173.09 89.56 176.98 88.87 180.46 89.86 181.76 91.26 182.45 94.94 182.25 98.22 181.76 100.81 181.16 102.5 179.37 105.99 177.18 109.27 173.69 113.36 170.51 115.94 167.52 117.74 166.03 118.23 163.34 118.63 161.94 118.33 160.35 117.34 159.16 115.25 158.66 112.76")),
]

// Tight crop of Tegaki's 201.3 x 180 viewBox around the ink.
private let view = CGRect(x: 22, y: 48, width: 170, height: 92)
private let total = strokes.map { $0.begin + $0.duration }.max()!

/// Tegaki's easing, cubic-bezier(0.33, 0, 0.15, 1), solved numerically.
private func ease(_ t: Double) -> Double {
    func bez(_ p1: Double, _ p2: Double, _ s: Double) -> Double { 3 * (1 - s) * (1 - s) * s * p1 + 3 * (1 - s) * s * s * p2 + s * s * s }
    var lo = 0.0, hi = 1.0
    for _ in 0..<24 { let mid = (lo + hi) / 2; if bez(0.33, 0.15, mid) < t { lo = mid } else { hi = mid } }
    return bez(0, 1, (lo + hi) / 2)
}

struct TegakiLogo: View {
    var color: Color = .nufoGreen
    var animate = true
    var speed = 1.0
    var onFinished: () -> Void = {}
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.appReady) private var ready
    /// Set when writing begins: after the launch hand-off, so the first stroke is never drawn under it.
    @State private var start: Date?

    var body: some View {
        let instant = !animate || reduceMotion
        TimelineView(.animation(paused: instant)) { ctx in
            let t = instant ? total : start.map { ctx.date.timeIntervalSince($0) * speed } ?? 0
            Canvas { gc, size in
                let s = size.width / view.width
                gc.scaleBy(x: s, y: s)
                gc.translateBy(x: -view.minX, y: -view.minY)
                for st in strokes {
                    let raw = min(max((t - st.begin) / st.duration, 0), 1)
                    guard raw > 0 else { continue }
                    var path = Path(); path.addLines(st.points)
                    gc.stroke(path.trimmedPath(from: 0, to: ease(raw)), with: .color(color),
                              style: StrokeStyle(lineWidth: st.width * 0.66, lineCap: .round, lineJoin: .round))
                }
            }
        }
        .aspectRatio(view.width / view.height, contentMode: .fit)
        .accessibilityElement().accessibilityLabel("Nufo")
        .task(id: ready) {
            guard ready, start == nil else { return }
            start = .now
            if !instant { try? await Task.sleep(for: .seconds(total / speed)) }
            onFinished()
        }
    }
}