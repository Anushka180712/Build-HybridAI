import SwiftUI

/// Same pastel tokens as the web app (web/src/styles/tokens.css). Light only.
enum Palette {
    static let paper = Color(hex: 0xFBF8F4)
    static let paper2 = Color(hex: 0xF6F2EC)
    static let ink = Color(hex: 0x24212C)
    static let ink2 = Color(hex: 0x565266)
    static let ink3 = Color(hex: 0x8B8799)
    static let line = Color(hex: 0xEDE8E1)
    static let lilac50 = Color(hex: 0xF6F3FF)
    static let lilac100 = Color(hex: 0xECE6FF)
    static let lilac500 = Color(hex: 0x7A68DE)
    static let lilac700 = Color(hex: 0x4A3AA3)
    static let mint50 = Color(hex: 0xF0FAF4)
    static let mint100 = Color(hex: 0xDCF3E6)
    static let mint700 = Color(hex: 0x226B47)
    static let butter50 = Color(hex: 0xFFFAEB)
    static let butter100 = Color(hex: 0xFFF0C7)
    static let butter700 = Color(hex: 0x845B00)
    static let rose50 = Color(hex: 0xFFF4F2)
    static let rose100 = Color(hex: 0xFFE2DC)
    static let rose700 = Color(hex: 0x9C3524)
    static let sky100 = Color(hex: 0xDDEBFB)
    static let sky600 = Color(hex: 0x3A78BF)
    static let peach100 = Color(hex: 0xFFE8D4)
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

extension Font {
    static func display(_ size: CGFloat) -> Font { .system(size: size, weight: .medium, design: .serif) }
}

struct CardStyle: ViewModifier {
    var tint: Color = .white
    func body(content: Content) -> some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(tint, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Palette.line))
    }
}

extension View {
    func card(_ tint: Color = .white) -> some View { modifier(CardStyle(tint: tint)) }
}

struct Pill: View {
    let text: String
    var systemImage: String?
    var bg: Color
    var fg: Color
    var body: some View {
        HStack(spacing: 5) {
            if let systemImage { Image(systemName: systemImage).font(.system(size: 11, weight: .semibold)) }
            Text(text).font(.system(size: 12, weight: .semibold))
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .foregroundStyle(fg).background(bg, in: Capsule())
    }
}
