import SwiftUI

/// Soft-shadowed card that sinks slightly when pressed.
struct NufoCard<Content: View>: View {
    var corner: CGFloat = 22
    @ViewBuilder var content: Content
    var body: some View {
        content
            .background(Color.nufoCard, in: RoundedRectangle(cornerRadius: corner, style: .continuous))
            .shadow(color: .black.opacity(0.06), radius: 14, y: 6)
    }
}

struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.nufo, value: configuration.isPressed)
    }
}

/// Fades and lifts content in the first time a screen shows it, staggered by index. Returning to the
/// screen keeps it at rest (the state survives pushes), and the first screen waits for the launch hand-off.
struct EnterStagger: ViewModifier {
    let index: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.appReady) private var ready
    @State private var shown = false
    func body(content: Content) -> some View {
        content.opacity(shown || reduceMotion ? 1 : 0).offset(y: shown || reduceMotion ? 0 : 12)
            .onAppear(perform: play)
            .onChange(of: ready) { _, _ in play() }
    }
    private func play() {
        guard ready, !shown else { return }
        withAnimation(Motion.easeOut().delay(Motion.stagger * Double(min(index, 10)))) { shown = true }
    }
}
extension View { func enterStagger(_ i: Int) -> some View { modifier(EnterStagger(index: i)) } }

struct ScoreChip: View {
    let title: String
    let value: String?
    let color: Color
    var body: some View {
        VStack(spacing: 6) {
            Text(value ?? "–").font(.title3.bold())
                .foregroundStyle(value == nil ? color : .white)
                .frame(width: 46, height: 46)
                .background((value == nil ? color.opacity(0.18) : color), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            Text(title).font(.caption).foregroundStyle(Color.nufoSecondaryText).lineLimit(1)
        }
        .accessibilityElement().accessibilityLabel(value.map { "\(title) \($0)" } ?? "\(title) not available")
    }
}

struct ScoreRing: View {
    let score: Int
    var diameter: CGFloat = 92
    @State private var sweep = 0.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        ZStack {
            Circle().stroke(Color.secondary.opacity(0.15), lineWidth: 9)
            Circle().trim(from: 0, to: sweep).stroke(Grade.score(score), style: StrokeStyle(lineWidth: 9, lineCap: .round)).rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                CalligraphNumber(value: Double(score), font: .title.bold())
                Text("NUFO").font(.system(size: 10, weight: .medium)).foregroundStyle(Color.nufoSecondaryText)
            }
        }
        .frame(width: diameter, height: diameter)
        .onAppear { withAnimation(reduceMotion ? nil : Motion.easeOut(Motion.reveal)) { sweep = Double(score) / 100 } }
        .accessibilityElement().accessibilityLabel("Nufo Score \(score) out of 100")
    }
}

struct NutrientCard: View {
    let icon: String
    let label: String
    let grams: Double?
    let tint: Color
    var body: some View {
        NufoCard {
            VStack(alignment: .leading, spacing: 4) {
                Image(systemName: icon).font(.system(size: 15, weight: .semibold)).foregroundStyle(tint)
                    .frame(width: 34, height: 34).background(tint.opacity(0.14), in: Circle())
                    .padding(.bottom, 6)
                Text(label).font(.caption).foregroundStyle(Color.nufoSecondaryText).lineLimit(1)
                if let g = grams {
                    let (v, unit) = (fmt(g), "g") // macronutrients are always stated in grams
                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text(v).font(.title3.bold()).contentTransition(.numericText())
                        Text(unit).font(.caption).foregroundStyle(Color.nufoSecondaryText)
                    }
                } else {
                    Text("Not available").font(.subheadline).foregroundStyle(Color.nufoSecondaryText)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(14)
        }
        .accessibilityElement(children: .combine)
    }
}

/// Product photo, or a tinted letter placeholder until the image has actually arrived.
/// Uses the 400 px Open Food Facts rendition (sharp at 3x and shared with the offline cache);
/// `hero` layers the full-resolution photo on top once it lands.
struct ProductThumb: View {
    let url: String?
    let name: String
    var hero = false
    @State private var loaded = false

