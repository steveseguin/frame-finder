import Foundation
import Combine
import AVFoundation

final class CameraModel: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    @Published var isRunning = false
    @Published var isStarting = false
    @Published var status = "Tap Start camera. No microphone access is needed."
    @Published var item = "Nothing identified yet"
    @Published var candidates = ""
    @Published var visibleText = ""
    @Published var barcodes = ""
    @Published var timing = ""
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "FrameFinder.capture", qos: .userInitiated)
    private let recognizer = FrameRecognizer()
    private var configured = false
    private var active = false // Accessed only on queue.
    private var lastFrameTime: TimeInterval = 0
    private var requestID = 0 // Accessed only on main thread.

    func start() {
        guard !isRunning && !isStarting else { return }
        requestID += 1
        let request = requestID
        isStarting = true
        status = "Opening camera…"
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                guard request == self.requestID else { return }
                guard granted else {
                    self.isStarting = false
                    self.status = "Allow camera access in Settings to use this demo."
                    return
                }
                self.queue.async {
                    do {
                        try self.configure()
                        self.active = true
                        self.lastFrameTime = 0
                        self.session.startRunning()
                        DispatchQueue.main.async {
                            guard request == self.requestID else { return }
                            self.isStarting = false
                            self.isRunning = self.session.isRunning
                            self.status = self.isRunning ? "Recognizing locally · approximately once per second" : "Camera could not start. Try again."
                        }
                    } catch {
                        DispatchQueue.main.async {
                            guard request == self.requestID else { return }
                            self.isStarting = false
                            self.status = error.localizedDescription
                        }
                    }
                }
            }
        }
    }

    func stop() {
        requestID += 1
        isStarting = false
        isRunning = false
        status = "Camera stopped. Tap Start camera to resume."
        queue.async {
            self.active = false
            self.session.stopRunning()
        }
    }

    private func configure() throws {
        guard !configured else { return }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            throw NSError(domain: "FrameFinder", code: 1, userInfo: [NSLocalizedDescriptionKey: "No rear camera found. Run on a physical iPhone."])
        }
        let input = try AVCaptureDeviceInput(device: device)
        let output = AVCaptureVideoDataOutput()
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: queue)
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .hd1280x720
        guard session.canAddInput(input) else {
            throw NSError(domain: "FrameFinder", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cannot attach camera input."])
        }
        session.addInput(input)
        guard session.canAddOutput(output) else {
            session.removeInput(input)
            throw NSError(domain: "FrameFinder", code: 3, userInfo: [NSLocalizedDescriptionKey: "Cannot attach frame output."])
        }
        session.addOutput(output)
        // Request portrait buffers; Vision then receives upright pixels (.up).
        if let connection = output.connection(with: .video), connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
        configured = true
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = ProcessInfo.processInfo.systemUptime
        guard active, now - lastFrameTime >= 1, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastFrameTime = now
        do {
            let result = try recognizer.recognize(buffer, orientation: .up)
            DispatchQueue.main.async {
                guard self.isRunning else { return }
                self.item = result.item
                self.candidates = result.candidates
                self.visibleText = result.text
                self.barcodes = result.barcodes
                self.timing = "\(result.milliseconds) ms · Apple Vision · full frame"
                self.status = "Recognizing locally · approximately once per second"
            }
        } catch {
            DispatchQueue.main.async {
                guard self.isRunning else { return }
                self.item = "No result"
                self.candidates = ""
                self.visibleText = ""
                self.barcodes = ""
                self.status = "Recognition failed: \(error.localizedDescription)"
            }
        }
    }
}
