import Foundation
import UIKit

@main
struct ImageLoaderProbe {
    static func main() async throws {
        let original = URL(string: "https://aiknow.kro.kr/media/generated/" + String(repeating: "a", count: 64) + ".png")!
        let advertised = URL(string: original.absoluteString + "?preview=1280")!
        precondition(ImageLoader.requestURL(original, maxPixelSize: 320) == original, "Old deployments must not incur a second request")
        precondition(ImageLoader.requestURL(advertised, maxPixelSize: 320).query == "preview=320")
        precondition(ImageLoader.requestURL(advertised, maxPixelSize: 1280) == advertised)
        precondition(ImageLoader.requestURL(advertised, maxPixelSize: 4096) == original, "Zoom must use the original")
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
        let (data, _) = try await URLSession.shared.data(from: base.appendingPathComponent("counts"))
        let counts = try JSONDecoder().decode([String: Int].self, from: data)
        precondition(counts["/image"] == 1, "Concurrent sizes and revisits must share one download: \(counts)")
        precondition(counts["/flaky"] == 2, "Failed images must be retryable")
        precondition(counts["/invalid"] == 1)
        print("PASS: 20 concurrent loads, downsampling, decoded/HTTP cache reuse, HTTP error retry, invalid image")
    }
}
