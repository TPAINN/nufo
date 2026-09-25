import SwiftUI
import PhotosUI
import Vision

enum PhotoSource: String, Identifiable { case camera, gallery; var id: String { rawValue } }

/// What on-device analysis found in a still photo. Nothing leaves the phone here.
struct PhotoAnalysis { var barcode: String?; var labels: [(String, Float)]; var textLines: [String] }

/// Vision's classifier knows ~1300 identifiers; keep those that name a food, generic "food" last.
private let foodWords = ["fruit", "vegetable", "bread", "cake", "pizza", "cookie", "juice", "cheese", "pasta", "egg", "meat",
                         "soup", "sushi", "rice", "salad", "sandwich", "hotdog", "burger", "dessert", "coffee", "tea", "wine",
                         "beer", "drink", "candy", "chocolate", "ice_cream", "pie", "muffin", "donut", "pancake", "waffle",
                         "cereal", "seafood", "fish", "chicken", "noodle", "pastry", "cupcake", "apple", "banana", "orange",
                         "yogurt", "steak", "taco", "burrito", "dumpling", "fries", "food"]

/// Order-preserving de-duplication.
func unique(_ items: [String]) -> [String] {
    var seen = Set<String>()
    return items.filter { seen.insert($0).inserted }
}

enum PhotoAnalyzer {
    static func analyze(_ image: UIImage) async -> PhotoAnalysis {
        guard let cg = image.cgImage else { return PhotoAnalysis(barcode: nil, labels: [], textLines: []) }
        return await Task.detached(priority: .userInitiated) {
            let barcodes = VNDetectBarcodesRequest(); barcodes.symbologies = [.ean13, .ean8, .upce]
            let classify = VNClassifyImageRequest()
            let text = VNRecognizeTextRequest(); text.recognitionLevel = .accurate
            try? VNImageRequestHandler(cgImage: cg, orientation: CGImagePropertyOrientation(image.imageOrientation)).perform([barcodes, classify, text])
            let code = barcodes.results?.compactMap(\.payloadStringValue).first
            let labels = (classify.results ?? [])
                .filter { obs in obs.confidence > 0.3 && foodWords.contains { obs.identifier.contains($0) } }
                .sorted { ($0.identifier == "food" ? 0 : 1, $0.confidence) > ($1.identifier == "food" ? 0 : 1, $1.confidence) }
                .prefix(6).map { ($0.identifier.replacingOccurrences(of: "_", with: " ").capitalized, $0.confidence) }
            let lines = (text.results ?? [])
                .sorted { $0.boundingBox.height > $1.boundingBox.height } // biggest type first: usually the product name
                .compactMap { $0.topCandidates(1).first?.string.replacingOccurrences(of: #"[^\p{L} '&-]"#, with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces) }
                .filter { (3...40).contains($0.count) }
            return PhotoAnalysis(barcode: code, labels: Array(labels), textLines: Array(unique(lines).prefix(3)))
        }.value
    }
}

extension CGImagePropertyOrientation {
    init(_ o: UIImage.Orientation) {
        switch o {
        case .up: self = .up; case .down: self = .down; case .left: self = .left; case .right: self = .right
        case .upMirrored: self = .upMirrored; case .downMirrored: self = .downMirrored
        case .leftMirrored: self = .leftMirrored; case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}

struct PhotoView: View {
    let source: PhotoSource
    let onBarcode: (String) -> Void
    let onConfirm: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var image: UIImage?
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var showLibrary = false
    @State private var analysis: PhotoAnalysis?
    @State private var analyzing = false
    @State private var name = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    ZStack(alignment: .bottom) {
                        RoundedRectangle(cornerRadius: 26, style: .continuous).fill(Color.secondary.opacity(0.12))
                        if let image {
                            Image(uiImage: image).resizable().scaledToFill()
                                .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                                .accessibilityLabel("Selected photo")
                            Button { pick() } label: {
                                Label("Change", systemImage: "arrow.left.arrow.right").font(.subheadline.weight(.semibold))
                                    .padding(.horizontal, 16).padding(.vertical, 10).background(.black.opacity(0.55), in: Capsule()).foregroundStyle(.white)
                            }
                            .padding(14)
                        }
                    }
                    .aspectRatio(1, contentMode: .fit)
                    .clipped()

                    if analyzing {
                        HStack(spacing: 12) { ProgressView(); Text("Looking closely… (on this device)") }
                    } else if let a = analysis {
                        confirm(a).transition(.move(edge: .bottom).combined(with: .opacity))
                    } else {
                        Text("Take or choose a photo of a meal, a food, or its packaging.").foregroundStyle(Color.nufoSecondaryText)
                    }
                }
                .padding(20)
                .animation(.nufo, value: analyzing)
            }
            .background(Color.nufoBackground)
            .navigationTitle("Identify food").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
        }
        .photosPicker(isPresented: $showLibrary, selection: $pickerItem, matching: .images)
        .fullScreenCover(isPresented: $showCamera) { CameraPicker { image = $0 }.ignoresSafeArea() }
        .onAppear { if image == nil { pick() } }
        .onChange(of: pickerItem) { _, item in
            Task { if let data = try? await item?.loadTransferable(type: Data.self), let ui = UIImage(data: data) { image = ui } }
        }
        .task(id: image) {
            guard let image else { return }
            analyzing = true
            let a = await PhotoAnalyzer.analyze(image)
            analysis = a; name = (a.textLines + a.labels.map(\.0)).first ?? ""
            analyzing = false
            if let code = a.barcode { onBarcode(code) }
        }
    }

    private func pick() {
        if source == .camera && UIImagePickerController.isSourceTypeAvailable(.camera) { showCamera = true } else { showLibrary = true }
    }

    private func confirm(_ a: PhotoAnalysis) -> some View {
        let candidates = unique(a.textLines + a.labels.map(\.0))
        let top = a.labels.first
        let hint: String
        if let code = a.barcode { hint = String(localized: "Found barcode \(code) — opening…") }
        else if let top, top.1 >= 0.8 { hint = String(localized: "Looks like \(top.0.lowercased()) (\(Int(top.1 * 100))% sure). Confirm or refine it.") }
        else if candidates.isEmpty { hint = String(localized: "Couldn't recognise this. What is it?") }
        else { hint = String(localized: "Not fully sure. What is this?") }
        return NufoCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(hint).font(.headline)
                if !candidates.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack { ForEach(candidates, id: \.self) { c in
                            Button(c) { name = c }.buttonStyle(.bordered).tint(name == c ? .nufoGreen : .secondary)
                        } }
                    }
                }
                TextField("Food name", text: $name).textFieldStyle(.roundedBorder).submitLabel(.search)
                    .onSubmit { if !name.isEmpty { onConfirm(name) } }
                Button { onConfirm(name.trimmingCharacters(in: .whitespaces)) } label: {
                    Text("Find nutrition").font(.headline).frame(maxWidth: .infinity).frame(height: 52)
                }
                .buttonStyle(.borderedProminent).disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                Text("You can adjust the portion on the next screen.").font(.caption).foregroundStyle(Color.nufoSecondaryText)
            }
            .padding(18)
        }
    }
}

private struct CameraPicker: UIViewControllerRepresentable {
    let onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController(); p.sourceType = .camera; p.delegate = context.coordinator; return p
    }
    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ p: CameraPicker) { parent = p }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage { parent.onImage(img) }
            parent.dismiss()
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}