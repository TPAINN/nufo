import SwiftUI
import SwiftData

struct SearchView: View {
    @EnvironmentObject private var model: AppModel
    @AppStorage(Prefs.allergens) private var allergensRaw = ""
    @State private var filters = SearchFilters()
    @State private var hits: [SearchHit] = []
    @State private var loading = false
    @State private var error: String?
    @State private var searched = false
    /// Results came from the offline fallback over saved products.
    @State private var offline = false
    @State private var attempt = 0
    @Namespace private var zoom
    @Query private var history: [HistoryItem]
    @State private var showFilters = false

    var body: some View {
        NavigationStack {
            List {
                if showFilters { filtersSection }
                if let error {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(error).foregroundStyle(Color.nufoSecondaryText)
                        Button("Retry") { attempt += 1 }.buttonStyle(.bordered).tint(.nufoGreen)
                    }
                    .listRowBackground(Color.clear)
                } else if model.query.trimmingCharacters(in: .whitespaces).isEmpty {
                    hint(String(localized: "Search Open Food Facts and USDA at once, in Greek or English.\nTry «φέτα», «γιαούρτι» or «olive oil»."))
                } else if loading && hits.isEmpty {
                    SearchWait()
                } else if searched && !loading && hits.isEmpty {
                    hint(offline ? String(localized: "You're offline and nothing saved matches «\(model.query)».") : filters.isActive ? String(localized: "No results for «\(model.query)» with these filters.") : String(localized: "No results for «\(model.query)»."))
                } else {
                    if offline { hint(String(localized: "Offline: showing products saved on this phone.")) }
                    ForEach(Array(hits.enumerated()), id: \.element.id) { i, h in
                        NavigationLink(value: ResultRequest.hit(h)) { HitRow(hit: h).zoomSource(ResultRequest.hit(h).zoomID, in: zoom) }
                            .listRowBackground(Color.nufoCard)
                            .enterStagger(i)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.nufoBackground)
            // Refreshing results already on screen: a thin bar. A first load gets skeleton rows instead, never both.
            .overlay(alignment: .top) { if loading && !hits.isEmpty { ProgressView().progressViewStyle(.linear).tint(.nufoGreen) } }
            .navigationTitle("Search")
            .searchable(text: $model.query, prompt: "Foods, brands, meals…")
            .toolbar {
                Button { withAnimation(.nufo) { showFilters.toggle() } } label: {
                    Image(systemName: filters.isActive ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                }
                .accessibilityLabel(showFilters ? "Hide filters" : "Show filters")
            }
            .navigationDestination(for: ResultRequest.self) { ResultView(request: $0).zoomDestination($0.zoomID, in: zoom) }
            // Debounced: restarts whenever the query or filters change.
            .task(id: "\(model.query)|\(filters.offQueryClauses)|\(filters.grades.sorted())|\(filters.nova.sorted())|\(attempt)") { await run() }
            // Back online: replace offline or failed results with fresh ones.
            .onChange(of: model.online) { _, online in if online && (offline || error != nil) { attempt += 1 } }
        }
    }

    private func run() async {
        let q = model.query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { hits = []; searched = false; error = nil; offline = false; return }
        try? await Task.sleep(for: .milliseconds(350))
        if Task.isCancelled { return }
        loading = true; error = nil
        let r = await model.api.search(q, filters: filters, lang: dataLang)
        if Task.isCancelled { return }
        loading = false; searched = true
        switch r {
        case .success(let h): offline = false; withAnimation(.nufo) { hits = h }
        case .failure(let e) where isOffline(e):
            offline = true
            withAnimation(.nufo) { hits = model.api.searchSaved(q, extra: history.compactMap(\.product)) }
        case .failure:
            offline = false; hits = []
            error = String(localized: "The food databases aren't responding. It's on their side, not yours.")
        }
    }

    private func hint(_ s: String) -> some View {
        Text(s).foregroundStyle(Color.nufoSecondaryText).listRowBackground(Color.clear)
    }

    private var filtersSection: some View {
        Section {
            Toggle("Only products sold in Greece", isOn: $filters.greekOnly)
            chips("Category", searchCategories.map { ($0.tag, String(localized: String.LocalizationValue($0.key))) }, selected: { filters.category == $0 }) { t in
                filters.category = filters.category == t ? nil : t
            }
            chips("Nutri-Score", ["a", "b", "c", "d", "e"].map { ($0, $0.uppercased()) }, selected: { filters.grades.contains($0) }) { g in
                if filters.grades.contains(g) { filters.grades.remove(g) } else { filters.grades.insert(g) }
            }
            chips("NOVA", (1...4).map { (String($0), "NOVA \($0)") }, selected: { filters.nova.contains(Int($0)!) }) { n in
                let v = Int(n)!; if filters.nova.contains(v) { filters.nova.remove(v) } else { filters.nova.insert(v) }
            }
            Toggle("Vegetarian", isOn: $filters.vegetarian)
            Toggle("Vegan", isOn: $filters.vegan)
            let mine = Prefs.tags(allergensRaw)
            Toggle(mine.isEmpty ? LocalizedStringKey("Set allergens in Settings") : "Exclude my allergens", isOn: Binding(
                get: { !filters.excludeAllergens.isEmpty },
                set: { filters.excludeAllergens = $0 ? mine : [] }
            ))
            .disabled(mine.isEmpty)
            if filters.needsOffTags {
                Text("Category, origin, diet and allergen filters use Open Food Facts tags, so USDA results are hidden.")
                    .font(.caption).foregroundStyle(Color.nufoSecondaryText)
            }
        }
        .tint(.nufoGreen)
    }

    private func chips(_ title: String, _ items: [(String, String)], selected: @escaping (String) -> Bool, toggle: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption).foregroundStyle(Color.nufoSecondaryText)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(items, id: \.0) { id, label in
                        Button(label) { withAnimation(.nufo) { toggle(id) } }
                            .buttonStyle(.bordered).tint(selected(id) ? .nufoGreen : .secondary)
                    }
                }
            }
        }
    }
}

