import SwiftUI
import SwiftData

struct HistoryView: View {
    @Namespace private var zoom
    @Environment(\.modelContext) private var context
    @EnvironmentObject private var model: AppModel
    @Query(sort: \HistoryItem.savedAt, order: .reverse) private var items: [HistoryItem]
    @State private var confirmClear = false

    var body: some View {
        NavigationStack {
            Group {
                if items.isEmpty {
                    ContentUnavailableView {
                        Label("No scans yet", systemImage: "clock.arrow.circlepath")
                    } description: {
                        Text("Everything you scan is saved here, offline, on this phone only.")
                    } actions: {
                        Button("Scan something") { model.tab = .scan }.buttonStyle(.borderedProminent)
                    }
                } else {
                    List {
                        ForEach(Array(items.enumerated()), id: \.element.key) { i, item in
                            if let p = item.product {
                                NavigationLink(value: ResultRequest.product(p)) { HistoryRow(product: p).zoomSource(p.key, in: zoom) }
                                    .listRowBackground(Color.nufoCard)
                                    .enterStagger(i)
                            }
                        }
                        .onDelete { idx in
                            withAnimation(.nufo) { for i in idx { context.delete(items[i]) } }
                            try? context.save()
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Color.nufoBackground)
            .navigationTitle("History")
            .toolbar { if !items.isEmpty { Button("Clear all", role: .destructive) { confirmClear = true } } }
            .navigationDestination(for: ResultRequest.self) { ResultView(request: $0).zoomDestination($0.zoomID, in: zoom) }
            .confirmationDialog("Clear all history?", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Clear \(items.count) items", role: .destructive) {
                    try? context.delete(model: HistoryItem.self); try? context.save()
                    ProductCache().clear()
                }
            } message: { Text("This removes all saved products from this device. It can't be undone.") }
        }
    }
}

private struct HistoryRow: View {
    let product: Product
    var body: some View {
        let n = product.nutritionPer100g
        HStack(spacing: 14) {
            ProductThumb(url: product.imageUrl, name: product.name).frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(product.displayName).font(.headline).lineLimit(1)
                Text(historyLine(n))
                    .font(.subheadline).foregroundStyle(Color.nufoSecondaryText).lineLimit(1)
                // Calories lead the last line, so the macro line always fits all three values.
                Text((n.calories.map { "\(Int($0)) kcal " } ?? "") + String(localized: "per 100 g · \(product.brand ?? product.source)"))
                    .font(.caption).foregroundStyle(Color.nufoSecondaryText).lineLimit(1)
            }
            Spacer(minLength: 8)
            Text("\(product.nufoScore)").font(.headline).foregroundStyle(.white)
                .frame(width: 40, height: 40).background(Grade.score(product.nufoScore), in: Circle())
                .accessibilityLabel("Nufo Score \(product.nufoScore)")
        }
        .padding(.vertical, 4)
    }
}

/// "P 9.5g · C 4g · F 0g", with localized initials (Π/Υ/Λ in Greek) and a dash for missing values.
private func historyLine(_ n: Nutrition) -> String {
    func part(_ key: String.LocalizationValue, _ v: Double?) -> String { "\(String(localized: key)) " + (v.map { "\(fmt($0))g" } ?? "—") }
    return [part("P", n.protein), part("C", n.carbs), part("F", n.fat)].joined(separator: " · ")
}