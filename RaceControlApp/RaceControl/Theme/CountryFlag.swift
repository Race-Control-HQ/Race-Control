import Foundation

/// Maps FastF1 / Ergast country & nationality strings to emoji flags so the UI
/// can show a recognisable marker without bundling image assets.
enum CountryFlag {

    /// ISO alpha-3 (FastF1 `CountryCode`) or country name -> flag emoji.
    static func flag(country: String?, code: String? = nil) -> String {
        if let code, let f = alpha3ToFlag[code.uppercased()] { return f }
        if let country, let f = nameToFlag[country.lowercased()] { return f }
        return "🏁"
    }

    private static func regional(_ a: Character, _ b: Character) -> String {
        let base: UInt32 = 0x1F1E6
        let s = base + UInt32(a.asciiValue! - 65)
        let t = base + UInt32(b.asciiValue! - 65)
        return String(UnicodeScalar(s)!) + String(UnicodeScalar(t)!)
    }

    private static let alpha3ToAlpha2: [String: String] = [
        "GBR": "GB", "NED": "NL", "MON": "MC", "ESP": "ES", "MEX": "MX",
        "AUS": "AU", "FIN": "FI", "GER": "DE", "FRA": "FR", "CAN": "CA",
        "JPN": "JP", "THA": "TH", "CHN": "CN", "DEN": "DK", "USA": "US",
        "ITA": "IT", "AUT": "AT", "BRA": "BR", "NZL": "NZ", "BEL": "BE",
        "SUI": "CH", "POL": "PL", "RUS": "RU", "IND": "IN", "ARG": "AR",
        "BRN": "BH", "SAU": "SA", "ARE": "AE", "AZE": "AZ", "SGP": "SG",
        "QAT": "QA", "HUN": "HU", "POR": "PT", "TUR": "TR", "KSA": "SA",
    ]

    private static var alpha3ToFlag: [String: String] {
        alpha3ToAlpha2.reduce(into: [:]) { dict, pair in
            let (a3, a2) = pair
            let chars = Array(a2)
            dict[a3] = regional(chars[0], chars[1])
        }
    }

    private static let nameToFlag: [String: String] = [
        "united kingdom": "🇬🇧", "uk": "🇬🇧", "great britain": "🇬🇧", "british": "🇬🇧",
        "netherlands": "🇳🇱", "dutch": "🇳🇱",
        "monaco": "🇲🇨", "monegasque": "🇲🇨",
        "spain": "🇪🇸", "spanish": "🇪🇸",
        "mexico": "🇲🇽", "mexican": "🇲🇽",
        "australia": "🇦🇺", "australian": "🇦🇺",
        "finland": "🇫🇮", "finnish": "🇫🇮",
        "germany": "🇩🇪", "german": "🇩🇪",
        "france": "🇫🇷", "french": "🇫🇷",
        "canada": "🇨🇦", "canadian": "🇨🇦",
        "japan": "🇯🇵", "japanese": "🇯🇵",
        "thailand": "🇹🇭", "thai": "🇹🇭",
        "china": "🇨🇳", "chinese": "🇨🇳",
        "denmark": "🇩🇰", "danish": "🇩🇰",
        "united states": "🇺🇸", "usa": "🇺🇸", "american": "🇺🇸",
        "italy": "🇮🇹", "italian": "🇮🇹",
        "austria": "🇦🇹", "austrian": "🇦🇹",
        "brazil": "🇧🇷", "brazilian": "🇧🇷",
        "new zealand": "🇳🇿",
        "belgium": "🇧🇪", "belgian": "🇧🇪",
        "switzerland": "🇨🇭", "swiss": "🇨🇭",
        "bahrain": "🇧🇭", "saudi arabia": "🇸🇦", "united arab emirates": "🇦🇪",
        "azerbaijan": "🇦🇿", "singapore": "🇸🇬", "qatar": "🇶🇦",
        "hungary": "🇭🇺", "portugal": "🇵🇹", "turkey": "🇹🇷",
    ]
}
