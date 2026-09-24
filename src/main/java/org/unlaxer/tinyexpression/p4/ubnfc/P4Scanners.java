// Vendored from ubnfc examples/p4-java/src/main/java/org/ubnfc/p4/P4Scanners.java by
// scripts/regenerate-ubnfc-parser.sh (package renamed only). Do not edit by hand; see UBNFC_PIN.
package org.unlaxer.tinyexpression.p4.ubnfc;

import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseOptions;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.TokenScanner;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.rt.Input;

/** 入力と ScanState だけに依存する純粋 scanner。IR の @memoSafeToken 宣言に対応する。 */
public final class P4Scanners {
    private P4Scanners() {}
    public static final Map<String, TokenScanner> ALL = Map.of(
        "TinyExpressionP4::STRING", scanner(P4Scanners::string, "STRING", true),
        "TinyExpressionP4::CODE_START", scanner(P4Scanners::codeStart, "CODE_START", false),
        "TinyExpressionP4::CODE_END", scanner(P4Scanners::codeEnd, "CODE_END", false));
    public static ParseOptions options(boolean ast) {
        return new ParseOptions(ast, true, false, true, ALL);
    }
    /** 認識専用（D-023 / D-035 の二段構え）: 出現表も診断観測も要求しない。 */
    public static ParseOptions recognize() {
        return new ParseOptions(false, true, false, true, ALL, ParseOptions.DEFAULT_MAX_DEPTH, false, false);
    }
    @FunctionalInterface private interface Scan { int end(Input in, int at, boolean invert); }
    private static TokenScanner scanner(Scan scan, String label, boolean unquote) {
        return (in, state) -> {
            // 旧 LazyChain/LazyChoice の begin は reset 時に matched を consumed に戻す。
            int start = state.mode() == TokenScanner.Mode.consumed || state.resetMatchedWithConsumed()
                ? state.consumed() : state.matched();
            int end = scan.end(in, start, state.invertMatch());
            if (end < 0) return new TokenScanner.ScanResult(false, state.consumed(), state.matched(),
                null, List.of(new TokenScanner.ScanDiagnostic(start, label)), List.of());
            int consumed = state.mode() == TokenScanner.Mode.consumed ? end : state.consumed();
            return new TokenScanner.ScanResult(true, consumed, end,
                valueSpan(in, start, end, unquote), List.of(), List.of());
        };
    }
    // 旧 Mapper の文字列変換は「外側空白の strip と**単引用符だけ**の除去」
    // （unlaxer-dsl/specs/generators.md #132、unlaxer-common elementary/QuotedParser.java:55-71）。
    // 共有 registry の scanners/rust/scanners.rs:143-144 と scanners/java/Registry.java:70-71 も
    // 単引用符のときだけ value span を 1 単位内側へ寄せる。ここだけ寄せていなかったため、
    // 同じ入力で Java backend の value が 'jp'、Rust backend が jp になっていた
    // （docs/reports/2026-09-23-parser-feature-correctness-comparison.md §3.2 結果(2) の 256 件）。
    private static TokenScanner.ValueSpan valueSpan(Input in, int start, int end, boolean unquote) {
        return unquote && end - start >= 2 && in.startsWith(start, "'")
            ? new TokenScanner.ValueSpan(start + 1, end - 1)
            : new TokenScanner.ValueSpan(start, end);
    }
    // tinyexpression: parser/StringLiteralParser.java:19-25。
    // unlaxer-common: elementary/QuotedParser.java:55-71、EscapeInQuotedParser.java:22-26。
    // NotPropagatableSource.java:41-42 は親の invert を反転する。復号はしない。
    private static int string(Input in, int at, boolean invert) {
        int end = quoted(in, at, invert, '"');
        return end >= 0 ? end : quoted(in, at, invert, '\'');
    }
    private static int quoted(Input in, int at, boolean invert, int quote) {
        int p = one(in, at, invert, c -> c == quote);
        if (p < 0) return -1;
        while (true) {
            int next = one(in, p, invert, c -> c == '\\');
            if (next >= 0) next = one(in, next, invert, c -> true);
            if (next < 0) next = one(in, p, !invert, c -> c == quote);
            if (next < 0) break;
            p = next;
        }
        return one(in, p, invert, c -> c == quote);
    }
    // tinyexpression: parser/javalang/CodeStartParser.java:32-40、TripleBackTickParser.java:7-8。
    // parser/javatype/JavaClassNameParser.java:28-39: identifier ('.' identifier)*。
    private static int codeStart(Input in, int at, boolean invert) {
        if (!bol(in, at, invert)) return -1;
        int p = word(in, at, invert, "```");
        p = identifier(in, p, invert);
        p = word(in, p, invert, ":");
        p = identifier(in, p, invert);
        if (p < 0) return -1;
        while (true) {
            int next = identifier(in, word(in, p, invert, "."), invert);
            if (next < 0) break;
            p = next;
        }
        return eol(in, p, invert);
    }
    // tinyexpression: parser/javalang/CodeEndParser.java:21-26。
    private static int codeEnd(Input in, int at, boolean invert) {
        return bol(in, at, invert) ? eol(in, word(in, at, invert, "```"), invert) : -1;
    }
    // unlaxer-common: elementary/StartOfLineParser.java:40-53。
    private static boolean bol(Input in, int p, boolean invert) {
        return !invert && (p == 0 || in.startsWith(p - 1, "\r") || in.startsWith(p - 1, "\n"));
    }
    // elementary/LineTerminatorParser.java:20-26: CRLF | CR | LF | EOF。
    private static int eol(Input in, int p, boolean invert) {
        if (p < 0) return -1;
        for (String line : List.of("\r\n", "\r", "\n")) {
            int end = word(in, p, invert, line);
            if (end >= 0) return end;
        }
        return p == in.length() || invert ? p : -1;
    }
    // clang/IdentifierParser.java:24-31 と posix/Alphabet{Numeric}UnderScoreParser。
    private static int identifier(Input in, int p, boolean invert) {
        p = one(in, p, invert, P4Scanners::letter);
        if (p < 0) return -1;
        while (true) {
            int end = one(in, p, invert, c -> letter(c) || c >= '0' && c <= '9');
            if (end < 0) return p;
            p = end;
        }
    }
    private static boolean letter(int c) { return c == '_' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'; }
    private static int one(Input in, int p, boolean invert, IntPredicate test) {
        if (p < 0 || p >= in.length()) return -1;
        int cp = in.codePointAt(p);
        return test.test(cp) != invert ? p + Character.charCount(cp) : -1;
    }
    // WordParser.java:77-82: 反転時の短い接頭辞も、空でなければ消費する。
    private static int word(Input in, int p, boolean invert, String text) {
        if (p < 0 || p >= in.length() || in.startsWith(p, text) == invert) return -1;
        int end = p;
        for (int n = 0; n < text.codePointCount(0, text.length()) && end < in.length(); n++)
            end += Character.charCount(in.codePointAt(end));
        return end;
    }
}