private struct HitRow: View {
    let hit: SearchHit
    var body: some View {
        HStack(spacing: 14) {
            ProductThumb(url: hit.imageUrl, name: hit.name).frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(hit.name).font(.headline).lineLimit(2)
                Text([hit.brand, hit.caloriesPer100g.map { "\(Int($0)) kcal/100g" }].compactMap { $0 }.joined(separator: " · "))
                    .font(.subheadline).foregroundStyle(Color.nufoSecondaryText).lineLimit(1)
                HStack(spacing: 6) {
                    if hit.soldInGreece {
                        Text("GR").font(.caption2.bold()).foregroundStyle(.white).padding(.horizontal, 4).padding(.vertical, 1)
                            .background(Color.nufoGreen, in: RoundedRectangle(cornerRadius: 4)).accessibilityLabel(Text("Sold in Greece"))
                    }
                    Text(hit.source).font(.caption.weight(.semibold)).foregroundStyle(Color.nufoGreen)
                }
            }
            Spacer(minLength: 8)
            Text(hit.nutriscoreGrade?.uppercased() ?? "–").font(.headline)
                .foregroundStyle(hit.nutriscoreGrade == nil ? Color.nufoSecondaryText : .white)
                .frame(width: 36, height: 36)
                .background(hit.nutriscoreGrade == nil ? Color.secondary.opacity(0.15) : Grade.color(hit.nutriscoreGrade), in: RoundedRectangle(cornerRadius: 11))
                .accessibilityLabel(hit.nutriscoreGrade.map { "Nutri-Score \($0.uppercased())" } ?? "Nutri-Score not available")
        }
        .padding(.vertical, 4)
    }
}
private struct HitSkeleton: View {
    var body: some View {
        HStack(spacing: 14) {
            RoundedRectangle(cornerRadius: 14).frame(width: 64, height: 64)
            VStack(alignment: .leading, spacing: 8) {
                RoundedRectangle(cornerRadius: 6).frame(width: 170, height: 16)
                RoundedRectangle(cornerRadius: 6).frame(width: 110, height: 12)
            }
            Spacer()
        }
        .foregroundStyle(Color.secondary.opacity(0.12))
        .padding(.vertical, 4)
        .accessibilityHidden(true)
    }
}

/// First-load placeholder rows: hidden for the first moments, then a caption that says what is happening.
private struct SearchWait: View {
    @State private var elapsed = 0.0
    var body: some View {
        // The caption row is always present (empty at first) and owns the timer.
        WaitCaption(text: elapsed >= Wait.slowAfter ? String(localized: "Taking longer than usual, still trying…")
                    : elapsed >= Wait.captionAfter ? String(localized: "Searching Open Food Facts and USDA…") : nil)
            .listRowBackground(Color.clear)
            .task { await Wait.tick { elapsed = $0 } }
        if elapsed >= Wait.showAfter {
            ForEach(0..<5, id: \.self) { i in HitSkeleton().listRowBackground(Color.nufoCard).enterStagger(i) }
        }
    }
}
