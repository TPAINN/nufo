import SwiftUI
import SwiftData
import Charts

private enum LoadState {
    /// `preview` is what the tapped row already showed, painted while the full record loads.
    case loading(SearchHit?)
    case loaded(Product, cachedAt: Date?)
    case notFound(String?, Identified?)
    case failed(offline: Bool)
}
private enum Portion: CaseIterable { case per100, serving, bowl, plate, custom }
private enum DetailTab: CaseIterable {
    case nutrition, vitamins, ingredients, additives, environment
    var title: LocalizedStringKey {
        switch self {
        case .nutrition: "Nutrition"; case .vitamins: "Vitamins & minerals"; case .ingredients: "Ingredients"
        case .additives: "Additives"; case .environment: "Environment"
        }
    }
}
private let bowlGrams = 250.0, plateGrams = 350.0

struct ResultView: View {
    let request: ResultRequest
    @EnvironmentObject private var model: AppModel
    @Environment(\.modelContext) private var context
    @Environment(\.openURL) private var openURL
    @Query private var history: [HistoryItem]
    @State private var state: LoadState = .loading(nil)
    @State private var step: LookupStep?

    var body: some View {
        Group {
            switch state {
            case .loading(let preview): Skeleton(preview: preview, step: step)
            case .notFound(_, let identified?):
                Identity(item: identified) { model.search(for: identified.name) }
            case .notFound(let code, nil):
                Message(icon: "magnifyingglass", title: "Product not found",
                        body: code.map { String(localized: "Neither Open Food Facts nor USDA has barcode \($0) yet. You can search by name, or add it for everyone.") }
                            ?? String(localized: "Neither Open Food Facts nor USDA has this item. Try searching by name.")) {
                    Button("Search by name") { model.search(for: "") }.buttonStyle(.borderedProminent)
                    if let code {
                        Button { openURL(URL(string: "https://world.openfoodfacts.org/cgi/product.pl?type=add&code=\(code)")!) } label: {
                            Label("Add it to Open Food Facts", systemImage: "plus.circle")
                        }
                        .buttonStyle(.bordered)
                    }
                }
            case .failed(let offline):
                Message(icon: offline ? "wifi.slash" : "exclamationmark.icloud",
                        title: offline ? "You're offline" : "The food database isn't responding",
                        body: offline ? String(localized: "This product isn't saved on your phone yet. Connect to the internet and try again.")
                                      : String(localized: "It's on their side, not yours. Try again in a moment.")) {
                    Button("Retry") { Task { await load() } }.buttonStyle(.borderedProminent)
                }
            case .loaded(let p, let cachedAt): ProductDetail(product: p, cachedAt: cachedAt)
            }
        }
        .animation(Motion.easeOut(), value: stateKey)
        .background(Color.nufoBackground)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if case .loaded(let p, _) = state {
                let saved = history.contains { $0.key == p.key }
                Button { saved ? context.removeProduct(p) : context.saveProduct(p) } label: {
                    Image(systemName: saved ? "bookmark.fill" : "bookmark").contentTransition(.symbolEffect(.replace))
                }
                .accessibilityLabel(saved ? Text("Remove from history") : Text("Save to history"))
                ShareLink(item: shareText(p)) { Image(systemName: "square.and.arrow.up") }.accessibilityLabel(Text("Share"))
            }
        }
        .task { await load() }
        // Back online after an offline failure: try again without making the user tap Retry.
        .onChange(of: model.online) { _, online in
            if online, case .failed(offline: true) = state { Task { await load() } }
        }
    }

    private var stateKey: Int {
        switch state { case .loading: 0; case .loaded: 1; case .notFound: 2; case .failed: 3 }
    }

    private func load() async {
        let lookup: Lookup
        var code: String?
        switch request {
        case .product(let p): state = .loaded(p, cachedAt: nil); return
        case .barcode(let c):
            code = c; state = .loading(nil)
            lookup = await model.api.lookupBarcode(c, lang: dataLang, onStep: report)
        case .hit(let h):
            code = h.barcode; state = .loading(h)
            if let p = h.product { lookup = .found(p) }
            else if let c = h.barcode { lookup = await model.api.lookupBarcode(c, lang: dataLang, onStep: report) }
            else { lookup = .notFound(nil) }
        }
        switch lookup {
        case .found(let p, let cachedAt):
            if cachedAt == nil { context.saveProduct(p) }
            state = .loaded(p, cachedAt: cachedAt)
        case .notFound(let identified):
            state = .notFound(request.isBarcode ? code : nil, identified)
        case .failed(let offline):
            // A product saved to history opens offline even if it never reached the cache.
            if let code, let saved = history.first(where: { $0.key == code })?.product {
                state = .loaded(saved, cachedAt: nil)
            } else {
                state = .failed(offline: offline)
            }
        }
    }

    private func shareText(_ p: Product) -> String {
        var s = p.displayName + (p.brand.map { " — \($0)" } ?? "") + "\n" + String(localized: "Nufo Score \(p.nufoScore)/100")
        if let g = p.nutriscoreGrade { s += " · Nutri-Score \(g.uppercased())" }
        if let n = p.novaGroup { s += " · NOVA \(n)" }
        if let k = p.nutritionPer100g.calories { s += "\n" + String(localized: "\(Int(k)) kcal per 100 g") }
        return s + "\n" + String(localized: "Source: \(p.source)")
    }
}

