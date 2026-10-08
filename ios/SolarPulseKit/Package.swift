// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SolarPulseKit",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [
        .library(name: "SolarPulseKit", targets: ["SolarPulseKit"]),
    ],
    targets: [
        .target(
            name: "SolarPulseKit",
            swiftSettings: [.swiftLanguageMode(.v6)]
        ),
        .testTarget(
            name: "SolarPulseKitTests",
            dependencies: ["SolarPulseKit"],
            swiftSettings: [.swiftLanguageMode(.v6)]
        ),
    ]
)
