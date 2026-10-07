import SwiftUI

struct BrandLogo: View {
    var size: CGFloat = 72

    var body: some View {
        Image("AiKnowLogo")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.30, style: .continuous))
            .accessibilityLabel("AI Know")
    }
}