extension ResultView {
    /// Lookup progress arrives off the main actor.
    private var report: @Sendable (LookupStep) -> Void {
        let binding = $step
        return { s in Task { @MainActor in binding.wrappedValue = s } }
    }
}

private extension ResultRequest {
    var isBarcode: Bool { if case .barcode = self { true } else { false } }
}

extension Product {
    var displayName: String { name.isEmpty ? String(localized: "Unnamed product") : name }
}

private struct Message<Actions: View>: View {
    let icon: String
    let title: LocalizedStringKey
    let message: String
    @ViewBuilder let actions: Actions
    init(icon: String, title: LocalizedStringKey, body: String, @ViewBuilder actions: () -> Actions) {
        self.icon = icon; self.title = title; self.message = body; self.actions = actions()
    }
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: icon).font(.largeTitle).foregroundStyle(Color.nufoGreen).frame(width: 76, height: 76).background(Color.nufoGreen.opacity(0.12), in: Circle())
            Text(title).font(.title2.bold()).multilineTextAlignment(.center)
            Text(message).multilineTextAlignment(.center).foregroundStyle(Color.nufoSecondaryText)
            VStack(spacing: 10) { actions }.padding(.top, 8)
        }
        .padding(32).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct Skeleton: View {
    let preview: SearchHit?
    let step: LookupStep?
    @State private var pulse = false
    @State private var elapsed = 0.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Real step first; after 7 s the line admits it is slow. Nothing under 1 s.
    private var caption: String? {
        if elapsed >= Wait.slowAfter { return String(localized: "Taking longer than usual, still trying…") }
        guard elapsed >= Wait.captionAfter else { return nil }
        switch step {
        case .usda: return String(localized: "Not there, checking USDA…")
        case .otherDatabases: return String(localized: "Checking other barcode databases…")
        case .openFoodFacts, nil: return String(localized: "Checking Open Food Facts…")
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // The row's photo and name land in place at once, so opening reads as one continuous motion.
            if let preview, preview.imageUrl != nil {
                NufoCard(corner: 28) {
                    ProductThumb(url: preview.imageUrl, name: preview.name, hero: true).frame(height: 260)
                        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                }
                .foregroundStyle(.primary)
            } else {
                RoundedRectangle(cornerRadius: 28).frame(height: 220)
            }
            if let preview {
                Text(preview.name).font(.title.bold()).foregroundStyle(.primary)
            } else {
                RoundedRectangle(cornerRadius: 8).frame(width: 240, height: 26)
            }
            WaitCaption(text: caption)
            HStack { ForEach(0..<3, id: \.self) { _ in Capsule().frame(width: 96, height: 28) } }
            RoundedRectangle(cornerRadius: 22).frame(height: 230).padding(.top, 10)
            Spacer()
        }
        // Placeholders stay hidden for the first moments, so a fast answer never flashes a skeleton.
        .foregroundStyle(Color.secondary.opacity(elapsed < Wait.showAfter ? 0 : (pulse ? 0.18 : 0.08)))
        .padding(20)
        .onAppear { if !reduceMotion { withAnimation(.easeInOut(duration: 0.65).repeatForever()) { pulse = true } } }
        .task { await Wait.tick { elapsed = $0 } }
        .accessibilityLabel(Text("Loading nutrition"))
    }
}
private struct ProductDetail: View {
    let product: Product
    let cachedAt: Date?
    @Environment(\.colorScheme) private var scheme
    @AppStorage(Prefs.units) private var unitsRaw = Units.metric.rawValue
    @AppStorage(Prefs.diet) private var dietRaw = Diet.none.rawValue
    @AppStorage(Prefs.allergens) private var allergensRaw = ""
    @State private var portion: Portion = .per100
    @State private var customGrams = 150.0
    @State private var tab: DetailTab = .nutrition
    @State private var showMethod = false