    var body: some View {
        ZStack {
            Color.nufoGreen.opacity(0.1)
            Text(name.prefix(1).uppercased()).font(.title.bold()).foregroundStyle(Color.nufoGreen.opacity(0.5))
            if loaded { Color.white }
            if let u = offImage(url, "400").flatMap({ URL(string: $0) }) {
                RemoteImage(url: u) { loaded = true }.padding(10)
            }
            if hero, let u = offImage(url, "full").flatMap({ URL(string: $0) }), u.absoluteString != url {
                RemoteImage(url: u, maxPixels: 1600) { loaded = true }.padding(10)
            }
        }
        .clipped()
        .accessibilityHidden(true)
    }
}

/// Loads with `.returnCacheDataElseLoad`: a product photo never changes under the same URL, so any cached
/// copy is good and photos stay visible offline (AsyncImage can't set a cache policy). Large originals are
/// decoded at display size instead of full resolution, which would cost ~50 MB for a 12 MP photo.
struct RemoteImage: View {
    let url: URL
    var maxPixels: CGFloat = 800
    var onLoad: () -> Void = {}
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image { Image(uiImage: image).resizable().scaledToFit().transition(.opacity) } else { Color.clear }
        }
        .task(id: url) {
            var request = URLRequest(url: url, cachePolicy: .returnCacheDataElseLoad, timeoutInterval: 30)
            request.setValue(FoodAPI.userAgent, forHTTPHeaderField: "User-Agent")
            guard let (data, _) = try? await URLSession.shared.data(for: request), let raw = UIImage(data: data) else { return }
            let scale = min(1, maxPixels / max(raw.size.width, raw.size.height))
            let target = CGSize(width: raw.size.width * scale, height: raw.size.height * scale)
            let ready = await raw.byPreparingThumbnail(ofSize: target) ?? raw
            withAnimation(Motion.easeOut()) { image = ready }
            onLoad()
        }
    }
}

struct Pill: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text).font(.subheadline.weight(.semibold)).foregroundStyle(color)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(color.opacity(0.12), in: Capsule())
    }
}
/// A–E (or 1–4) strip in the style of the official Nutri-Score badge: every grade visible,
/// the product's grade enlarged; with no grade the strip stays muted and says so.
struct GradeScale: View {
    let title: String
    let values: [String]
    let active: String?
    let color: (String) -> Color
    let description: LocalizedStringKey?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 0) {
                Text(title).font(.subheadline.weight(.semibold)).frame(width: 96, alignment: .leading)
                HStack(spacing: 2) {
                    ForEach(values, id: \.self) { v in
                        let on = v.caseInsensitiveCompare(active ?? "") == .orderedSame
                        Text(v.uppercased())
                            .font(.system(size: on ? 17 : 12, weight: .bold))
                            .foregroundStyle(on ? Color.white : Color.primary.opacity(0.55))
                            .frame(width: on ? 34 : 26, height: on ? 34 : 24)
                            .background(active == nil ? Color.secondary.opacity(0.15) : color(v).opacity(on ? 1 : 0.22),
                                        in: RoundedRectangle(cornerRadius: on ? 10 : 6, style: .continuous))
                            .overlay { if on { RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(.white, lineWidth: 2) } }
                    }
                }
                .animation(.nufo, value: active)
                if active == nil {
                    Text("Not available").font(.caption).foregroundStyle(Color.nufoSecondaryText).padding(.leading, 10)
                }
            }
            if let description, active != nil {
                Text(description).font(.caption).foregroundStyle(Color.nufoSecondaryText).padding(.leading, 96)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

struct TrustChip: View {
    let icon: String
    let text: String
    var tint: Color = .nufoGreen
    var body: some View {
        Label { Text(text).font(.caption.weight(.medium)).foregroundStyle(.primary) } icon: { Image(systemName: icon).foregroundStyle(tint) }
            .labelStyle(.titleAndIcon)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(tint.opacity(0.09), in: Capsule())
    }
}

extension Level {
    var color: Color { self == .low ? Grade.color("a") : self == .medium ? Grade.color("c") : Grade.color("e") }
    var label: LocalizedStringKey { self == .low ? "Low" : self == .medium ? "Medium" : "High" }
}

/// Darker grade colours for text on light backgrounds (the bright ones are for fills only).
func scoreTextColor(_ s: Int, dark: Bool) -> Color {
    if dark { return Grade.score(s) }
    return s >= 80 ? Grade.color("a") : s >= 60 ? Color(hex: 0x2E7D32) : s >= 40 ? Color(hex: 0x8A5A00) : s >= 20 ? Color(hex: 0xB23C00) : Color(hex: 0xC62828)
}

/// What Nufo does with data: privacy, provenance, and what happens when something is missing.
struct AboutSheet: View {
    var body: some View {
        NavigationStack {
            List {
                section("lock", "Your privacy", "Nufo has no accounts, no analytics and no ads. Photos are analysed on your phone. History is stored only on this device and is excluded from cloud backups.")
                section("externaldrive", "Where the data comes from", "Packaged products come from Open Food Facts, a non-profit database built by volunteers and manufacturers, with thousands of Greek products. Generic foods come from the U.S. Department of Agriculture. Every result names its source and last update.")
                section("magnifyingglass", "When a product is missing", "Nufo never guesses. You'll see «Not available», and you can add the product to Open Food Facts so it's there for everyone.")
                Text("Nufo provides nutrition information, not medical advice.").font(.caption).foregroundStyle(Color.nufoSecondaryText)
            }
            .navigationTitle("How Nufo works")
        }
        .presentationDetents([.large])
    }

    private func section(_ icon: String, _ title: LocalizedStringKey, _ body: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon).foregroundStyle(Color.nufoGreen).frame(width: 36, height: 36).background(Color.nufoGreen.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline)
                Text(body).font(.subheadline).foregroundStyle(Color.nufoSecondaryText)
            }
        }
        .padding(.vertical, 4)
    }
}

