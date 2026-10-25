import Foundation
import Security
#if DEBUG
import OSLog
#endif

@MainActor
final class APIClient {
    static let shared = APIClient()
    private let session: URLSession
    private let baseURL: URL?
    private let persistCookies: Bool
    private var csrf: CSRF?

    init(baseURL: URL? = nil, session: URLSession = .shared, persistCookies: Bool = true) {
        self.session = session
        self.persistCookies = persistCookies
        self.baseURL = baseURL ?? (Bundle.main.object(forInfoDictionaryKey: "AIKnowAPIBaseURL") as? String).flatMap(URL.init(string:))
        if persistCookies { restoreCookie() }
    }

    enum Provider: String { case google = "GOOGLE", apple = "APPLE", kakao = "KAKAO" }

    struct CSRF: Decodable { let headerName: String; let token: String }
    struct LoginResult: Decodable { let userId: String; let csrfHeaderName: String; let csrfToken: String }
    struct Profile: Codable, Identifiable, Hashable { let id: Int64; let name: String; let imgUrl: String }
    struct User: Decodable { let nickname: String; let onboardingCompleted: Bool; let userId: String?; let userRole: String?; let profile: Profile? }
    struct Challenge: Decodable { let nonce: String }
    private struct ServerError: Decodable { let status: Int; let code: String; let message: String }

    func availableProviders() async throws -> Set<Provider> {
        let names: [String] = try await decode("api/v1/auth/social/providers")
        return Set(names.compactMap(Provider.init(rawValue:)))
    }

    func refreshCSRF() async throws {
        csrf = try JSONDecoder().decode(CSRF.self, from: await send(path: "api/v1/auth/csrf", method: "GET"))
    }

    func challenge(provider: Provider = .google) async throws -> Challenge {
        // Always obtain fresh anonymous CSRF before starting a new, single-use challenge.
        try await refreshCSRF()
        return try JSONDecoder().decode(Challenge.self, from: await request(path: "api/v1/auth/social/\(provider.rawValue)/challenge", method: "POST"))
    }

    func login(provider: Provider = .google, idToken: String, nickname: String?) async throws {
        struct Body: Encodable { let idToken: String; let nickname: String? }
        let data = try await request(path: "api/v1/auth/social/\(provider.rawValue)", method: "POST", body: JSONEncoder().encode(Body(idToken: idToken, nickname: nickname)))
        let result = try JSONDecoder().decode(LoginResult.self, from: data)
        // Spring rotates both the session cookie and CSRF on successful login.
        csrf = CSRF(headerName: result.csrfHeaderName, token: result.csrfToken)
    }

    func currentUser() async throws -> User {
        try await decode("api/v1/users")
    }

    func logout() async throws {
        do { _ = try await request(path: "api/v1/auth/logout", method: "POST") }
        catch APIError.http(401) { }
        clearSession()
    }

    func request(path: String, method: String = "GET", body: Data? = nil, query: [URLQueryItem] = [], contentType: String = "application/json") async throws -> Data {
        let mutation = !["GET", "HEAD"].contains(method)
        if mutation && csrf == nil { try await refreshCSRF() }
        do { return try await send(path: path, method: method, body: body, csrf: mutation ? csrf : nil, query: query, contentType: contentType) }
        catch APIError.http(403) where mutation {
            // A CSRF rejection happens before the controller consumes the challenge.
            try await refreshCSRF()
            return try await send(path: path, method: method, body: body, csrf: csrf, query: query, contentType: contentType)
        }
    }