    private var lang: String { dataLang }
    private var imperial: Bool { unitsRaw == Units.imperial.rawValue }
    private func weight(_ g: Double) -> String { imperial ? "\(fmt(g / 28.3495)) oz" : "\(fmt(g)) g" }
    private var hasServing: Bool { product.servingGrams != nil || product.nutritionPerServing != nil }

    private var grams: Double? {
        switch portion {
        case .per100: 100; case .serving: product.servingGrams; case .bowl: bowlGrams; case .plate: plateGrams; case .custom: customGrams
        }
    }
    private var nutrition: Nutrition {
        if portion == .serving, product.servingGrams == nil { return product.nutritionPerServing ?? product.nutritionPer100g }
        return product.nutritionPer100g.scaled((grams ?? 100) / 100)
    }
    private var portionText: String {
        portion == .serving ? (product.servingSize ?? grams.map(weight) ?? String(localized: "1 serving")) : weight(grams ?? 100)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                NufoCard(corner: 28) {
                    ProductThumb(url: product.imageUrl, name: product.displayName, hero: true)
                        .frame(height: product.imageUrl == nil ? 120 : 260)
                        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                }
                .enterStagger(0)
                VStack(alignment: .leading, spacing: 4) {
                    Calligraph(text: product.displayName, font: .title.bold(), stagger: 0.012).accessibilityAddTraits(.isHeader)
                    let meta = [product.brand, product.quantity].compactMap { $0 }.joined(separator: " · ")
                    if !meta.isEmpty { Text(meta).foregroundStyle(Color.nufoSecondaryText) }
                }
                .padding(.top, 18).enterStagger(1)
                provenance.padding(.top, 12).enterStagger(2)
                if let cachedAt {
                    Label(String(localized: "Offline copy saved on \(cachedAt.formatted(date: .abbreviated, time: .omitted)). Reconnect for the latest data."),
                          systemImage: "icloud.slash")
                        .font(.footnote).foregroundStyle(Color.nufoSecondaryText).padding(.top, 10)
                }
                alerts
                scores.padding(.top, 16).enterStagger(4)
                Text("Portion").font(.title3.bold()).padding(.top, 22)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Portion.allCases.filter { $0 != .serving || hasServing }, id: \.self) { opt in
                            chip(portionLabel(opt), selected: portion == opt) { withAnimation(.nufo) { portion = opt } }
                        }
                    }
                    .padding(.vertical, 10)
                }
                if portion == .custom {
                    VStack(alignment: .leading) {
                        Text(weight(customGrams)).font(.headline).contentTransition(.numericText())
                        Slider(value: $customGrams, in: 10...1000, step: 5)
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
                calories
                if product.source == OffParser.source, let code = product.barcode { PricesCard(barcode: code).padding(.top, 16) }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(DetailTab.allCases, id: \.self) { t in
                            Button { withAnimation(.nufo) { tab = t } } label: {
                                Text(t.title).font(.subheadline.weight(.semibold)).padding(.horizontal, 16).padding(.vertical, 10)
                                    .foregroundStyle(tab == t ? .white : .primary)
                                    .background(tab == t ? Color.nufoGreen : Color.nufoCard, in: Capsule())
                            }
                            .buttonStyle(PressableStyle())
                            .accessibilityAddTraits(tab == t ? .isSelected : [])
                        }
                    }
                }
                .padding(.top, 20)
                Group {
                    switch tab {
                    case .nutrition: NutritionTab(product: product, n: nutrition, portionLabel: portion == .per100 ? nil : portionText)
                    case .vitamins: VitaminsTab(product: product, n: nutrition, lang: lang)
                    case .ingredients: IngredientsTab(product: product, lang: lang)
                    case .additives: AdditivesTab(product: product, lang: lang)
                    case .environment: EnvironmentTab(product: product, lang: lang)
                    }
                }
                .id(tab)
                .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .opacity))
                .padding(.top, 14)
                footer.padding(.top, 26)
            }
            .padding(20)
        }
        .sheet(isPresented: $showMethod) { MethodologySheet() }
    }

    private var provenance: some View {
        FlowLayout(spacing: 6) {
            TrustChip(icon: "checkmark.seal", text: String(localized: "Source: \(product.source)"))
            if product.lastUpdated > 0 {
                let d = Date(timeIntervalSince1970: Double(product.lastUpdated) / 1000).formatted(date: .abbreviated, time: .omitted)
                TrustChip(icon: "clock", text: String(localized: "Updated \(d)"))
            }
            if let c = product.completeness { TrustChip(icon: "chart.pie", text: String(localized: "Data \(Int(c * 100))% complete")) }
            if product.soldInGreece { TrustChip(icon: "mappin.and.ellipse", text: String(localized: "Sold in Greece")) }
            if product.source != OffParser.source && lang == "el" {
                TrustChip(icon: "info.circle", text: String(localized: "English-language data"), tint: Grade.color("c"))
            }
        }
    }

    private func portionLabel(_ p: Portion) -> String {
        switch p {
        case .per100: imperial ? "100 g (3.5 oz)" : "100 g"
        case .serving: product.servingGrams.map { String(localized: "Serving · \(weight($0))") } ?? String(localized: "Serving")
        case .bowl: String(localized: "Bowl · \(weight(bowlGrams))")
        case .plate: String(localized: "Plate · \(weight(plateGrams))")
        case .custom: String(localized: "Custom")
        }
    }

    private func chip(_ text: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.subheadline.weight(.semibold)).padding(.horizontal, 16).padding(.vertical, 10)
                .foregroundStyle(selected ? .white : .primary)
                .background(selected ? Color.nufoGreen : Color.nufoCard, in: Capsule())
                .overlay(Capsule().stroke(Color.secondary.opacity(selected ? 0 : 0.25)))
        }
        .buttonStyle(PressableStyle())
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    @ViewBuilder private var alerts: some View {
        let hits = product.matchingAllergens(Prefs.tags(allergensRaw))
        let note = dietNote(product, Diet(rawValue: dietRaw) ?? .none)
        if !hits.isEmpty || note != nil {
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle").foregroundStyle(Grade.color("e"))
                VStack(alignment: .leading, spacing: 2) {
                    if !hits.isEmpty {
                        Text("Contains \(hits.map { product.tagName($0, lang: lang) }.joined(separator: ", "))").font(.headline).foregroundStyle(Grade.color("e"))
                    }
                    if let note { Text(dietNoteText(note)).font(.subheadline) }
                }
                Spacer(minLength: 0)
            }
            .padding(14).background(Grade.color("e").opacity(0.1), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .padding(.top, 14).enterStagger(3)
        }
    }

    private var scores: some View {
        NufoCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 18) {
                    ScoreRing(score: product.nufoScore)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Nufo Score").font(.caption).foregroundStyle(Color.nufoSecondaryText)
                        Text(verdictText(Verdict.of(product.nufoScore))).font(.title2.bold())
                            .foregroundStyle(scoreTextColor(product.nufoScore, dark: scheme == .dark))
                        Button { showMethod = true } label: { Label("How is this calculated?", systemImage: "info.circle").font(.subheadline.weight(.semibold)) }
                    }
                }
                Divider()
                GradeScale(title: "Nutri-Score", values: ["a", "b", "c", "d", "e"], active: product.nutriscoreGrade, color: Grade.color,
                           description: "Overall nutritional quality, A (best) to E.")
                GradeScale(title: "NOVA", values: ["1", "2", "3", "4"], active: product.novaGroup.map(String.init), color: { Grade.nova(Int($0)) },
                           description: product.novaGroup.map(novaText))
                GradeScale(title: "Eco-Score", values: ["a", "b", "c", "d", "e"], active: product.ecoscoreGrade, color: Grade.color,
                           description: "Estimated environmental impact, A (lowest) to E.")
                Divider()
                let reasons = Scoring.reasons(product)
                if reasons.isEmpty {
                    Text("No penalties or bonuses from the data available.").font(.caption).foregroundStyle(Color.nufoSecondaryText)
                } else {
                    Text("Why this score").font(.subheadline.weight(.semibold))
                    FlowLayout(spacing: 6) {
                        ForEach(reasons, id: \.self) { r in
                            Pill(text: reasonText(r.reason) + "  " + (r.delta > 0 ? "+\(r.delta)" : "−\(-r.delta)"),
                                 color: r.delta > 0 ? Grade.color("a") : Grade.color("e"))
                        }
                    }
                }
            }
            .padding(18)
        }
    }

    private var calories: some View {
        NufoCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Calories · \(portionText)").font(.caption).foregroundStyle(Color.nufoSecondaryText)
                    if let k = nutrition.calories {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            CalligraphNumber(value: k, font: .system(size: 44, weight: .bold))
                            Text("kcal").font(.headline).foregroundStyle(Color.nufoSecondaryText)
                        }
                    } else {
                        Text("Not available").font(.title2).foregroundStyle(Color.nufoSecondaryText)
                    }
                }
                Spacer()
                MacroDonut(n: nutrition)
            }
            .padding(20)
        }
        .padding(.top, 6)
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 4) {
            let updated = product.lastUpdated > 0
                ? Date(timeIntervalSince1970: Double(product.lastUpdated) / 1000).formatted(date: .abbreviated, time: .omitted)
                : String(localized: "Not available")
            Text("Source: \(product.source) • Last updated: \(updated)")
            Text(product.source == OffParser.source ? LocalizedStringKey("Data © Open Food Facts contributors, licensed under ODbL.") : "Public-domain data from the U.S. Department of Agriculture.")
            Text("Nufo provides nutrition information, not medical advice.").padding(.top, 4)
        }
        .font(.caption).foregroundStyle(Color.nufoSecondaryText)
    }
}
// MARK: - Localized labels for typed data

