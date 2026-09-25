import SwiftUI
import SwiftData

/// Signature writes itself once per launch, not every time Home is revisited.
private var homeLogoPlayed = false

struct HomeView: View {
    @Namespace private var zoom
    @EnvironmentObject private var model: AppModel
    @Query(sort: \HistoryItem.savedAt, order: .reverse) private var history: [HistoryItem]
    @State private var path = NavigationPath()
    @State private var showScanner = false
    @State private var photoSource: PhotoSource?
    @State private var showAbout = false

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 2) {
                        TegakiLogo(animate: !homeLogoPlayed, speed: 1.5, onFinished: { homeLogoPlayed = true }).frame(height: 46)
                        Text("Easy • Quick • Accurate").font(.caption.weight(.medium)).foregroundStyle(Color.nufoSecondaryText)
                    }
                    .enterStagger(0)
                    hero.padding(.top, 20).enterStagger(1)
                    Button { model.tab = .search } label: {
                        NufoCard(corner: 18) {
                            HStack(spacing: 12) {
                                Image(systemName: "magnifyingglass")
                                Text("Search foods, brands, meals…")
                                Spacer()
                            }
                            .foregroundStyle(Color.nufoSecondaryText).padding(.horizontal, 18).padding(.vertical, 16)
                        }
                    }
                    .buttonStyle(PressableStyle()).padding(.top, 16).enterStagger(2)
                    popular.padding(.top, 24).enterStagger(3)
                    Text("Recent scans").font(.title3.bold()).padding(.top, 24).enterStagger(4)
                    recent.padding(.top, 12)
                    trustCard.padding(.top, 24).enterStagger(6)
                }
                .padding(20)
            }
            .background(Color.nufoBackground)
            .navigationDestination(for: ResultRequest.self) { ResultView(request: $0).zoomDestination($0.zoomID, in: zoom) }
            .fullScreenCover(isPresented: $showScanner) {
                ScannerView { code in
                    showScanner = false
                    path.append(ResultRequest.barcode(code))
                }
            }
            .sheet(item: $photoSource) { source in
                PhotoView(source: source,
                          onBarcode: { code in photoSource = nil; path.append(ResultRequest.barcode(code)) },
                          onConfirm: { q in photoSource = nil; model.search(for: q) })
            }
        }
    }

    /// Common Greek foods, one tap away; the search shows products sold in Greece first.
    private var popular: some View {
        let items = [("Φέτα", "Feta"), ("Γιαούρτι", "Greek yogurt"), ("Ελαιόλαδο", "Olive oil"), ("Σπανακόπιτα", "Spinach pie"),
                     ("Χαλβάς", "Halva"), ("Μέλι", "Honey"), ("Φρυγανιές", "Rusks"), ("Ταχίνι", "Tahini")]
        return VStack(alignment: .leading, spacing: 10) {
            Text("Popular in Greece").font(.title3.bold())
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(items, id: \.0) { el, en in
                        let label = dataLang == "el" ? el : en
                        Button { model.search(for: label) } label: {
                            Text(label).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                                .padding(.horizontal, 16).padding(.vertical, 10)
                                .background(Color.nufoCard, in: Capsule())
                                .overlay(Capsule().stroke(Color.secondary.opacity(0.2)))
                        }
                        .buttonStyle(PressableStyle())
                    }
                }
            }
        }
    }

    private var trustCard: some View {
        Button { showAbout = true } label: {
            NufoCard {
                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: "checkmark.shield").foregroundStyle(Color.nufoGreen)
                        .frame(width: 40, height: 40).background(Color.nufoGreen.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Data you can check").font(.headline).foregroundStyle(.primary)
                        Text("Every result shows its source and date. No account. Nothing leaves your phone except what you search for.")
                            .font(.subheadline).foregroundStyle(Color.nufoSecondaryText).multilineTextAlignment(.leading)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right").foregroundStyle(Color.nufoSecondaryText)
                }
                .padding(18)
            }
        }
        .buttonStyle(PressableStyle())
        .sheet(isPresented: $showAbout) { AboutSheet() }
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("What are you eating?").font(.title2.bold())
                    Text("Scan a barcode or photograph your plate.").font(.subheadline).opacity(0.78)
                }
                Spacer(minLength: 12)
                Image(systemName: "barcode.viewfinder").font(.system(size: 54, weight: .light)).opacity(0.9).accessibilityHidden(true)
            }
            .foregroundStyle(.white)
            Button { showScanner = true } label: {
                Label("Scan barcode", systemImage: "barcode.viewfinder").font(.headline)
                    .frame(maxWidth: .infinity).frame(height: 54)
                    .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .foregroundStyle(Color.nufoGreenDeep)
            }
            .buttonStyle(PressableStyle()).padding(.top, 20)
            HStack(spacing: 10) {
                ghost("camera", "Take photo") { photoSource = .camera }
                ghost("photo.on.rectangle", "Gallery") { photoSource = .gallery }
            }
            .padding(.top, 10)
        }
        .padding(22)
        .background(LinearGradient(colors: [Color(hex: 0x3B8F3F), .nufoGreenDeep], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: Color.nufoGreenDeep.opacity(0.25), radius: 16, y: 8)
    }

    private func ghost(_ icon: String, _ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                .frame(maxWidth: .infinity).frame(height: 48)
                .background(.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(.white.opacity(0.22)))
        }
        .buttonStyle(PressableStyle())
    }

    @ViewBuilder private var recent: some View {
        let products = history.prefix(12).compactMap(\.product)
        if products.isEmpty {
            NufoCard {
                HStack(spacing: 14) {
                    Image(systemName: "fork.knife").foregroundStyle(Color.nufoGreen)
                        .frame(width: 44, height: 44).background(Color.nufoGreen.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Nothing scanned yet").font(.headline)
                        Text("Your scans appear here and stay on this device.").font(.subheadline).foregroundStyle(Color.nufoSecondaryText)
                    }
                    Spacer(minLength: 0)
                }
                .padding(18)
            }
            .enterStagger(4)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(Array(products.enumerated()), id: \.element.key) { i, p in
                        NavigationLink(value: ResultRequest.product(p)) { RecentCard(product: p).zoomSource(p.key, in: zoom) }
                            .buttonStyle(PressableStyle()).enterStagger(4 + i)
                    }
                }
                .padding(.vertical, 12)
            }
            .padding(.horizontal, -20).contentMargins(.horizontal, 20, for: .scrollContent)
        }
    }
}

private struct RecentCard: View {
    let product: Product
    var body: some View {
        NufoCard {
            VStack(alignment: .leading, spacing: 0) {
                ProductThumb(url: product.imageUrl, name: product.name).frame(height: 104)
                    .overlay(alignment: .topTrailing) {
                        Text("\(product.nufoScore)").font(.caption.bold()).foregroundStyle(.white)
                            .frame(width: 30, height: 30).background(Grade.score(product.nufoScore), in: Circle()).padding(8)
                    }
                VStack(alignment: .leading, spacing: 4) {
                    Text(product.displayName).font(.subheadline.weight(.semibold)).lineLimit(2, reservesSpace: true).foregroundStyle(.primary)
                    Text(product.nutritionPer100g.calories.map { "\(Int($0)) kcal / 100 g" } ?? String(localized: "kcal not available"))
                        .font(.caption).foregroundStyle(Color.nufoSecondaryText)
                }
                .padding(12)
            }
            .frame(width: 150)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("\(product.displayName), Nufo Score \(product.nufoScore)"))
    }
}