import SwiftUI
import SwiftData

struct SettingsView: View {
    @Environment(\.modelContext) private var context
    @AppStorage(Prefs.units) private var units = Units.metric.rawValue
    @AppStorage(Prefs.theme) private var theme = ThemeMode.system.rawValue
    @AppStorage(Prefs.diet) private var diet = Diet.none.rawValue
    @AppStorage(Prefs.allergens) private var allergensRaw = ""
    @State private var confirmClear = false
    @State private var showAbout = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Units") {
                    Picker("Units", selection: $units) { ForEach(Units.allCases, id: \.rawValue) { Text(LocalizedStringKey($0.rawValue)).tag($0.rawValue) } }
                        .pickerStyle(.segmented)
                }
                Section("Theme") {
                    Picker("Theme", selection: $theme) { ForEach(ThemeMode.allCases, id: \.rawValue) { Text(LocalizedStringKey($0.rawValue)).tag($0.rawValue) } }
                        .pickerStyle(.segmented)
                }
                Section {
                    Button { UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!) } label: {
                        LabeledContent("Language", value: Locale.current.localizedString(forLanguageCode: Locale.current.language.languageCode?.identifier ?? "en") ?? "English")
                    }
                } header: { Text("Language") } footer: { Text("Follows your phone. Food names come in the language the source provides.") }
                Section("Diet preference") {
                    Picker("Diet", selection: $diet) { ForEach(Diet.allCases, id: \.rawValue) { Text(LocalizedStringKey($0.rawValue)).tag($0.rawValue) } }
                }
                Section {
                    ForEach(allergenTags, id: \.self) { tag in
                        Toggle(TagNames.fallback(tag, lang: dataLang) ?? tag, isOn: Binding(
                            get: { Prefs.tags(allergensRaw).contains(tag) },
                            set: { on in
                                var set = Prefs.tags(allergensRaw)
                                if on { set.insert(tag) } else { set.remove(tag) }
                                allergensRaw = set.sorted().joined(separator: ",")
                            }
                        ))
                    }
                } header: { Text("Allergen warnings") } footer: { Text("Products that declare these get a warning banner.") }
                Section {
                    Link("Open Food Facts — ODbL", destination: URL(string: "https://world.openfoodfacts.org")!)
                    Link("USDA FoodData Central — public domain", destination: URL(string: "https://fdc.nal.usda.gov")!)
                    Link("Apple Vision & VisionKit — on-device", destination: URL(string: "https://developer.apple.com/documentation/vision")!)
                } header: { Text("Data sources") } footer: { Text("No account. No tracking. Only barcodes and search terms are sent to these sources.") }
                Section {
                    Button { showAbout = true } label: { LabeledContent("How Nufo works") { Image(systemName: "chevron.right") } }
                }
                Section {
                    Button("Clear history and cache", role: .destructive) { confirmClear = true }
                } footer: { Text("Nufo provides nutrition information, not medical advice. Version 1.0") }
            }
            .tint(.nufoGreen)
            .navigationTitle("Settings")
            .sheet(isPresented: $showAbout) { AboutSheet() }
            .confirmationDialog("Clear local data?", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Clear", role: .destructive) {
                    try? context.delete(model: HistoryItem.self); try? context.save()
                    ProductCache().clear()
                    URLCache.shared.removeAllCachedResponses()
                }
            } message: { Text("Removes all saved scans and cached images from this phone. Settings are kept.") }
        }
    }
}