    private func send(path: String, method: String, body: Data? = nil, csrf: CSRF? = nil, query: [URLQueryItem] = [], contentType: String = "application/json") async throws -> Data {
        guard let baseURL, ["http", "https"].contains(baseURL.scheme ?? ""), baseURL.host != nil else { throw APIError.configuration }
        guard var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false) else { throw APIError.configuration }
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw APIError.configuration }
        // Account permissions and card-open counts must reach the server on each request.
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData)
        request.httpMethod = method
        request.httpBody = body
        request.timeoutInterval = 20
        if body != nil { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        if let csrf { request.setValue(csrf.token, forHTTPHeaderField: csrf.headerName) }
        let data: Data
        let response: URLResponse
        do { (data, response) = try await session.data(for: request) }
        catch let error as URLError {
            switch error.code {
            case .notConnectedToInternet, .cannotFindHost, .cannotConnectToHost, .networkConnectionLost, .timedOut, .dnsLookupFailed:
                throw APIError.network
            case .secureConnectionFailed, .serverCertificateUntrusted, .serverCertificateHasBadDate, .serverCertificateHasUnknownRoot, .serverCertificateNotYetValid:
                throw APIError.secureConnection
            default: throw error
            }
        }
        guard let response = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        #if DEBUG
        // Never log request bodies, query strings, cookies, CSRF or identity tokens.
        Logger(subsystem: "org.aiknow.AiKnow", category: "API").notice("API \(method, privacy: .public) \(request.url?.path ?? "", privacy: .public) -> \(response.statusCode)")
        #endif
        if persistCookies { saveCookie() }
        if response.statusCode == 401 && persistCookies {
            NotificationCenter.default.post(name: Notification.Name("AIKnowSessionExpired"), object: nil)
        }
        guard (200..<300).contains(response.statusCode) else {
            let status = response.statusCode
            if status != 401 && status != 404 && status < 500,
               let error = try? JSONDecoder().decode(ServerError.self, from: data), error.status == status,
               !error.message.isEmpty, error.message.count <= 400,
               !(status == 403 && error.code == "FORBIDDEN") {
                // Preserve Spring's validation errors; authorization failures must not be retried as CSRF.
                throw APIError.server(status: status, code: error.code, message: error.message)
            }
            throw APIError.http(status)
        }
        return data
    }

    func decode<T: Decodable>(_ path: String, method: String = "GET", body: Data? = nil, query: [URLQueryItem] = []) async throws -> T {
        let data = try await request(path: path, method: method, body: body, query: query)
        do { return try JSONDecoder().decode(T.self, from: data) }
        catch { throw APIError.invalidResponse }
    }

    func mediaURL(_ raw: String?) -> URL? {
        guard let raw, !raw.isEmpty, let baseURL, let url = URL(string: raw, relativeTo: baseURL)?.absoluteURL,
              ["http", "https"].contains(url.scheme ?? "") else { return nil }
        if ["localhost", "127.0.0.1", "::1"].contains(url.host ?? ""),
           var parts = URLComponents(url: url, resolvingAgainstBaseURL: false) {
            parts.scheme = baseURL.scheme; parts.host = baseURL.host; parts.port = baseURL.port
            return parts.url
        }
        return url
    }

    func clearSession() {
        csrf = nil
        if let baseURL {
            for cookie in session.configuration.httpCookieStorage?.cookies(for: baseURL) ?? [] where cookie.name == "JSESSIONID" {
                session.configuration.httpCookieStorage?.deleteCookie(cookie)
            }
        }
        if persistCookies { SecItemDelete(keychainQuery as CFDictionary) }
    }

    private var keychainQuery: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "org.aiknow.AiKnow.server-session",
         kSecAttrAccount as String: baseURL?.absoluteString ?? "unconfigured"]
    }

    private func saveCookie() {
        guard let baseURL, let cookie = session.configuration.httpCookieStorage?.cookies(for: baseURL)?.first(where: { $0.name == "JSESSIONID" }), let properties = cookie.properties else { return }
        let strings = Dictionary(uniqueKeysWithValues: properties.map { ($0.key.rawValue, $0.value) })
        guard let data = try? PropertyListSerialization.data(fromPropertyList: strings, format: .binary, options: 0) else { return }
        let query = keychainQuery
        let update = [kSecValueData as String: data]
        if SecItemUpdate(query as CFDictionary, update as CFDictionary) == errSecItemNotFound {
            var item = query
            item[kSecValueData as String] = data
            item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(item as CFDictionary, nil)
        }
    }

    private func restoreCookie() {
        var query = keychainQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              let raw = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any] else { return }
        let properties = Dictionary(uniqueKeysWithValues: raw.map { (HTTPCookiePropertyKey($0.key), $0.value) })
        if let cookie = HTTPCookie(properties: properties) { session.configuration.httpCookieStorage?.setCookie(cookie) }
    }
}

enum APIError: Error, LocalizedError {
    case configuration, invalidResponse, network, secureConnection, http(Int)
    case server(status: Int, code: String, message: String)
    var errorDescription: String? {
        switch self {
        case .configuration: return "서버 주소 설정을 확인해 주세요."
        case .network: return "서버에 연결할 수 없습니다. 네트워크와 서버 실행 상태를 확인한 뒤 다시 시도해 주세요."
        case .secureConnection: return "서버에 안전하게 연결할 수 없습니다. HTTPS 설정을 확인해 주세요."
        case .server(_, _, let message): return message
        case .http(401): return "로그인이 만료되었습니다. 다시 로그인해 주세요."
        case .http(404): return "요청한 콘텐츠를 찾을 수 없습니다."
        case .http(503): return "서비스를 준비 중입니다. 잠시 후 다시 시도해 주세요."
        case .http(let code): return "서버 요청에 실패했습니다 (\(code)). 다시 시도해 주세요."
        case .invalidResponse: return "서버 응답을 확인하지 못했습니다."
        }
    }
}
