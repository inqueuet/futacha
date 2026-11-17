import Foundation

/// File I/O and text processing belong on the caller's background task.
struct SavedHtmlDocumentLoader {
    static let maximumBytes = 21 * 1024 * 1024
    enum ReadError: Error { case tooLarge, invalidEncoding }

    static func load(_ url: URL) throws -> String {
        var html = try decodeHTML(readBoundedHTML(url))
        // Each pattern stops at the next tag or quote it cannot cross (`[^<>]`,
        // `[^()]`), so malformed input cannot make a match rescan the rest of
        // the file from every candidate position.
        let labelRules: [(String, String)] = [
            (
                #"(?is)(<a\b[^<>]{0,1000}>\s*(?:fu|f)\d+\.(?:gif|jpe?g|jpe|png|webp|bmp|apng|avif|webm|mp4|m4v|mov|mkv|avi|ts|flv)\s*</a\s*>)\s*<span\b[^<>]{0,1000}>\s*(?:\[|［|&#0*91;|&#x0*5b;|&lbrack;)\s*見る\s*(?:\]|］|&#0*93;|&#x0*5d;|&rbrack;)\s*</span\s*>"#,
                "$1"
            ),
            (
                #"(?i)((?:fu|f)\d+\.(?:gif|jpe?g|jpe|png|webp|bmp|apng|avif|webm|mp4|m4v|mov|mkv|avi|ts|flv))(\s*</a\s*>)?\s*(?:\[|［|&#0*91;|&#x0*5b;|&lbrack;)\s*見る\s*(?:\]|］|&#0*93;|&#x0*5d;|&rbrack;)(?=\s*(?:</a\s*>|<br\b[^<>]*>|</?(?:font|span|blockquote|div|p|td)\b[^<>]*>|$))"#,
                "$1$2"
            )
        ]
        let externalRules: [(String, String)] = [
            (#"(?i)\b(src|href)\s*=\s*(['\"])\s*(?:https?:)?//.*?\2"#, "$1=$2#$2"),
            (#"(?i)url\(\s*(['\"]?)(?:https?:)?//[^()]*?\1\s*\)"#, "url()")
        ]
        html = try applying(labelRules, to: html)
        html = try removingActiveElements(html)
        html = try applying(externalRules, to: html)
        return withLocalOnlyPolicy(html)
    }

    private static func applying(_ rules: [(String, String)], to html: String) throws -> String {
        var html = html
        for (pattern, replacement) in rules {
            try Task.checkCancellation()
            guard let expression = try? NSRegularExpression(pattern: pattern) else { continue }
            html = try replacingMatches(of: expression, in: html, with: replacement)
        }
        return html
    }

    /// `stringByReplacingMatches` cannot be interrupted; enumerating with
    /// progress reports lets a closed viewer stop the work between matches.
    private static func replacingMatches(
        of expression: NSRegularExpression,
        in html: String,
        with template: String
    ) throws -> String {
        let source = html as NSString
        let result = NSMutableString(capacity: source.length)
        var copied = 0
        var cancelled = false
        expression.enumerateMatches(
            in: html,
            options: [.reportProgress],
            range: NSRange(location: 0, length: source.length)
        ) { match, _, stop in
            if Task.isCancelled {
                cancelled = true
                stop.pointee = true
                return
            }
            guard let match else { return }
            result.append(source.substring(with: NSRange(location: copied, length: match.range.location - copied)))
            result.append(expression.replacementString(for: match, in: html, offset: 0, template: template))
            copied = match.range.location + match.range.length
        }
        if cancelled { throw CancellationError() }
        result.append(source.substring(from: copied))
        return result as String
    }

    /// Removes script/iframe/object/embed elements in one forward pass. A lazy
    /// `<script…>.*?</script>` regex rescans to the end of the file for every
    /// unclosed start tag (quadratic on malformed HTML).
    static func removingActiveElements(_ html: String) throws -> String {
        let source = html as NSString
        let length = source.length
        let result = NSMutableString(capacity: length)
        var copied = 0
        var position = 0
        // Searches only move forward: once an end tag is missing, it stays missing.
        var missingEndTags = Set<String>()
        var steps = 0
        while position < length {
            steps += 1
            if steps % 1024 == 0 { try Task.checkCancellation() }
            let open = source.range(of: "<", options: .literal, range: NSRange(location: position, length: length - position))
            guard open.location != NSNotFound else { break }
            guard let name = activeElementName(in: source, at: open.location + 1) else {
                position = open.location + 1
                continue
            }
            result.append(source.substring(with: NSRange(location: copied, length: open.location - copied)))
            let afterName = open.location + 1 + (name as NSString).length
            var end = length
            let tagEnd = source.range(of: ">", options: .literal, range: NSRange(location: afterName, length: length - afterName))
            if tagEnd.location != NSNotFound {
                end = tagEnd.location + 1
                if name != "embed", !missingEndTags.contains(name) {
                    if let close = endTag(of: name, in: source, from: end) {
                        let closeEnd = source.range(of: ">", options: .literal, range: NSRange(location: close, length: length - close))
                        end = closeEnd.location == NSNotFound ? length : closeEnd.location + 1
                    } else {
                        missingEndTags.insert(name)
                        // script and iframe are raw text: unclosed, the rest is theirs.
                        if name == "script" || name == "iframe" { end = length }
                    }
                }
            }
            copied = end
            position = end
        }
        if copied < length { result.append(source.substring(from: copied)) }
        return result as String
    }

    private static let activeElementNames = ["script", "iframe", "object", "embed"]

    private static func activeElementName(in source: NSString, at location: Int) -> String? {
        for name in activeElementNames {
            let nameLength = (name as NSString).length
            guard location + nameLength <= source.length else { continue }
            let candidate = NSRange(location: location, length: nameLength)
            guard source.compare(name, options: .caseInsensitive, range: candidate) == .orderedSame else { continue }
            if isTagNameEnd(in: source, at: location + nameLength) { return name }
        }
        return nil
    }

    private static func isTagNameEnd(in source: NSString, at location: Int) -> Bool {
        guard location < source.length else { return true }
        let character = source.character(at: location)
        // '>' '/' and HTML whitespace (tab, LF, FF, CR, space).
        return [0x3E, 0x2F, 0x09, 0x0A, 0x0C, 0x0D, 0x20].contains(character)
    }

    private static func endTag(of name: String, in source: NSString, from start: Int) -> Int? {
        let token = "</" + name
        let tokenLength = (token as NSString).length
        var position = start
        while position < source.length {
            let found = source.range(of: token, options: .caseInsensitive, range: NSRange(location: position, length: source.length - position))
            guard found.location != NSNotFound else { return nil }
            if isTagNameEnd(in: source, at: found.location + tokenLength) { return found.location }
            position = found.location + tokenLength
        }
        return nil
    }

    /// Saved pages may come from anywhere via Files, and the rewrite above misses
    /// unquoted attributes, srcset, @import, meta refresh etc. The viewer blocks
    /// every non-local load with a content rule list; this CSP is the same policy
    /// inside the document (also when the rule list could not be compiled).
    static let contentSecurityPolicy =
        "default-src 'none'; img-src file: data: blob:; media-src file: data: blob:; " +
        "style-src file: 'unsafe-inline'; font-src file: data:; form-action 'none'; base-uri 'none'"

    /// Only these schemes may be loaded or navigated to by a saved page.
    static func isLocalURL(_ url: URL?) -> Bool {
        guard let scheme = url?.scheme?.lowercased() else { return false }
        return ["file", "about", "data", "blob"].contains(scheme)
    }

    static func withLocalOnlyPolicy(_ html: String) -> String {
        let meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"\(contentSecurityPolicy)\">" +
            "<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\">"
        // After a leading doctype (moving it would switch to quirks mode); the
        // parser places a leading meta into <head>, before anything it governs.
        if let doctype = html.range(of: #"^\s*<!doctype[^>]*>"#, options: [.regularExpression, .caseInsensitive]) {
            return String(html[..<doctype.upperBound]) + meta + String(html[doctype.upperBound...])
        }
        return meta + html
    }


    private static func readBoundedHTML(_ url: URL) throws -> Data {
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
        return data
    }

    /// Windows-31J: Shift_JIS with the NEC/IBM extensions Futaba pages use.
    private static let windows31J = String.Encoding(
        rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.dosJapanese.rawValue))
    )

