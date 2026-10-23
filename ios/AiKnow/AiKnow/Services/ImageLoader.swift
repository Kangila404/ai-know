import Foundation
import UIKit
import ImageIO
#if DEBUG
import OSLog
#endif

/// Separate from API requests: public images may use HTTP caching; account APIs must stay fresh.
actor ImageLoader {
    static let shared = ImageLoader()
    private let session: URLSession
    private let images = NSCache<NSString, UIImage>()
    private var downloads: [URL: Task<Data, Error>] = [:]
    private var decodes: [String: Task<UIImage, Error>] = [:]

    init(session: URLSession? = nil) {
        if let session { self.session = session }
        else {
            let config = URLSessionConfiguration.default
            // Generated PNGs can exceed URLCache.shared's per-response disk limit.
            config.urlCache = URLCache(memoryCapacity: 32 * 1_024 * 1_024,
                                       diskCapacity: 256 * 1_024 * 1_024, diskPath: "card-images")
            config.requestCachePolicy = .useProtocolCachePolicy
            config.timeoutIntervalForRequest = 30
            config.timeoutIntervalForResource = 60
            config.httpMaximumConnectionsPerHost = 4
            self.session = URLSession(configuration: config)
        }
        images.totalCostLimit = 48 * 1_024 * 1_024
    }

    func image(at url: URL, maxPixelSize: Int) async throws -> UIImage {
        let pixels = max(64, min(maxPixelSize, 4_096))
        let key = "\(pixels):\(url.absoluteString)"
        if let cached = images.object(forKey: key as NSString) { return cached }
        if let pending = decodes[key] { return try await pending.value }
        let task = Task { [self] in
            let data = try await data(at: Self.requestURL(url, maxPixelSize: pixels))
            return try await Task.detached(priority: .userInitiated) {
                guard let source = CGImageSourceCreateWithData(data as CFData,
                    [kCGImageSourceShouldCache: false] as CFDictionary),
                    let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                        kCGImageSourceCreateThumbnailFromImageAlways: true,
                        kCGImageSourceCreateThumbnailWithTransform: true,
                        kCGImageSourceThumbnailMaxPixelSize: pixels,
                        kCGImageSourceShouldCacheImmediately: true
                    ] as CFDictionary) else { throw URLError(.cannotDecodeContentData) }
                return UIImage(cgImage: thumbnail)
            }.value
        }
        decodes[key] = task
        defer { decodes[key] = nil }
        let result = try await task.value
        let cost = result.cgImage.map { $0.bytesPerRow * $0.height } ?? pixels * pixels * 4
        images.setObject(result, forKey: key as NSString, cost: cost)
        return result
    }

    nonisolated static func requestURL(_ url: URL, maxPixelSize: Int) -> URL {
        // Only opt in when the server advertises a preview URL in its API response.
        // Older deployments keep using their original URLs with no extra request.
        guard url.path.range(of: "^/media/generated/[a-f0-9]{64}\\.png$", options: .regularExpression) != nil,
              var parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
              parts.queryItems?.contains(where: { $0.name == "preview" && ["320", "1280"].contains($0.value ?? "") }) == true else { return url }
        var query = (parts.queryItems ?? []).filter { $0.name != "preview" }
        if maxPixelSize < 4_096 { query.append(URLQueryItem(name: "preview", value: maxPixelSize <= 320 ? "320" : "1280")) }
        parts.queryItems = query.isEmpty ? nil : query
        return parts.url ?? url
    }

    private func data(at url: URL) async throws -> Data {
        if let pending = downloads[url] { return try await pending.value }
        let task = Task { [session] in
            let start = Date()
            let (data, response) = try await session.data(from: url)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw URLError(.badServerResponse)
            }
            #if DEBUG
            // Public image path only; never log query strings, cookies or response bodies.
            Logger(subsystem: "org.aiknow.AiKnow", category: "Images")
                .notice("Image \(url.host ?? "", privacy: .public)\(url.path, privacy: .public): \(data.count) bytes in \(Date().timeIntervalSince(start))s")
            #endif
            return data
        }
        downloads[url] = task
        defer { downloads[url] = nil }
        return try await task.value
    }
}
