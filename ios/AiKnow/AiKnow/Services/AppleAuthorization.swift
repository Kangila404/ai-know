import AuthenticationServices
import UIKit

@MainActor
final class AppleAuthorization: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    struct Credential {
        let idToken: String
        let nickname: String?
    }

    private var continuation: CheckedContinuation<Credential, Error>?
    private var controller: ASAuthorizationController?
    private var window: UIWindow?

    func signIn(nonce: String) async throws -> Credential {
        guard continuation == nil,
              let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive }),
              let window = scene.windows.first(where: { $0.isKeyWindow }) else { throw APIError.configuration }
        self.window = window
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName]
        // The backend expects the exact challenge nonce in the signed Apple ID token.
        request.nonce = nonce
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            self.controller = controller
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        // Captured from the active scene before starting authorization.
        window ?? ASPresentationAnchor()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let data = credential.identityToken,
              let token = String(data: data, encoding: .utf8), !token.isEmpty else {
            finish(.failure(APIError.invalidResponse))
            return
        }
        let nickname = credential.fullName.flatMap { name -> String? in
            let value = PersonNameComponentsFormatter().string(from: name).trimmingCharacters(in: .whitespacesAndNewlines)
            return value.isEmpty ? nil : String(value.prefix(20))
        }
        finish(.success(Credential(idToken: token, nickname: nickname)))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish(.failure(error))
    }

    private func finish(_ result: Result<Credential, Error>) {
        let pending = continuation
        continuation = nil
        controller = nil
        window = nil
        pending?.resume(with: result)
    }
}
