import SwiftUI

// Claude brand colors
extension Color {
    static let claudePurple = Color(red: 0x6B/255, green: 0x4F/255, blue: 0xBB/255)
    static let claudePurpleLight = Color(red: 0x8B/255, green: 0x6F/255, blue: 0xDB/255)
    static let claudePurpleDark = Color(red: 0x4B/255, green: 0x2F/255, blue: 0x9B/255)
    static let claudePurpleSurface = Color(red: 0x1A/255, green: 0x10/255, blue: 0x25/255)

    // Status colors
    static let statusNormal = Color(red: 0x6B/255, green: 0x4F/255, blue: 0xBB/255)
    static let statusWarning = Color(red: 0xE8/255, green: 0x94/255, blue: 0x3A/255)
    static let statusCritical = Color(red: 0xE8/255, green: 0x54/255, blue: 0x54/255)

    // Background
    static let darkBackground = Color(red: 0x0D/255, green: 0x0D/255, blue: 0x0D/255)
    static let darkSurface = Color(red: 0x1A/255, green: 0x1A/255, blue: 0x2E/255)
    static let darkSurfaceVariant = Color(red: 0x25/255, green: 0x25/255, blue: 0x40/255)
    static let darkCard = Color(red: 0x16/255, green: 0x16/255, blue: 0x2A/255)

    // Text
    static let textPrimary = Color(red: 0xE8/255, green: 0xE6/255, blue: 0xF0/255)
    static let textSecondary = Color(red: 0xA0/255, green: 0x9B/255, blue: 0xB0/255)
    static let textMuted = Color(red: 0x6B/255, green: 0x66/255, blue: 0x80/255)

    // Extra usage / Opus colors
    static let statusExtra = Color(red: 0x10/255, green: 0xB9/255, blue: 0x81/255)
    static let statusExtraLight = Color(red: 0x34/255, green: 0xD3/255, blue: 0x99/255)
    static let statusOpus = Color(red: 0xF5/255, green: 0x9E/255, blue: 0x0B/255)

    // Progress bar backgrounds
    static let progressTrack = Color(red: 0x2A/255, green: 0x2A/255, blue: 0x40/255)
}
