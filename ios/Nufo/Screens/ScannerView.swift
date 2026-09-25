import SwiftUI
import VisionKit
import AVFoundation

/// Barcodes that exist in Open Food Facts; the Simulator has no camera, so these stand in for real packaging.
let sampleBarcodes = [("3017620422003", "Nutella"), ("5449000000996", "Coca-Cola"), ("3175680011480", "Gerblé biscuits"),
                      ("8076809513388", "Barilla sauce"), ("5201054017388", "FAGE yogurt")]

func isValidBarcode(_ s: String) -> Bool { (8...14).contains(s.count) && s.allSatisfy(\.isNumber) }

struct ScannerView: View {
    let onBarcode: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var manual = false
    @State private var torch = false
    @State private var delivered = false

    private var liveScanning: Bool { DataScannerViewController.isSupported && DataScannerViewController.isAvailable }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if liveScanning {
                DataScanner(torch: torch) { deliver($0) }.ignoresSafeArea()
            } else {
                // Debug mock scanner: Simulator, or devices without a supported camera.
                VStack(spacing: 10) {
                    Image(systemName: "camera.metering.unknown").font(.largeTitle)
                    Text("Live scanning isn't available here.").font(.headline)
                    Text("Type a barcode or pick a sample below.").font(.subheadline).opacity(0.7)
                }
                .foregroundStyle(.white)
            }
            ScanOverlay().allowsHitTesting(false)
            VStack {
                HStack {
                    glass("xmark", "Close scanner") { dismiss() }
                    Spacer()
                    if liveScanning { glass(torch ? "flashlight.on.fill" : "flashlight.off.fill", torch ? "Turn flash off" : "Turn flash on") { torch.toggle() } }
                }
                .padding()
                Spacer()
                Text("Align the barcode inside the frame").font(.headline).foregroundStyle(.white)
                Button { manual = true } label: {
                    VStack(spacing: 6) {
                        Image(systemName: "keyboard").font(.title2).frame(width: 58, height: 58).background(.white.opacity(0.16), in: Circle())
                        Text("Type code").font(.caption)
                    }
                    .foregroundStyle(.white)
                }
                .padding(.top, 18).padding(.bottom, 28)
            }
        }
        .sensoryFeedback(.success, trigger: delivered)
        .sheet(isPresented: $manual) { ManualEntry { manual = false; deliver($0) }.presentationDetents([.medium]) }
    }

    private func deliver(_ code: String) {
        guard !delivered else { return }
        delivered = true
        onBarcode(code)
    }

    private func glass(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.headline).foregroundStyle(.white).frame(width: 48, height: 48).background(.white.opacity(0.16), in: Circle())
        }
        .accessibilityLabel(label)
    }
}

private struct ManualEntry: View {
    let onSubmit: (String) -> Void
    @State private var code = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Enter barcode").font(.title3.bold())
            TextField("8–14 digits", text: $code).keyboardType(.numberPad).textFieldStyle(.roundedBorder)
                .onChange(of: code) { _, v in code = String(v.filter(\.isNumber).prefix(14)) }
            Button("Look up") { onSubmit(code) }.buttonStyle(.borderedProminent).disabled(!isValidBarcode(code)).frame(maxWidth: .infinity)
            #if DEBUG || targetEnvironment(simulator)
            Text("Samples").font(.caption).foregroundStyle(.secondary).padding(.top, 8)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack { ForEach(sampleBarcodes, id: \.0) { bc, name in Button(name) { onSubmit(bc) }.buttonStyle(.bordered) } }
            }
            #endif
        }
        .padding(24)
    }
}

/// Dimmed surround, rounded window, corner brackets and a sweeping laser line.
private struct ScanOverlay: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sweep = false
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width * 0.78, h = w * 0.62
            let rect = CGRect(x: (geo.size.width - w) / 2, y: geo.size.height * 0.42 - h / 2, width: w, height: h)
            ZStack {
                Color.black.opacity(0.55)
                    .mask { Rectangle().overlay { RoundedRectangle(cornerRadius: 28).frame(width: w, height: h).position(x: rect.midX, y: rect.midY).blendMode(.destinationOut) }.compositingGroup() }
                RoundedRectangle(cornerRadius: 28).stroke(.white, style: StrokeStyle(lineWidth: 5, lineCap: .round, dash: [34, w - 34]))
                    .frame(width: w, height: h).position(x: rect.midX, y: rect.midY)
                LinearGradient(colors: [.clear, Color(hex: 0x69F0AE), .clear], startPoint: .leading, endPoint: .trailing)
                    .frame(width: w - 32, height: 3)
                    .position(x: rect.midX, y: reduceMotion ? rect.midY : (sweep ? rect.maxY - 18 : rect.minY + 18))
            }
            .onAppear { if !reduceMotion { withAnimation(.linear(duration: 1.8).repeatForever(autoreverses: true)) { sweep = true } } }
        }
        .ignoresSafeArea()
    }
}

/// VisionKit live barcode scanner limited to retail symbologies.
private struct DataScanner: UIViewControllerRepresentable {
    let torch: Bool
    let onCode: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let vc = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.ean13, .ean8, .upce])],
            qualityLevel: .balanced, recognizesMultipleItems: false, isHighFrameRateTrackingEnabled: false, isHighlightingEnabled: false
        )
        vc.delegate = context.coordinator
        try? vc.startScanning()
        return vc
    }

    func updateUIViewController(_ vc: DataScannerViewController, context: Context) {
        // DataScanner has no torch API; drive the device torch directly.
        guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else { return }
        try? device.lockForConfiguration()
        device.torchMode = torch ? .on : .off
        device.unlockForConfiguration()
    }

    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onCode: (String) -> Void
        init(onCode: @escaping (String) -> Void) { self.onCode = onCode }
        func dataScanner(_ scanner: DataScannerViewController, didAdd items: [RecognizedItem], allItems: [RecognizedItem]) {
            for case let .barcode(b) in items { if let v = b.payloadStringValue { onCode(v); return } }
        }
    }
}