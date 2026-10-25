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
    private var unavailablePreviews: [URL: Date] = [:]
    private var failedURLs: Set<URL> = []
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
            config.httpCookieStorage = nil
            config.urlCredentialStorage = nil
            config.httpShouldSetCookies = false
            config.httpAdditionalHeaders = ["User-Agent": "AiKnow/1.0 (+https://aiknow.kro.kr)",
                                            "Accept": "image/png,image/jpeg,image/webp,image/*;q=0.8"]
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
            let preview = Self.requestURL(url, maxPixelSize: pixels)
            // Prefer an already cached original over downloading a second representation.
            let cachedOriginal = session.configuration.urlCache?.cachedResponse(for: URLRequest(url: url))
            let originalIsCached = (cachedOriginal?.response as? HTTPURLResponse).map { (200..<300).contains($0.statusCode) } ?? false
            let previewUnavailable = unavailablePreviews[preview].map { Date().timeIntervalSince($0) < 300 } ?? false
            let requested = originalIsCached || previewUnavailable ? url : preview
            do { return try await decoded(at: requested, pixels: pixels) }
            catch {
                // A source may not offer this thumbnail (e.g. an original smaller than the
                // requested width). Fall back to the saved original without changing its URL.
                guard requested != url, let failure = error as? ImageLoadError, failure.allowsOriginalFallback else { throw error }
                unavailablePreviews[preview] = Date()
                return try await decoded(at: url, pixels: pixels)
            }
        }
        decodes[key] = task
        defer { decodes[key] = nil }
        let result = try await task.value
        let cost = result.cgImage.map { $0.bytesPerRow * $0.height } ?? pixels * pixels * 4
        images.setObject(result, forKey: key as NSString, cost: cost)
        return result
    }

    private func decoded(at url: URL, pixels: Int) async throws -> UIImage {
        let data = try await data(at: url)
        do {
            return try await Task.detached(priority: .userInitiated) {
                guard let source = CGImageSourceCreateWithData(data as CFData,
                    [kCGImageSourceShouldCache: false] as CFDictionary),
                    let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                        kCGImageSourceCreateThumbnailFromImageAlways: true,
                        kCGImageSourceCreateThumbnailWithTransform: true,
                        kCGImageSourceThumbnailMaxPixelSize: pixels,
                        kCGImageSourceShouldCacheImmediately: true
                    ] as CFDictionary) else { throw ImageLoadError.invalidImage }
                return UIImage(cgImage: thumbnail)
            }.value
        } catch {
            session.configuration.urlCache?.removeCachedResponse(for: URLRequest(url: url))
            failedURLs.insert(url)
            throw error
        }
    }

    nonisolated static func requestURL(_ url: URL, maxPixelSize: Int) -> URL {
        // Commons raster originals have documented CDN thumbnail URLs. Use only its
        // standard widths; arbitrary sizes can be rejected. Never rewrite signed URLs,
        // other hosts, SVG/page links or the original used for zoom.
        if maxPixelSize < 4_096, url.scheme == "https", url.host == "upload.wikimedia.org",
           url.query == nil, url.fragment == nil, url.user == nil,
           var parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
           parts.percentEncodedPath.range(of: "^/wikipedia/commons/[a-f0-9]/[a-f0-9]{2}/[^/]+\\.(png|jpg|jpeg|webp)$", options: [.regularExpression, .caseInsensitive]) != nil {
            let file = parts.percentEncodedPath.split(separator: "/").last!
            let width = maxPixelSize <= 330 ? 330 : (maxPixelSize <= 500 ? 500 : (maxPixelSize <= 960 ? 960 : 1280))
            parts.percentEncodedPath = parts.percentEncodedPath.replacingOccurrences(of: "/wikipedia/commons/", with: "/wikipedia/commons/thumb/") + "/\(width)px-\(file)"
            return parts.url ?? url
        }
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
        var request = URLRequest(url: url)
        if failedURLs.remove(url) != nil { request.cachePolicy = .reloadIgnoringLocalCacheData }
        let task = Task { [session] in
            let start = Date()
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
            guard (200..<300).contains(http.statusCode) else {
                // URLCache can store 404/503 responses when the origin supplies cache headers.
                // A retry must reach the server instead of replaying that cached error forever.
                session.configuration.urlCache?.removeCachedResponse(for: URLRequest(url: url))
                throw ImageLoadError.http(http.statusCode)
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
        do { return try await task.value }
        catch {
            // Cache writes may finish after removeCachedResponse. The next request
            // must explicitly bypass that entry as well.
            failedURLs.insert(url)
            throw error
        }
    }
}


enum ImageLoadError: Error, LocalizedError {
    case http(Int), invalidImage
    var allowsOriginalFallback: Bool {
        switch self {
        case .http(let code): return [400, 404, 410, 422].contains(code)
        case .invalidImage: return true
        }
    }
    var errorDescription: String? {
        switch self {
        case .http(403): return "이미지 제공처에서 접근을 허용하지 않습니다."
        case .http(404), .http(410): return "이미지 주소가 만료되었거나 파일이 없습니다."
        case .http(429): return "이미지 제공처의 요청이 많습니다. 잠시 후 다시 불러와 주세요."
        case .http: return "이미지 서버에 연결하지 못했습니다."
        case .invalidImage: return "이미지 파일이 아닌 주소이거나 지원하지 않는 형식입니다."
        }
    }
}
