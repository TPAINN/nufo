import SwiftUI
import SwiftData
import Network

@main
struct NufoApp: App {
    @StateObject private var model = AppModel()
    @AppStorage(Prefs.theme) private var theme = ThemeMode.system.rawValue

    init() {
        // Room for a few hundred product photos, so history and recents keep their pictures offline.
        URLCache.shared = URLCache(memoryCapacity: 32 << 20, diskCapacity: 300 << 20)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(ThemeMode(rawValue: theme)?.scheme)
                .tint(.nufoGreen)
        }
        .modelContainer(for: HistoryItem.self)
    }
}

enum AppTab: Hashable { case scan, search, history, settings }

/// What a result screen should load.
enum ResultRequest: Hashable {
    case barcode(String)
    case hit(SearchHit)
    case product(Product)
}

/// Cross-screen state: the selected tab and the search box (photo recognition hands its guess to Search).
@MainActor
final class AppModel: ObservableObject {
    @Published var tab: AppTab = .scan
    @Published var query = ""
    @Published private(set) var online = true
    let api = FoodAPI()
    private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in withAnimation(.nufo) { self?.online = online } }
        }
        monitor.start(queue: DispatchQueue(label: "nufo.network"))
    }

    func search(for text: String) {
        query = text
        tab = .search
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @AppStorage(Prefs.onboarded) private var onboarded = false
    @State private var launching = true
    @State private var handedOff = false

    var body: some View {
        ZStack {
            if onboarded {
                TabView(selection: $model.tab) {
                    HomeView().offlineInset().tabItem { Label("Scan", systemImage: "barcode.viewfinder") }.tag(AppTab.scan)
                    SearchView().offlineInset().tabItem { Label("Search", systemImage: "magnifyingglass") }.tag(AppTab.search)
                    HistoryView().offlineInset().tabItem { Label("History", systemImage: "clock.arrow.circlepath") }.tag(AppTab.history)
                    SettingsView().offlineInset().tabItem { Label("Settings", systemImage: "gearshape") }.tag(AppTab.settings)
                }
                .transition(.opacity.combined(with: .scale(scale: 0.96)))
            } else {
                // Welcome to tabs is a peer change: fade through, like switching tabs on Android.
                WelcomeView { withAnimation(Motion.easeOut()) { onboarded = true } }
                    .transition(.opacity)
            }
            if launching { LaunchOverlay(onHandOff: { handedOff = true }) { launching = false }.zIndex(1) }
        }
        .environment(\.appReady, handedOff)
    }
}
private struct OfflineInset: ViewModifier {
    @EnvironmentObject private var model: AppModel
    /// Reserves space above the tab bar, so the notice never covers content.
    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) { if !model.online { OfflineBanner() } }
    }
}

extension View {
    func offlineInset() -> some View { modifier(OfflineInset()) }
}
