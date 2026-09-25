import SwiftUI

/// Intro choreography, in seconds after the launch hand-off (mirrors WelcomeScreen.kt). Elements overlap
/// instead of waiting for each other, so the button is usable at about 1.1 s rather than after the signature.
private enum Intro {
    static let features = 0.25, featureGap = 0.09, tagline = 0.9, cta = 1.1
}

/// What each feature icon acts out once, as its row lands: a scan, a confirmation, a lock closing.
private enum Gesture: Equatable { case scan, confirm, lock }

struct WelcomeView: View {
    let onStart: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.appReady) private var ready
    @State private var go = false
    @State private var tagline = false
    @State private var confirm = 0

    var body: some View {
        ZStack {
            Color.nufoBackground.ignoresSafeArea()
            RadialGradient(colors: [Color.nufoGreen.opacity(0.16), .clear], center: UnitPoint(x: 0.5, y: 0.34), startRadius: 0, endRadius: 360)
                .ignoresSafeArea()
                .opacity(go ? 1 : 0).scaleEffect(go ? 1 : 0.85)
                .animation(Motion.easeOut(Motion.slow * 2), value: go)
            VStack(spacing: 0) {
                // iOS keeps each app's language in Settings (Ελληνικά / English appear there once both are bundled).
                Button { UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!) } label: {
                    Label(dataLang == "el" ? "Ελληνικά" : "English", systemImage: "globe").font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 14).padding(.vertical, 8).background(Color.nufoCard, in: Capsule())
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
                .accessibilityLabel(Text("Language"))
                .opacity(go ? 1 : 0).animation(Motion.easeOut(Motion.slow), value: go)
                Spacer()
                TegakiLogo(speed: 1.35).frame(width: 250)
                Group {
                    if tagline { Calligraph(text: String(localized: "Easy • Quick • Accurate"), font: .headline, color: .nufoSecondaryText) }
                }.frame(height: 28).padding(.top, 14)
                Spacer()
                VStack(alignment: .leading, spacing: 18) {
                    feature(.scan, "barcode.viewfinder", "Scan anything", "Barcodes, meals and labels in seconds.", 0)
                    feature(.confirm, "checkmark.seal", "Real data only", "Straight from Open Food Facts and USDA. Missing means missing.", 1)
                    feature(.lock, "lock", "No account, ever", "Your history never leaves this phone.", 2)
                }
                Spacer()
                VStack(spacing: 10) {
                    Button(action: onStart) {
                        Text("Get started").font(.headline).frame(maxWidth: .infinity).frame(height: 56)
                            .background(Color.nufoGreen, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .foregroundStyle(.white)
                    }
                    .buttonStyle(PressableStyle())
                    Text("Nutrition information, not medical advice.").font(.caption).foregroundStyle(Color.nufoSecondaryText)
                }
                .opacity(go ? 1 : 0).offset(y: go ? 0 : 32)
                .animation(Motion.easeOut(Motion.slow).delay(Intro.cta), value: go)
            }
            .padding(.horizontal, 28).padding(.bottom, 20)
        }
        .onAppear(perform: start)
        .onChange(of: ready) { _, _ in start() }
    }

    private func start() {
        guard ready, !go else { return }
        if reduceMotion {
            var t = Transaction(); t.disablesAnimations = true
            withTransaction(t) { go = true; tagline = true }
            return
        }
        go = true
        Task {
            try? await Task.sleep(for: .seconds(Intro.features + Intro.featureGap + Motion.base))
            confirm += 1
            try? await Task.sleep(for: .seconds(Intro.tagline - Intro.features - Intro.featureGap - Motion.base))
            tagline = true
        }
    }

    private func feature(_ gesture: Gesture, _ icon: String, _ title: LocalizedStringKey, _ body: LocalizedStringKey, _ i: Int) -> some View {
        let at = Intro.features + Double(i) * Intro.featureGap
        return HStack(spacing: 16) {
            ZStack {
                Circle().fill(Color.nufoGreen.opacity(0.12))
                Image(systemName: icon).font(.title3).foregroundStyle(Color.nufoGreen)
                    .symbolEffect(.bounce, value: gesture == .confirm ? confirm : 0)
                    // The lock swings shut from -16 degrees as its row settles.
                    .rotationEffect(.degrees(gesture == .lock && !go && !reduceMotion ? -16 : 0))
                    .animation(Motion.easeOut(0.6).delay(at + Motion.base), value: go)
                if gesture == .scan && !reduceMotion { ScanSweep(trigger: go, delay: at + Motion.base) }
            }
            .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(body).font(.subheadline).foregroundStyle(Color.nufoSecondaryText)
            }
        }
        .opacity(go ? 1 : 0).offset(y: go ? 0 : 24)
        .animation(Motion.easeOut(Motion.slow).delay(at), value: go)
        .accessibilityElement(children: .combine)
    }
}

/// A scan line that sweeps the scanner icon once, top to bottom, fading at both ends.
private struct ScanSweep: View {
    let trigger: Bool
    let delay: Double
    private struct Line { var y = -13.0; var opacity = 0.0 }

    var body: some View {
        Capsule().fill(Color.nufoGreen).frame(width: 26, height: 2)
            .keyframeAnimator(initialValue: Line(), trigger: trigger) { line, v in
                line.opacity(v.opacity).offset(y: v.y)
            } keyframes: { _ in
                KeyframeTrack(\.y) {
                    LinearKeyframe(-13, duration: delay)
                    CubicKeyframe(13, duration: 0.6)
                }
                KeyframeTrack(\.opacity) {
                    LinearKeyframe(0, duration: delay)
                    LinearKeyframe(1, duration: 0.15)
                    LinearKeyframe(1, duration: 0.3)
                    LinearKeyframe(0, duration: 0.15)
                }
            }
            .accessibilityHidden(true)
    }
}