func verdictText(_ v: Verdict) -> String {
    switch v {
    case .excellent: String(localized: "Excellent choice"); case .good: String(localized: "Good choice")
    case .fair: String(localized: "Fair choice"); case .poor: String(localized: "Poor choice"); case .veryPoor: String(localized: "Very poor choice")
    }
}

func reasonText(_ r: Reason) -> String {
    switch r {
    case .nutriB: "Nutri-Score B"; case .nutriC: "Nutri-Score C"; case .nutriD: "Nutri-Score D"; case .nutriE: "Nutri-Score E"
    case .nova2: String(localized: "Processed ingredient"); case .nova3: String(localized: "Processed")
    case .nova4: String(localized: "Ultra-processed"); case .highProtein: String(localized: "High protein")
    case .highFiber: String(localized: "High fibre"); case .highSugar: String(localized: "High sugar")
    case .highSalt: String(localized: "High salt"); case .highSatFat: String(localized: "High saturated fat")
    }
}

func insightText(_ i: InsightKind) -> String {
    switch i {
    case .highProtein: String(localized: "High protein"); case .goodFiber: String(localized: "Good source of fibre")
    case .lowFiber: String(localized: "Low fibre"); case .highSugar: String(localized: "High sugar")
    case .lowSugar: String(localized: "Low sugar"); case .highSodium: String(localized: "High sodium")
    case .lowSodium: String(localized: "Low sodium"); case .highSatFat: String(localized: "High saturated fat")
    case .ultraProcessed: String(localized: "Ultra-processed"); case .unprocessed: String(localized: "Unprocessed")
    }
}

