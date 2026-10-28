import SwiftUI
import AVFoundation

struct ContentView: View {
    @StateObject private var camera = CameraModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("FRAME FINDER").font(.caption).tracking(3).foregroundColor(.green)
                Text("Point. Hold. Identify.").font(.largeTitle).bold()
                CameraPreview(session: camera.session)
                    .frame(height: 350).background(Color.black).clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(alignment: .bottom) {
                        Text("Hold one item close to the camera")
                            .font(.caption).padding(8).background(.black.opacity(0.7)).cornerRadius(6).padding()
                    }
                Button(camera.isRunning ? "Stop camera" : "Start camera") {
                    if camera.isRunning { camera.stop() } else { camera.start() }
                }.buttonStyle(.borderedProminent).tint(.green).disabled(camera.isStarting)
                Text(camera.status).font(.callout).foregroundColor(.secondary)
                Text(camera.item).font(.title).bold()
                Text(camera.candidates).font(.callout)
                if !camera.visibleText.isEmpty {
                    Text("VISIBLE TEXT").font(.caption).foregroundColor(.secondary)
                    Text(camera.visibleText).textSelection(.enabled)
                }
                if !camera.barcodes.isEmpty {
                    Text("BARCODES").font(.caption).foregroundColor(.secondary)
                    Text(camera.barcodes).textSelection(.enabled)
                }
                Text(camera.timing).font(.caption).foregroundColor(.secondary)
                Text("Apple Vision runs on this device. Categories and visible text are clues, not verified product identities.")
                    .font(.footnote).foregroundColor(.secondary)
            }.padding(24)
        }.preferredColorScheme(.dark)
            .onChange(of: scenePhase) { phase in if phase == .background { camera.stop() } }
    }
}

private final class PreviewView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    override func layoutSubviews() {
        super.layoutSubviews()
        if let connection = previewLayer.connection, connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
    }
}

private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspect
        return view
    }
    func updateUIView(_ view: PreviewView, context: Context) {}
}
