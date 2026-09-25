import SwiftUI

/// Animated continuation of the static launch screen, mirroring Android's splash: the icon settles in, then
/// one 700 ms beat tells the product story (corners focus, a scan line sweeps, the leaf pulses as if
/// recognized). The hand-off is fast and small so the mark never hangs over the first screen, which plays
/// its own entrance as the background dissolves.
struct LaunchOverlay: View {
    let onHandOff: () -> Void
    let onFinish: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var icon = false
    @State private var focus = false
    @State private var scanned = false
    @State private var pulse = false
    @State private var iconGone = false
    @State private var gone = false

    var body: some View {
        ZStack {
            Color.nufoBackground.ignoresSafeArea().opacity(gone ? 0 : 1)
            ZStack {
                Circle().fill(Color.nufoGreen).frame(width: 112, height: 112)
                ForEach(0..<4, id: \.self) { i in
                    Corner()
                        .stroke(.white, style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
                        .frame(width: 14, height: 14)
                        .rotationEffect(.degrees(Double(i) * 90))
                        .offset(cornerOffset(i))
                }
                Image(systemName: "leaf.fill").font(.system(size: 32, weight: .semibold)).foregroundStyle(.white)
                    .scaleEffect(pulse ? 1.08 : 1)
                Capsule().fill(Color(hex: 0xC8E6C9)).frame(width: 40, height: 2)
                    .offset(y: scanned ? 18 : -18)
                    .opacity(scanned ? 0 : (focus ? 1 : 0))
            }
            .scaleEffect(iconGone ? 0.92 : (icon ? 1 : 0.96))
            .opacity(iconGone ? 0 : (icon ? 1 : 0))
        }
        .allowsHitTesting(!gone)
        .accessibilityHidden(true)
        .task { await play() }
    }

    /// Corners rest 26 pt from the centre (top-left, top-right, bottom-right, bottom-left) and focus 3 pt inward.
    private func cornerOffset(_ i: Int) -> CGSize {
        let sx: CGFloat = i == 1 || i == 2 ? 1 : -1, sy: CGFloat = i >= 2 ? 1 : -1
        let d: CGFloat = focus ? 23 : 26
        return CGSize(width: sx * d, height: sy * d)
    }

    private func wait(_ s: Double) async { try? await Task.sleep(for: .seconds(s)) }

    private func play() async {
        withAnimation(Motion.easeOut(Motion.fast)) { icon = true }
        if !reduceMotion {
            await wait(Motion.fast)
            withAnimation(Motion.easeOut(0.18)) { focus = true }
            withAnimation(Motion.easeInOut(0.44).delay(0.12)) { scanned = true }
            await wait(0.18)
            withAnimation(Motion.easeInOut(0.24)) { focus = false }
            await wait(0.24)
            withAnimation(Motion.easeOut(0.13)) { pulse = true }
            await wait(0.13)
            withAnimation(Motion.easeInOut(0.15)) { pulse = false }
            await wait(0.15)
        } else {
            await wait(0.3)
        }
        onHandOff()
        withAnimation(Motion.easeOut(Motion.fast)) { iconGone = true }
        withAnimation(Motion.easeOut(Motion.base)) { gone = true }
        await wait(Motion.base)
        onFinish()
    }
}

/// Top-left scan bracket; rotated for the other three corners.
private struct Corner: Shape {
    func path(in r: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: r.minX, y: r.maxY))
            p.addLine(to: CGPoint(x: r.minX, y: r.minY))
            p.addLine(to: CGPoint(x: r.maxX, y: r.minY))
        }
    }
}

/// Floating notice shown while there is no connection.
struct OfflineBanner: View {
    var body: some View {
        Label("You're offline — saved products still open", systemImage: "icloud.slash")
            .font(.footnote.weight(.medium))
            .foregroundStyle(Color(uiColor: .systemBackground))
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(Color.primary.opacity(0.88), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(.horizontal, 16).padding(.bottom, 8)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .accessibilityAddTraits(.updatesFrequently)
    }
}