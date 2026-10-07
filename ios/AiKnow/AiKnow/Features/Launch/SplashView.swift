import SwiftUI

struct SplashView: View {
    let onFinished: () -> Void
    @State private var isVisible = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.indigo, Color(red: 0.43, green: 0.32, blue: 0.93)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            Circle()
                .fill(.white.opacity(0.12))
                .frame(width: 340)
                .blur(radius: 4)
                .offset(x: 120, y: -260)

            VStack(spacing: 18) {
                BrandLogo(size: 104)
                VStack(spacing: 6) {
                    Text("AI Know")
                        .font(.system(size: 30, weight: .heavy, design: .rounded))
                    Text("하루 한 장, 더 넓은 AI 인사이트")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.78))
                }
            }
            .foregroundStyle(.white)
            .opacity(isVisible ? 1 : 0)
            .scaleEffect(isVisible ? 1 : 0.94)
        }
        .task {
            withAnimation(.easeOut(duration: 0.45)) {
                isVisible = true
            }
            try? await Task.sleep(nanoseconds: 1_250_000_000)
            onFinished()
        }
    }
}
