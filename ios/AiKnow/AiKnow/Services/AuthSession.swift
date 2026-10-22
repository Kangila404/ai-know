import SwiftUI
import GoogleSignIn
import AuthenticationServices
import OSLog
import KakaoSDKUser
import KakaoSDKAuth
import KakaoSDKCommon

@MainActor
@Observable
final class AuthSession {
    static let shared = AuthSession()
    private(set) var user: APIClient.User?
    private(set) var busy = false
    var errorMessage: String?
    var isAdmin: Bool { user?.userRole == "ADMIN" }
    var isPreview: Bool {
        #if DEBUG
        return APIClient.shared.previewRequest != nil
        #else
        return false
        #endif
    }
    #if DEBUG
    func beginPreview(admin: Bool) {
        let preview = DevelopmentPreview(admin: admin)
        APIClient.shared.previewRequest = preview.request
        user = preview.user
        errorMessage = nil
    }
    func endPreview() {
        APIClient.shared.previewRequest = nil
        user = nil
        errorMessage = nil
    }
    #endif
    private let logger = Logger(subsystem: "org.aiknow.AiKnow", category: "Authentication")
    private let appleAuthorization = AppleAuthorization()

    func signInGoogle() async -> Bool {
        guard !busy else { return false }
        busy = true
        errorMessage = nil
        defer { busy = false }
        var stage = "challenge"
        do {
            guard let iosID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String,
                  let webID = Bundle.main.object(forInfoDictionaryKey: "GIDServerClientID") as? String,
                  let presenter = presenter else { throw APIError.configuration }
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: iosID, serverClientID: webID)
            let challenge = try await APIClient.shared.challenge()
            stage = "provider"
            // Pass the server nonce unchanged. The server compares it to the Google ID-token nonce.
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: challenge.nonce)
            guard let idToken = result.user.idToken?.tokenString else { throw APIError.invalidResponse }
            let name = (result.user.profile?.name).map { String($0.prefix(20)) }
            stage = "session"
            try await APIClient.shared.login(idToken: idToken, nickname: name)
            stage = "profile"
            try await finishLogin()
            return true
        } catch {
            if (error as NSError).domain == kGIDSignInErrorDomain && (error as NSError).code == GIDSignInError.canceled.rawValue {
                return false
            }
            report(error, provider: "Google", stage: stage)
            return false
        }
    }

    func signInApple() async -> Bool {
        guard !busy else { return false }
        busy = true
        errorMessage = nil
        defer { busy = false }
        var stage = "challenge"
        do {
            let challenge = try await APIClient.shared.challenge(provider: .apple)
            stage = "provider"
            let credential = try await appleAuthorization.signIn(nonce: challenge.nonce)
            stage = "session"
            try await APIClient.shared.login(provider: .apple, idToken: credential.idToken, nickname: credential.nickname)
            stage = "profile"
            try await finishLogin()
            return true
        } catch {
            let failure = error as NSError
            if failure.domain == ASAuthorizationError.errorDomain && failure.code == ASAuthorizationError.canceled.rawValue {
                return false
            }
            report(error, provider: "Apple", stage: stage)
            return false
        }
    }

    func signInKakao() async -> Bool {
        guard !busy else { return false }
        busy = true
        errorMessage = nil
        defer { busy = false }
        var stage = "challenge"
        do {
            let challenge = try await APIClient.shared.challenge(provider: .kakao)
            stage = "provider"
            // Account login works both without KakaoTalk (Simulator) and on real devices.
            let token: OAuthToken = try await withCheckedThrowingContinuation { continuation in
                UserApi.shared.loginWithKakaoAccount(nonce: challenge.nonce) { token, error in
                    if let error { continuation.resume(throwing: error) }
                    else if let token { continuation.resume(returning: token) }
                    else { continuation.resume(throwing: APIError.invalidResponse) }
                }
            }
            guard let idToken = token.idToken, !idToken.isEmpty else {
                errorMessage = "카카오에서 ID 토큰을 받지 못했습니다. Kakao Developers에서 OpenID Connect 활성화를 확인해 주세요."
                return false
            }
            stage = "session"
            try await APIClient.shared.login(provider: .kakao, idToken: idToken, nickname: nil)
            stage = "profile"
            try await finishLogin()
            return true
        } catch {
            if let kakaoError = error as? SdkError,
               case .ClientFailed(reason: .Cancelled, errorMessage: _) = kakaoError { return false }
            report(error, provider: "Kakao", stage: stage)
            return false
        }
    }

    private func report(_ error: Error, provider: String, stage: String) {
        let failure = error as NSError
        // Never log localizedDescription/userInfo: OAuth errors may embed credentials.
        let domain = failure.domain.filter { $0.isASCII && ($0.isLetter || $0.isNumber || ".-_".contains($0)) }.prefix(100)
        logger.error("Auth \(provider, privacy: .public) stage=\(stage, privacy: .public) domain=\(String(domain), privacy: .public) code=\(failure.code)")
        let explanation = (error as? APIError)?.errorDescription ?? "인증을 완료하지 못했습니다."
        errorMessage = "\(provider): \(explanation)"
        #if DEBUG
        errorMessage! += " [\(stage), \(domain):\(failure.code)]"
        #endif
    }

    private func finishLogin() async throws {
        user = try await APIClient.shared.currentUser()
        Task { await PushNotifications.shared.syncToken() }
    }

    func restoreSession() async -> Bool {
        do {
            user = try await APIClient.shared.currentUser()
            try await APIClient.shared.refreshCSRF()
            return true
        } catch APIError.http(401) {
            user = nil
            APIClient.shared.clearSession()
            return false
        } catch { return false }
    }

    func refreshUser() async throws { user = try await APIClient.shared.currentUser() }

    func completeOnboarding() async -> Bool {
        guard !busy else { return false }
        busy = true; errorMessage = nil
        defer { busy = false }
        do {
            user = try await APIClient.shared.decode("api/v1/users/onboarding", method: "PATCH")
            return true
        } catch { errorMessage = error.localizedDescription; return false }
    }

    func expireSession() {
        user = nil
        APIClient.shared.clearSession()
    }

    func withdraw() async {
        if isPreview { errorMessage = "미리보기에서는 계정을 삭제하지 않습니다."; return }
        guard !busy else { return }
        busy = true; errorMessage = nil
        defer { busy = false }
        do {
            await PushNotifications.shared.waitForSynchronization()
            _ = try await APIClient.shared.request(path: "api/v1/users/me", method: "DELETE")
            GIDSignIn.sharedInstance.signOut()
            TokenManager.manager.deleteToken()
            PushNotifications.shared.pendingCardID = nil
            expireSession()
        } catch { errorMessage = error.localizedDescription }
    }

    func logout() async {
        #if DEBUG
        if isPreview { endPreview(); return }
        #endif
        guard !busy else { return }
        busy = true
        defer { busy = false }
        do {
            // Drain an in-flight registration before deactivating the device token.
            await PushNotifications.shared.waitForSynchronization()
            if let token = PushNotifications.shared.token {
                struct Body: Encodable { let token: String }
                _ = try await APIClient.shared.request(path: "api/v1/device-tokens", method: "DELETE", body: JSONEncoder().encode(Body(token: token)))
            }
            try await APIClient.shared.logout()
            PushNotifications.shared.pendingCardID = nil
            GIDSignIn.sharedInstance.signOut()
            TokenManager.manager.deleteToken()
            user = nil
            errorMessage = nil
        } catch APIError.http(401) {
            APIClient.shared.clearSession()
            GIDSignIn.sharedInstance.signOut()
            TokenManager.manager.deleteToken()
            user = nil
        } catch { errorMessage = "로그아웃에 실패했습니다. 서버 연결을 확인하고 다시 시도해 주세요." }
    }

    private var presenter: UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
        var controller = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let next = controller?.presentedViewController { controller = next }
        return controller
    }
}