func novaText(_ group: Int) -> LocalizedStringKey {
    switch group {
    case 1: "Unprocessed or minimally processed"; case 2: "Processed culinary ingredient"; case 3: "Processed food"
    default: "Ultra-processed food"
    }
}

func dietNoteText(_ n: DietNote) -> String {
    switch n {
    case .notVegan: String(localized: "Not vegan: declares animal-derived allergens.")
    case .notVegetarian: String(localized: "Not vegetarian: contains fish or seafood.")
    case .highCarbsKeto(let g): String(localized: "High in carbs for keto (\(fmt(g)) g per 100 g).")
    case .notLowSodium(let g): String(localized: "Not low-sodium: \(fmt(g)) g salt per 100 g.")
    }
}

// MARK: - Nutrition

/// Energy split between protein, carbs and fat (4 / 4 / 9 kcal per gram), drawn with Swift Charts.
private struct MacroDonut: View {
    let n: Nutrition
    var body: some View {
        let parts: [(LocalizedStringKey, Double, Color)] = [
            ("Protein", (n.protein ?? 0) * 4, Color(hex: 0x43A047)),
            ("Carbs", (n.carbs ?? 0) * 4, Color(hex: 0xFFB300)),
            ("Fat", (n.fat ?? 0) * 9, Color(hex: 0xFF7043)),
        ]
        let total = parts.reduce(0) { $0 + $1.1 }
        VStack(alignment: .leading, spacing: 6) {
            Chart(parts.indices, id: \.self) { i in
                SectorMark(angle: .value("kcal", total > 0 ? parts[i].1 : 1), innerRadius: .ratio(0.7), angularInset: 1)
                    .foregroundStyle(total > 0 ? parts[i].2 : Color.secondary.opacity(0.15))
            }
            .frame(width: 74, height: 74)
            ForEach(parts.indices, id: \.self) { i in
                HStack(spacing: 5) { Circle().fill(parts[i].2).frame(width: 7, height: 7); Text(parts[i].0).font(.caption2).foregroundStyle(Color.nufoSecondaryText) }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(total > 0
            ? Text("Energy from protein \(Int(parts[0].1 / total * 100))%, carbs \(Int(parts[1].1 / total * 100))%, fat \(Int(parts[2].1 / total * 100))%")
            : Text("Macro split not available"))
    }
}

/// A row of the EU nutrition declaration, optionally with an FSA traffic-light level.
private struct DeclRow: Identifiable {
    let id = UUID()
    let label: LocalizedStringKey
    let per100: Double?
    let scaled: Double?
    var kcal = false
    var indent = false
    var level: Level? = nil
}

private struct NutritionTab: View {
    let product: Product
    let n: Nutrition
    let portionLabel: String?

    var body: some View {
        let h = product.nutritionPer100g
        let rows = [
            DeclRow(label: "Energy", per100: h.calories, scaled: n.calories, kcal: true),
            DeclRow(label: "Fat", per100: h.fat, scaled: n.fat, level: h.fat.map(Scoring.fatLevel)),
            DeclRow(label: "of which saturates", per100: h.saturatedFat, scaled: n.saturatedFat, indent: true, level: h.saturatedFat.map(Scoring.satFatLevel)),
            DeclRow(label: "Carbohydrate", per100: h.carbs, scaled: n.carbs),
            DeclRow(label: "of which sugars", per100: h.sugar, scaled: n.sugar, indent: true, level: h.sugar.map(Scoring.sugarLevel)),
            DeclRow(label: "Fibre", per100: h.fiber, scaled: n.fiber),
            DeclRow(label: "Protein", per100: h.protein, scaled: n.protein),
            DeclRow(label: "Salt", per100: h.salt, scaled: n.salt, level: h.salt.map(Scoring.saltLevel)),
        ]
        VStack(alignment: .leading, spacing: 12) {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible())], spacing: 12) {
                NutrientCard(icon: "fish", label: String(localized: "Protein"), grams: n.protein, tint: Color(hex: 0x43A047)).enterStagger(0)
                NutrientCard(icon: "leaf", label: String(localized: "Carbohydrate"), grams: n.carbs, tint: Color(hex: 0xFFA000)).enterStagger(1)
                NutrientCard(icon: "drop", label: String(localized: "Fat"), grams: n.fat, tint: Color(hex: 0xFF7043)).enterStagger(2)
                NutrientCard(icon: "circle.grid.cross", label: String(localized: "Fibre"), grams: n.fiber, tint: Color(hex: 0x1E88E5)).enterStagger(3)
            }
            // Mirrors the table printed on EU packaging, so every number can be checked against the pack.
            NufoCard {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Nutrition declaration").font(.headline).accessibilityAddTraits(.isHeader)
                    HStack {
                        Spacer()
                        Text("per 100 g").frame(width: 84, alignment: .trailing)
                        if let portionLabel { Text("per \(portionLabel)").frame(width: 92, alignment: .trailing).lineLimit(2) }
                    }
                    .font(.caption).foregroundStyle(Color.nufoSecondaryText).padding(.top, 10)
                    Rectangle().fill(Color.primary).frame(height: 2).padding(.top, 8)
                    ForEach(rows) { r in
                        HStack {
                            HStack(spacing: 8) {
                                Text(r.label).fontWeight(r.indent ? .regular : .semibold)
                                if let l = r.level { Circle().fill(l.color).frame(width: 10, height: 10) }
                            }
                            .padding(.leading, r.indent ? 14 : 0)
                            Spacer()
                            Text(cell(r.per100, r.kcal)).frame(width: 84, alignment: .trailing)
                            if portionLabel != nil { Text(cell(r.scaled, r.kcal)).fontWeight(.semibold).frame(width: 92, alignment: .trailing) }
                        }
                        .font(.subheadline).padding(.vertical, 10)
                        .accessibilityElement(children: .combine)
                        if r.id != rows.last?.id { Divider() }
                    }
                    HStack(spacing: 14) {
                        ForEach(Level.allCases, id: \.self) { l in
                            HStack(spacing: 5) { Circle().fill(l.color).frame(width: 10, height: 10); Text(l.label).font(.caption2).foregroundStyle(Color.nufoSecondaryText) }
                        }
                    }
                    .padding(.top, 12)
                    Text("Colours follow UK FSA front-of-pack thresholds for foods, per 100 g.").font(.caption2).foregroundStyle(Color.nufoSecondaryText).padding(.top, 4)
                }
                .padding(.horizontal, 18).padding(.vertical, 16)
            }
            .enterStagger(4)
            let insights = Scoring.insights(product)
            if !insights.isEmpty {
                Text("Highlights (per 100 g)").font(.headline).padding(.top, 4)
                FlowLayout(spacing: 8) { ForEach(insights, id: \.self) { Pill(text: insightText($0), color: $0.positive ? Grade.color("a") : Grade.color("e")) } }
            }
        }
    }

    /// EU declarations state macronutrients in grams, never mg.
    private func cell(_ v: Double?, _ kcal: Bool) -> String {
        guard let v else { return "—" }
        return kcal ? "\(Int(v.rounded())) kcal" : "\(fmt(v)) g"
    }
}

