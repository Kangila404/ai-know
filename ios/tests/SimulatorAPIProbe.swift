import Foundation

@main
struct SimulatorAPIProbe {
    @MainActor
    static func main() async throws {
        let client = APIClient(baseURL: URL(string: "http://localhost:8080")!, session: URLSession(configuration: .ephemeral), persistCookies: false)
        let health = try await client.request(path: "health-check")
        precondition(String(data: health, encoding: .utf8) == "건강함")
        print("PASS simulator -> localhost:8080 health 200")
        let providers = try JSONDecoder().decode([String].self, from: await client.request(path: "api/v1/auth/social/providers"))
        precondition(Set(providers) == Set(["GOOGLE", "APPLE", "KAKAO"]))
        print("PASS provider discovery 200: GOOGLE, APPLE, KAKAO")
        for provider in [APIClient.Provider.google, .apple, .kakao] {
            let challenge = try await client.challenge(provider: provider)
            precondition(challenge.nonce.count == 64)
            print("PASS \(provider.rawValue) challenge 200 with session cookie and CSRF")
            do {
                try await client.login(provider: provider, idToken: "invalid-simulator-probe-token", nickname: nil)
                fatalError("Invalid token unexpectedly accepted")
            } catch APIError.http(401) {
                print("PASS \(provider.rawValue) invalid ID token rejected 401")
            }
        }
        do {
            _ = try await client.currentUser()
            fatalError("Anonymous user authenticated")
        } catch APIError.http(401) { print("PASS anonymous profile rejected 401") }
        let body = try JSONSerialization.data(withJSONObject: ["token":"simulator-probe-not-a-real-fcm-token", "platform":"IOS"])
        do {
            _ = try await client.request(path:"api/v1/device-tokens", method:"PUT", body:body)
            fatalError("Anonymous device registration accepted")
        } catch APIError.http(401) { print("PASS anonymous device registration rejected 401") }
    }
}
