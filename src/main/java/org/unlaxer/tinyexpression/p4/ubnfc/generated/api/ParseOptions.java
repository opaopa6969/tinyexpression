package org.unlaxer.tinyexpression.p4.ubnfc.generated.api;
import java.util.Map;
/**
 * 観測の要求。{@code occurrences} は公開する capture 出現表（D-035 の Java 版）、
 * {@code diagnostics} は診断観測（D-023）。どちらも false の認識専用経路は Match 木も
 * 診断 DAG も作らず、失敗・末尾入力の結果だけを診断モードで決定的に再解析する。
 *
 * <p>D-057: この 2 つは {@code buildAst=true} でも効く。AST だけが要るなら
 * {@code options.withObservations(false, false)} を使うと、出現表と診断を作らずに typed AST を組む
 * （typed AST と canonical JSON は要求した場合と完全に一致する。失敗・末尾入力は決定的に再解析される）。
 */
public record ParseOptions(boolean buildAst, boolean requireFullInput, boolean lexical,
                           boolean memo, Map<String, TokenScanner> scanners, int maxDepth,
                           boolean occurrences, boolean diagnostics) {
    public static final int DEFAULT_MAX_DEPTH = 10_000;
    public ParseOptions {
        scanners = Map.copyOf(scanners);
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be positive");
    }
    /** 既存の呼出し形。観測は従来どおり全て要求する。 */
    public ParseOptions(boolean buildAst, boolean requireFullInput, boolean lexical, boolean memo, Map<String, TokenScanner> scanners, int maxDepth) {
        this(buildAst, requireFullInput, lexical, memo, scanners, maxDepth, true, true);
    }
    public ParseOptions(boolean buildAst, boolean requireFullInput, boolean lexical, boolean memo, Map<String, TokenScanner> scanners) {
        this(buildAst, requireFullInput, lexical, memo, scanners, DEFAULT_MAX_DEPTH);
    }
    public ParseOptions(boolean buildAst, boolean requireFullInput) { this(buildAst, requireFullInput, false, true, Map.of()); }
    /** D-023 の再解析用。解析は決定的なので観測は要求して解析し直したものと同一。 */
    public ParseOptions withDiagnostics() {
        return diagnostics ? this : new ParseOptions(buildAst, requireFullInput, lexical, memo, scanners, maxDepth, occurrences, true);
    }
    /** A/B 計測用に観測要求だけを差し替える。 */
    public ParseOptions withObservations(boolean occurrences, boolean diagnostics) {
        return new ParseOptions(buildAst, requireFullInput, lexical, memo, scanners, maxDepth, occurrences, diagnostics);
    }
    public ParseOptions withMemo(boolean memo) {
        return new ParseOptions(buildAst, requireFullInput, lexical, memo, scanners, maxDepth, occurrences, diagnostics);
    }
    public static final ParseOptions DEFAULT = new ParseOptions(true, true);
    /** 認識専用（D-023 / D-035 の二段構え）。出現表も診断観測も作らない。 */
    public static final ParseOptions RECOGNIZE = new ParseOptions(false, true, false, true, Map.of(), DEFAULT_MAX_DEPTH, false, false);
    public static final ParseOptions PREFIX = new ParseOptions(true, false);
}