private struct InfoCard<C: View>: View {
    @ViewBuilder let content: C
    var body: some View { NufoCard { VStack(alignment: .leading, spacing: 8) { content }.frame(maxWidth: .infinity, alignment: .leading).padding(18) } }
}

private struct VitaminsTab: View {
    let product: Product
    let n: Nutrition
    let lang: String
    var body: some View {
        InfoCard {
            let rows = Micronutrients.all.map(\.0).compactMap { id in (n.vitamins[id] ?? n.minerals[id]).map { (id, $0) } }
            if rows.isEmpty {
                Text("Not available — \(product.source) has no vitamin or mineral data for this product.").foregroundStyle(Color.nufoSecondaryText)
            } else {
                ForEach(rows, id: \.0) { id, value in
                    let (v, u) = formatGrams(value)
                    HStack { Text(Micronutrients.name(id, lang: lang)); Spacer(); Text("\(v) \(u)").fontWeight(.semibold) }.padding(.vertical, 6)
                    Divider()
                }
            }
        }
    }
}

private struct IngredientsTab: View {
    let product: Product
    let lang: String
    var body: some View {
        InfoCard {
            Text("Ingredients").font(.headline)
            if let text = product.ingredientsText { Text(text) }
            else { Text("Not available — \(product.source) has no ingredient list for this product.").foregroundStyle(Color.nufoSecondaryText) }
            Text("Allergens").font(.headline).padding(.top, 8)
            if product.allergenTags.isEmpty {
                Text("None declared by \(product.source).").foregroundStyle(Color.nufoSecondaryText)
            } else {
                FlowLayout(spacing: 8) { ForEach(product.allergenTags, id: \.self) { Pill(text: product.tagName($0, lang: lang), color: Grade.color("e")) } }
            }
        }
    }
}

