import Foundation
import UIKit

/// Isolated CDN behavior: the thumbnail is missing, but the saved original exists.
private final class MissingThumbnailProtocol: URLProtocol {
    static var payload = Data()
    private static let lock = NSLock()
    private static var paths: [String] = []
    static var requestedPaths: [String] { lock.lock(); defer { lock.unlock() }; return paths }
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "upload.wikimedia.org" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let url = request.url!
        Self.lock.lock(); Self.paths.append(url.path); Self.lock.unlock()
        let missing = url.path.contains("/thumb/")
        let response = HTTPURLResponse(url: url, statusCode: missing ? 404 : 200, httpVersion: nil,
            headerFields: ["Content-Type": "image/png", "Cache-Control": "no-store"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: missing ? Data() : Self.payload)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

@main
struct ImageLoaderProbe {
    static func main() async throws {
        let original = URL(string: "https://aiknow.kro.kr/media/generated/" + String(repeating: "a", count: 64) + ".png")!
        let advertised = URL(string: original.absoluteString + "?preview=1280")!
        precondition(ImageLoader.requestURL(original, maxPixelSize: 320) == original, "Old deployments must not incur a second request")
        precondition(ImageLoader.requestURL(advertised, maxPixelSize: 320).query == "preview=320")
        precondition(ImageLoader.requestURL(advertised, maxPixelSize: 1280) == advertised)
        precondition(ImageLoader.requestURL(advertised, maxPixelSize: 4096) == original, "Zoom must use the original")
        let commons = URL(string: "https://upload.wikimedia.org/wikipedia/commons/a/ab/Example%20photo.jpg")!
        let thumb = ImageLoader.requestURL(commons, maxPixelSize: 500)
        precondition(thumb.absoluteString == "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Example%20photo.jpg/500px-Example%20photo.jpg")
        precondition(ImageLoader.requestURL(commons, maxPixelSize: 4096) == commons)
        for raw in ["https://other.example/wikipedia/commons/a/ab/image.jpg", commons.absoluteString + "?signature=keep", "https://upload.wikimedia.org/wikipedia/commons/a/ab/drawing.svg"] {
            let url = URL(string: raw)!; precondition(ImageLoader.requestURL(url, maxPixelSize: 500) == url)
        }
        let base = URL(string: "http://127.0.0.1:18090")!
        let loader = ImageLoader()
        let run = UUID().uuidString
        func endpoint(_ path: String) -> URL {
            URL(string: base.appendingPathComponent(path).absoluteString + "?run=" + run)!
        }
        let sizes = try await withThrowingTaskGroup(of: Int.self) { group in
            for index in 0..<20 {
                group.addTask {
                    let limit = index.isMultiple(of: 2) ? 320 : 960
                    let image = try await loader.image(at: endpoint("image"), maxPixelSize: limit)
                    let size = max(image.cgImage!.width, image.cgImage!.height)
                    precondition(size == limit, "Must decode only the requested pixel size")
                    return size
                }
            }
            var results: [Int] = []
            for try await size in group { results.append(size) }
            return results
        }
        precondition(sizes.count == 20)
        _ = try await loader.image(at: endpoint("image"), maxPixelSize: 320)
        // A different decoded size must reuse the cacheable original HTTP response.
        _ = try await loader.image(at: endpoint("image"), maxPixelSize: 640)

        let full = try await loader.image(at: endpoint("image"), maxPixelSize: 4_096)
        precondition(full.cgImage!.width == 1800 && full.cgImage!.height == 1200, "The zoom viewer must retain original detail")

        do {
            _ = try await loader.image(at: endpoint("flaky"), maxPixelSize: 320)
            fatalError("A non-2xx response must fail")
        } catch { }
        _ = try await loader.image(at: endpoint("flaky"), maxPixelSize: 320)
        do {
            _ = try await loader.image(at: endpoint("invalid"), maxPixelSize: 320)
            fatalError("An invalid image must fail decoding")
        } catch { }
        for path in ["cached-error", "cached-html"] {
            do {
                _ = try await loader.image(at: endpoint(path), maxPixelSize: 320)
                fatalError("A cached non-image must fail")
            } catch { }
            _ = try await loader.image(at: endpoint(path), maxPixelSize: 320)
        }
        MissingThumbnailProtocol.payload = try await URLSession.shared.data(from: endpoint("preview-source")).0
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MissingThumbnailProtocol.self]
        let previewLoader = ImageLoader(session: URLSession(configuration: config))
        let fallback = try await previewLoader.image(at: commons, maxPixelSize: 320)
        precondition(fallback.cgImage!.width == 320)
        _ = try await previewLoader.image(at: commons, maxPixelSize: 319)
        precondition(MissingThumbnailProtocol.requestedPaths.filter { $0.contains("/thumb/") }.count == 1,
                     "A missing preview must fall back to the original and be remembered")
        let (data, _) = try await URLSession.shared.data(from: base.appendingPathComponent("counts"))
        let counts = try JSONDecoder().decode([String: Int].self, from: data)
        precondition(counts["/image"] == 1, "Concurrent sizes and revisits must share one download: \(counts)")
        precondition(counts["/flaky"] == 2, "Failed images must be retryable")
        precondition(counts["/invalid"] == 1)
        precondition(counts["/cached-error"] == 2 && counts["/cached-html"] == 2, "Retries must evict cached error responses")
        print("PASS: 20 concurrent loads, downsampling, decoded/HTTP cache reuse, HTTP error retry, invalid image, cached error eviction, Commons thumbnails")
    }
}