/// The complete Nufo Score rule table, so nothing about the number is hidden.
struct MethodologySheet: View {
    var body: some View {
        NavigationStack {
            List {
                Text("Every result starts at 100. Nufo only adjusts for data the source actually publishes — missing values never cost or earn points.")
                Section {
                    rule("Nutri-Score B / C / D / E", "−5 / −15 / −30 / −45")
                    rule("NOVA 2 / 3 / 4", "−5 / −15 / −30")
                    rule("Protein ≥ 10 g per 100 g", "+10")
                    rule("Fibre ≥ 5 g per 100 g", "+10")
                    rule("Sugar > 10 g per 100 g", "−15")
                    rule("Salt > 1 g per 100 g", "−15")
                    rule("Saturated fat > 5 g per 100 g", "−10")
                }
                Text("Nutri-Score, NOVA and Eco-Score are shown exactly as published by Open Food Facts. Nufo never estimates them.")
                    .font(.subheadline).foregroundStyle(Color.nufoSecondaryText)
                Text("Colours follow UK FSA front-of-pack thresholds for foods, per 100 g.")
                    .font(.subheadline).foregroundStyle(Color.nufoSecondaryText)
            }
            .navigationTitle("How Nufo scores")
        }
        .presentationDetents([.large])
    }

    private func rule(_ text: LocalizedStringKey, _ delta: String) -> some View {
        HStack {
            Text(text)
            Spacer()
            Text(delta).fontWeight(.semibold).foregroundStyle(delta.hasPrefix("+") ? Grade.color("a") : Grade.color("e"))
        }
    }
}
extension ResultRequest {
    /// Shared by the tapped row and the product page, so iOS 18 can zoom one into the other.
    var zoomID: String {
        switch self {
        case .barcode(let c): c
        case .hit(let h): h.barcode ?? h.name
        case .product(let p): p.key
        }
    }
}

extension View {
    /// iOS 18+: the tapped card grows into the product page and shrinks back on close. Older iOS keeps the standard push.
    @ViewBuilder func zoomSource(_ id: String, in ns: Namespace.ID) -> some View {
        if #available(iOS 18, *) { matchedTransitionSource(id: id, in: ns) } else { self }
    }
    @ViewBuilder func zoomDestination(_ id: String, in ns: Namespace.ID) -> some View {
        if #available(iOS 18, *) { navigationTransition(.zoom(sourceID: id, in: ns)) } else { self }
    }
}

/// How long waits are presented, everywhere (mirrors Wait.kt): nothing for the first 0.4 s so near-instant
/// answers never flash a loader; after 1 s a line saying what is really happening; after 7 s it admits it is
/// slow; at 15 s the request gives up (`giveUpSeconds`) and the user gets a saved copy or Retry.
enum Wait {
    static let showAfter = 0.4, captionAfter = 1.0, slowAfter = 7.0
    /// Reports elapsed seconds at each threshold until cancelled.
    static func tick(_ update: @MainActor (Double) -> Void) async {
        var last = 0.0
        for mark in [showAfter, captionAfter, slowAfter] {
            try? await Task.sleep(for: .seconds(mark - last))
            if Task.isCancelled { return }
            last = mark
            await update(mark)
        }
    }
}

/// The line under a loader; crossfades as the text changes.
struct WaitCaption: View {
    let text: String?
    var body: some View {
        ZStack(alignment: .leading) {
            if let text {
                Text(text).font(.subheadline.weight(.medium)).foregroundStyle(Color.nufoSecondaryText)
                    .id(text).transition(.opacity)
            }
        }
        .frame(height: 20, alignment: .leading)
        .animation(Motion.easeOut(), value: text)
        .accessibilityAddTraits(.updatesFrequently)
    }
}
