import Foundation

/// File I/O and text processing belong on the caller's background task.
struct SavedHtmlDocumentLoader {
    static let maximumBytes = 21 * 1024 * 1024
    enum ReadError: Error { case tooLarge, invalidEncoding }

    static func load(_ url: URL) throws -> String {
        var html = try readBoundedHTML(url)
        let rules: [(String, String)] = [
            (
                #"(?is)(<a\b[^>]{0,1000}>\s*(?:fu|f)\d+\.(?:gif|jpe?g|jpe|png|webp|bmp|apng|avif|webm|mp4|m4v|mov|mkv|avi|ts|flv)\s*</a\s*>)\s*<span\b[^>]{0,1000}>\s*(?:\[|［|&#0*91;|&#x0*5b;|&lbrack;)\s*見る\s*(?:\]|］|&#0*93;|&#x0*5d;|&rbrack;)\s*</span\s*>"#,
                "$1"
            ),
            (
                #"(?i)((?:fu|f)\d+\.(?:gif|jpe?g|jpe|png|webp|bmp|apng|avif|webm|mp4|m4v|mov|mkv|avi|ts|flv))(\s*</a\s*>)?\s*(?:\[|［|&#0*91;|&#x0*5b;|&lbrack;)\s*見る\s*(?:\]|］|&#0*93;|&#x0*5d;|&rbrack;)(?=\s*(?:</a\s*>|<br\b[^>]*>|</?(?:font|span|blockquote|div|p|td)\b[^>]*>|$))"#,
                "$1$2"
            ),
            (#"(?is)<script\b[^>]*>.*?</script\s*>"#, ""),
            (#"(?is)<(?:iframe|object|embed)\b[^>]*>.*?</(?:iframe|object|embed)\s*>"#, ""),
            (#"(?i)\b(src|href)\s*=\s*(['\"])\s*(?:https?:)?//.*?\2"#, "$1=$2#$2"),
            (#"(?i)url\(\s*(['\"]?)(?:https?:)?//.*?\1\s*\)"#, "url()")
        ]
        for (pattern, replacement) in rules {
            try Task.checkCancellation()
            guard let expression = try? NSRegularExpression(pattern: pattern) else { continue }
            let range = NSRange(html.startIndex..<html.endIndex, in: html)
            html = expression.stringByReplacingMatches(
                in: html,
                range: range,
                withTemplate: replacement
            )
        }
        return html
    }


    private static func readBoundedHTML(_ url: URL) throws -> String {
        let input = try FileHandle(forReadingFrom: url)
        defer { try? input.close() }
        var data = Data()
        while true {
            try Task.checkCancellation()
            let count = min(64 * 1024, maximumBytes - data.count + 1)
            guard let chunk = try input.read(upToCount: count), !chunk.isEmpty else { break }
            guard data.count + chunk.count <= maximumBytes else { throw ReadError.tooLarge }
            data.append(chunk)
        }
        guard let html = String(data: data, encoding: .utf8) else { throw ReadError.invalidEncoding }
        return html
    }
}