private struct AdditivesTab: View {
    let product: Product
    let lang: String
    var body: some View {
        InfoCard {
            if product.additiveTags.isEmpty {
                Text("No additives listed by \(product.source).").foregroundStyle(Color.nufoSecondaryText)
            } else {
                ForEach(product.additiveTags, id: \.self) { a in
                    HStack(spacing: 12) { Circle().fill(Grade.color("c")).frame(width: 8, height: 8); Text(product.tagName(a, lang: lang)).fontWeight(.medium) }.padding(.vertical, 6)
                }
            }
        }
    }
}

private struct EnvironmentTab: View {
    let product: Product
    let lang: String
    var body: some View {
        InfoCard {
            GradeScale(title: "Eco-Score", values: ["a", "b", "c", "d", "e"], active: product.ecoscoreGrade, color: Grade.color,
                       description: "Estimated environmental impact, A (lowest) to E.")
            if product.ecoscoreGrade == nil {
                Text("\(product.source) has not computed an Eco-Score for this product.").font(.subheadline).foregroundStyle(Color.nufoSecondaryText)
            }
            if !product.labelTags.isEmpty {
                Text("Labels").font(.headline).padding(.top, 8)
                FlowLayout(spacing: 8) { ForEach(product.labelTags.prefix(12), id: \.self) { Pill(text: product.tagName($0, lang: lang), color: .nufoGreen) } }
            }
            // Only categories with a translated name: raw ids in other languages look broken.
            let cats = product.categoryTags.suffix(4).filter { product.tagNames[$0] != nil }
            if !cats.isEmpty {
                Text("Categories").font(.headline).padding(.top, 8)
                FlowLayout(spacing: 8) { ForEach(cats, id: \.self) { Pill(text: product.tagName($0, lang: lang), color: .secondary) } }
            }
        }
    }
}