    /// Pages saved by the app are UTF-8; ones added through Files may keep the
    /// board's Shift_JIS (or EUC-JP / ISO-2022-JP). The returned string is loaded
    /// with an explicit UTF-8 encoding, which overrides the page's own meta charset.
    static func decodeHTML(_ data: Data) throws -> String {
        let declared = declaredEncodings(in: data)
        // ISO-2022-JP is 7-bit and would otherwise "decode" as UTF-8 escape codes.
        if declared.first == .iso2022JP, let html = String(data: data, encoding: .iso2022JP) { return html }
        if data.starts(with: [0xFF, 0xFE]) || data.starts(with: [0xFE, 0xFF]),
           let html = String(data: data, encoding: .utf16) {
            return html
        }
        if let html = String(data: data, encoding: .utf8) { return html }
        for encoding in declared + [windows31J, .shiftJIS, .japaneseEUC] {
            try Task.checkCancellation()
            if let html = String(data: data, encoding: encoding) { return html }
        }
        throw ReadError.invalidEncoding
    }

    private static func declaredEncodings(in data: Data) -> [String.Encoding] {
        let head = String(decoding: data.prefix(4096), as: Unicode.ASCII.self)
        guard let match = head.range(
            of: #"<meta\b[^<>]{0,512}?charset\s*=\s*["']?\s*[A-Za-z0-9_.:-]{1,40}"#,
            options: [.regularExpression, .caseInsensitive]
        ) else { return [] }
        let tag = String(head[match])
        guard let nameStart = tag.range(of: "charset", options: [.caseInsensitive, .backwards]) else { return [] }
        let name = tag[nameStart.upperBound...]
            .trimmingCharacters(in: CharacterSet(charactersIn: "= \t\r\n\"'"))
        let lowered = name.lowercased()
        if ["shift_jis", "shift-jis", "sjis", "x-sjis", "windows-31j", "cp932", "ms932"].contains(lowered) {
            return [windows31J, .shiftJIS]
        }
        let cfEncoding = CFStringConvertIANACharSetNameToEncoding(name as CFString)
        guard cfEncoding != kCFStringEncodingInvalidId else { return [] }
        return [String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(cfEncoding))]
    }
}
