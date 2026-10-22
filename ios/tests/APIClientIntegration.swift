import Foundation

@main
struct APIClientIntegration {
    @MainActor
    static func main() async throws {
        guard let raw = ProcessInfo.processInfo.environment["API_CLIENT_TEST_URL"], let base = URL(string: raw) else { fatalError("Test server URL required") }
        guard let provider = APIClient.Provider(rawValue: ProcessInfo.processInfo.environment["API_CLIENT_TEST_PROVIDER"] ?? "GOOGLE") else { fatalError("Invalid test provider") }
        let session = URLSession(configuration: .ephemeral)
        let client = APIClient(baseURL: base, session: session, persistCookies: false)
        let providers = try await client.availableProviders()
        precondition(providers == Set([.google, .apple]))
        let challenge = try await client.challenge(provider: provider)
        precondition(challenge.nonce == "server-generated-nonce")
        try await client.login(provider: provider, idToken: "synthetic-test-token", nickname: "테스트")
        let user = try await client.currentUser()
        precondition(user.nickname == "테스트" && user.userRole == "ADMIN")
        let body = try JSONSerialization.data(withJSONObject: ["token": "synthetic-fcm-token", "platform": "IOS", "installationId": UUID().uuidString])
        // Test server rejects the first mutation with 403, requiring CSRF refresh and one retry.
        _ = try await client.request(path: "api/v1/device-tokens", method: "PUT", body: body)
        do {
            _ = try await client.request(path: "api/v1/users/nickname", method: "PATCH", body: Data("{\"nickname\":\"\"}".utf8))
            fatalError("Invalid nickname was accepted")
        } catch APIError.server(let status, let code, let message) {
            precondition(status == 400 && code == "NICKNAME_REQUIRED" && message == "닉네임은 필수입니다.")
        }
        do {
            _ = try await client.request(path: "api/v1/users/onboarding", method: "PATCH")
            fatalError("Business-rule rejection was ignored")
        } catch APIError.server(let status, let code, _) {
            precondition(status == 403 && code == "INCOMPLETE_ONBOARDING")
        }
        let multipart = Data("--fixture-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"recovery.png\"\r\nContent-Type: image/png\r\n\r\nfixture-bytes\r\n--fixture-boundary--\r\n".utf8)
        _ = try await client.request(path: "api/v1/admin/generations/1/recover-image", method: "POST", body: multipart, contentType: "multipart/form-data; boundary=fixture-boundary")
        try await client.logout()
        precondition(session.configuration.httpCookieStorage?.cookies(for: base)?.contains(where: { $0.name == "JSESSIONID" }) != true)
        do {
            _ = try await client.currentUser()
            fatalError("Logout retained authentication")
        } catch APIError.http(401) { }
        print("PASS \(provider.rawValue): admin role, multipart session/CSRF, enabled providers, server validation errors, business-rule 403, nonce, cookie/CSRF rotation, CSRF retry, logout")
    }
}
