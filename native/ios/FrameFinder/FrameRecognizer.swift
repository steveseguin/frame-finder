import Foundation
import Vision
import CoreVideo
import ImageIO

struct FrameResult {
    let item: String
    let candidates: String
    let text: String
    let barcodes: String
    let milliseconds: Int
}

/// Call on a serial background queue with a decoded frame, including a WebRTC/HLS frame.
/// This accepts pixels independently of the camera and never uploads an image.
final class FrameRecognizer {
    func recognize(_ buffer: CVPixelBuffer, orientation: CGImagePropertyOrientation) throws -> FrameResult {
        let started = CFAbsoluteTimeGetCurrent()
        let classify = VNClassifyImageRequest()
        let text = VNRecognizeTextRequest()
        text.recognitionLevel = .fast
        text.usesLanguageCorrection = false
        let barcode = VNDetectBarcodesRequest()
        let handler = VNImageRequestHandler(cvPixelBuffer: buffer, orientation: orientation, options: [:])
        try handler.perform([classify, text, barcode])
        let labels = Array((classify.results ?? []).prefix(3))
        let top = labels.first
        return FrameResult(
            item: (top?.confidence ?? 0) >= 0.35 ? top!.identifier : "No clear match",
            candidates: labels.map { "\($0.identifier) · \(Int($0.confidence * 100))%" }.joined(separator: "\n"),
            text: (text.results ?? []).compactMap { $0.topCandidates(1).first?.string }.prefix(12).joined(separator: "\n"),
            barcodes: (barcode.results ?? []).compactMap { $0.payloadStringValue }.joined(separator: "\n"),
            milliseconds: Int((CFAbsoluteTimeGetCurrent() - started) * 1000)
        )
    }
}