/// Wrapping row layout for chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0, maxX: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x + s.width > width, x > 0 { x = 0; y += row + spacing; row = 0 }
            x += s.width + spacing; row = max(row, s.height); maxX = max(maxX, x - spacing)
        }
        return CGSize(width: maxX, height: y + row)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, row: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x + s.width > bounds.maxX, x > bounds.minX { x = bounds.minX; y += row + spacing; row = 0 }
            v.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(s))
            x += s.width + spacing; row = max(row, s.height)
        }
    }
}
/// A barcode no food database knows, but a product database identified: show what it is and offer a name search.
private struct Identity: View {
    let item: Identified
    let onSearch: () -> Void
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                NufoCard(corner: 28) {
                    ProductThumb(url: item.imageUrl, name: item.name).frame(height: item.imageUrl == nil ? 120 : 220)
                        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                }
                Text(item.name).font(.title2.bold()).multilineTextAlignment(.center)
                if let brand = item.brand { Text(brand).foregroundStyle(Color.nufoSecondaryText) }
                Text("Identified, but no nutrition data").font(.headline).padding(.top, 6)
                Text(String(localized: "\(item.source) lists this barcode as «\(item.name)». No food database has its nutrition facts yet."))
                    .multilineTextAlignment(.center).foregroundStyle(Color.nufoSecondaryText)
                Button(String(localized: "Search «\(item.name)»"), action: onSearch).buttonStyle(.borderedProminent)
            }
            .padding(24)
        }
    }
}

/// Latest shopper-reported prices from Open Prices, with loading, empty and failed states.
private struct PricesCard: View {
    let barcode: String
    @EnvironmentObject private var model: AppModel
    @State private var reports: Result<[PriceReport], Error>?

    var body: some View {
        NufoCard {
            VStack(alignment: .leading, spacing: 12) {
                Label("Prices", systemImage: "tag").font(.headline).foregroundStyle(.primary)
                switch reports {
                case nil:
                    ForEach(0..<2, id: \.self) { _ in RoundedRectangle(cornerRadius: 8).fill(Color.secondary.opacity(0.1)).frame(height: 36) }
                case .success(let list) where list.isEmpty:
                    Text("No price reports yet for this product.").foregroundStyle(Color.nufoSecondaryText)
                case .success(let list):
                    ForEach(list, id: \.self) { r in
                        HStack(alignment: .firstTextBaseline) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text([r.store, r.city].compactMap { $0 }.joined(separator: ", ")).lineLimit(1)
                                Text([r.country, Self.day(r.date)].compactMap { $0 }.joined(separator: " · "))
                                    .font(.footnote).foregroundStyle(Color.nufoSecondaryText)
                            }
                            Spacer()
                            Text(r.price, format: .currency(code: r.currency)).font(.headline).monospacedDigit()
                        }
                    }
                    Text("Crowdsourced receipts and shelf photos via Open Prices.").font(.caption).foregroundStyle(Color.nufoSecondaryText)
                case .failure:
                    Text("Prices couldn't be loaded right now.").foregroundStyle(Color.nufoSecondaryText)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
        }
        .task(id: barcode) {
            do { reports = .success(try await model.api.prices(barcode)) } catch { reports = .failure(error) }
        }
    }

    private static func day(_ iso: String) -> String? {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.locale = Locale(identifier: "en_US_POSIX")
        return f.date(from: iso)?.formatted(date: .abbreviated, time: .omitted)
    }
}
