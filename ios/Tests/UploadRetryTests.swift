import Foundation

private actor Attempt {
    var uploads = 0
    var indexes = 0
    func upload() -> PawlyUploadContract.Asset {
        uploads += 1
        return .init(public_id: "pawly/post-photos/test/file", secure_url: "https://example.org/photo", resource_type: "image")
    }
    func index() throws {
        indexes += 1
        if indexes == 1 { throw PawlyMediaError.indexFailed }
    }
    func verify() { precondition(uploads == 1 && indexes == 2) }
}

@main enum UploadRetryTests {
    static func main() async throws {
        let cache = PawlyUploadCache()
        let attempt = Attempt()
        @Sendable func resolve() async throws -> String {
            try await cache.resolve(key: "fixture") { await attempt.upload() } index: { _ in try await attempt.index() }
        }
        let first = try? await resolve()
        precondition(first == nil)
        async let a = resolve(), b = resolve()
        let urls = try await [a, b]
        precondition(Set(urls) == ["https://example.org/photo"])
        await attempt.verify()
        print("A failed ownership write and concurrent retries reused one upload")
    }
}
