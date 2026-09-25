package org.unlaxer.tinyexpression.p4.ubnfc.generated.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Capture;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Diagnostic;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.MappingException;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseOptions;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseResult;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Span;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.TokenScanner;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.rt.Diagnostics;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.rt.Input;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.rt.MemoTable;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.rt.ScopeStore;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.rt.Severity;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.internal.Recipe.Value;

/** Per-parse state; transactions preserve syntax diagnostics but undo semantic effects. */
public final class Session {
    @FunctionalInterface public interface Call { Match parse(Session session, Frame frame); }
    /** Expected-hint label with a process-wide stable id; generated code holds these as static constants so failures never hash strings. */
    public static final class Label {
        private static final java.util.concurrent.ConcurrentHashMap<String, Label> REGISTRY = new java.util.concurrent.ConcurrentHashMap<>();
        private static volatile String[] texts = new String[256];
        private static int count;
        public final String text; public final int id;
        /** A group label stands for several expected hints registered at one position (trivia delimiter sets); expanded on output. */
        final Label[] members;
        private Label(String text, int id, Label[] members) { this.text = text; this.id = id; this.members = members; }
        public static Label of(String text) {
            Label label = REGISTRY.get(text);
            if (label != null) return label;
            synchronized (Label.class) {
                label = REGISTRY.get(text);
                if (label == null) {
                    if (count == texts.length) texts = Arrays.copyOf(texts, count * 2);
                    texts[count] = text; label = new Label(text, count++, null); REGISTRY.put(text, label);
                }
                return label;
            }
        }
        public static String text(int id) { return texts[id]; }
        static Label group(Label[] members) {
            var names = new StringBuilder("\u0000group");
            for (Label member : members) names.append('\u0001').append(member.text);
            String key = names.toString();
            Label label = REGISTRY.get(key);
            if (label != null) return label;
            synchronized (Label.class) {
                label = REGISTRY.get(key);
                if (label == null) {
                    if (count == texts.length) texts = Arrays.copyOf(texts, count * 2);
                    texts[count] = key; label = new Label(key, count++, members.clone()); REGISTRY.put(key, label);
                }
                return label;
            }
        }
        static int count() { return count; }
        /** Expand ids into sorted distinct hint texts (group members flattened). */
        static List<String> texts(int[] ids) {
            if (ids.length == 0) return List.of();
            var result = new java.util.TreeSet<String>();
            for (int id : ids) {
                Label label = REGISTRY.get(texts[id]);
                if (label.members == null) result.add(label.text); else for (Label member : label.members) result.add(member.text);
            }
            return List.copyOf(result);
        }
    }
    private static final Label L_SPACE = Label.of("' '"), L_WILDCARD_STRING = Label.of("WildCardStringParser"),
        L_SIGN = Label.of("SignParser"), L_DIGIT = Label.of("DigitParser"), L_ONE_OR_MORE = Label.of("OneOrMore"), L_DOT = Label.of("'.'"),
        L_CHAIN = Label.of("Chain"), L_NUMBER = Label.of("NumberParser"), L_E = Label.of("EParser"), L_EXPONENT = Label.of("ExponentParser"),
        L_ALPHABET_UNDERSCORE = Label.of("AlphabetUnderScoreParser"), L_IDENTIFIER = Label.of("IdentifierParser"), L_ALNUM_UNDERSCORE = Label.of("AlphabetNumericUnderScoreParser"),
        L_ANY = Label.of("ANY"), L_EMPTY = Label.of("EMPTY"), L_WILDCARD_CHAR = Label.of("WildCardCharacterParser"), L_EOS = Label.of("EndOfSourceParser"), L_REPEAT = Label.of("Repeat");
    public static final class Frame {
        public int c, m;
        public boolean matched, invert, reset = true;
        public int position() { return matched ? m : c; }
        public Frame copy(boolean begin) {
            var f = new Frame(); f.c = c; f.m = begin && reset ? c : m;
            f.matched = matched; f.invert = invert; f.reset = reset; return f;
        }
        public void commit(Frame child) { c = child.c; m = child.m; }
        public void advance(int length) { if (matched) m += length; else { c += length; m = c; } }
    }
    /** Capture occurrence; completion order is stamped by the completing rule (was an identity map). */
    public static final class Occurrence {
        private String site, name; private final Value value; long completed = -1;
        public Occurrence(String site, String name, Value value) { this.site = site; this.name = name; this.value = value; }
        public String site() { return site; } public String name() { return name; } public Value value() { return value; }
        /** 左因数分解（D-061）が共有要素の出現を採用候補の site へ付け替える。実体は評価のたびに新しく作られる。 */
        void retag(String site, String name) { this.site = site; this.name = name; }
    }
    public record Trace(String rule, String expr, int start, int end, List<Trace> children, String token) {}
    public record RecoveryEvent(String rule, String mode, int start, int end, String pattern, int syncStart,
                                int syncEnd, Diagnostic diagnostic, Recipe marker) {}
    /** values == null denotes plain text; an empty values list denotes an absent semantic value. */
    public record Match(List<Recipe> nodes, List<Occurrence> local, List<Occurrence> captures,
                        List<Trace> traces, int start, int end, boolean mappingFailure, List<RecoveryEvent> recoveries, List<Match> items, String text, List<Value> values) {
        public Match(List<Recipe> nodes, List<Occurrence> local, List<Occurrence> captures,
                     List<Trace> traces, int start, int end, boolean mappingFailure, List<RecoveryEvent> recoveries, List<Match> items, String text) {
            this(nodes, local, captures, traces, start, end, mappingFailure, recoveries, items, text, null);
        }
        public Match(List<Recipe> nodes, List<Occurrence> local, List<Occurrence> captures,
                     List<Trace> traces, int start, int end, boolean mappingFailure, List<RecoveryEvent> recoveries) {
            this(nodes, local, captures, traces, start, end, mappingFailure, recoveries, List.of(), null);
        }
        public Match(List<Recipe> nodes, List<Occurrence> local, List<Occurrence> captures,
                     List<Trace> traces, int start, int end, boolean mappingFailure) {
            this(nodes, local, captures, traces, start, end, mappingFailure, List.of());
        }
        public Match(List<Recipe> nodes, List<Occurrence> local, List<Occurrence> captures,
                     List<Trace> traces, int start, int end) { this(nodes, local, captures, traces, start, end, false); }
        // Lists are built only inside Session and never mutated after construction; no defensive copies.
        public static Match empty(int start, int end) { return new Match(List.of(), List.of(), List.of(), List.of(), start, end); }
    }
    /** Trivia delimiter with labels and an ASCII bitmap precomputed once (generated code builds these as static constants). */
    public static final class Delimiter {
        private final String kind, open, close; private final int[] characters;
        final boolean isCharacters, isLineComment; final Label openLabel, closeLabel; final long asciiLow, asciiHigh; final boolean asciiOnly; final char first;
        public Delimiter(String kind, String open, String close, int[] characters) {
            this.kind = kind; this.open = open; this.close = close; this.characters = characters;
            isCharacters = kind.equals("characters"); isLineComment = kind.equals("lineComment");
            openLabel = open == null ? null : Label.of("'" + open + "'"); closeLabel = close == null ? null : Label.of("'" + close + "'");
            long low = 0, high = 0; boolean ascii = true;
            for (int ch : characters) { if (ch < 64) low |= 1L << ch; else if (ch < 128) high |= 1L << (ch - 64); else ascii = false; }
            asciiLow = low; asciiHigh = high; asciiOnly = ascii; first = open == null || open.isEmpty() ? 0 : open.charAt(0);
        }
        public String kind() { return kind; } public String open() { return open; } public String close() { return close; } public int[] characters() { return characters; }
        boolean contains(int ch) {
            if (ch < 64) return ch >= 0 && (asciiLow & (1L << ch)) != 0;
            if (ch < 128) return (asciiHigh & (1L << (ch - 64))) != 0;
            return !asciiOnly && Arrays.binarySearch(characters, ch) >= 0;
        }
    }
    /** A delimiter set with the failure hint groups of each failing prefix precomputed (one registered id per trivia round). */
    public static final class Delimiters {
        final Delimiter[] items; final Label[] prefixGroups;
        /** どの区切りも始まり得ない先頭文字を 1 回の bit 試験で弾くための ASCII 表（非 ASCII が入る集合では使わない）。 */
        final long startLow, startHigh; final boolean startAscii;
        public Delimiters(Delimiter... items) {
            this.items = items; prefixGroups = new Label[items.length];
            long low = 0, high = 0; boolean ascii = true;
            for (Delimiter item : items) {
                if (item.isCharacters) { low |= item.asciiLow; high |= item.asciiHigh; ascii &= item.asciiOnly; }
                else if (item.open == null || item.open.isEmpty()) ascii = false;
                else { char c = item.open.charAt(0); if (c < 64) low |= 1L << c; else if (c < 128) high |= 1L << (c - 64); else ascii = false; }
            }
            // 区切りが 0 個の集合では一般経路が hint を 1 件も登録しないので、高速経路を使わない。
            startLow = low; startHigh = high; startAscii = ascii && items.length > 0;
            var members = new ArrayList<Label>();
            for (int i = 0; i < items.length; i++) {
                if (items[i].isCharacters) members.add(L_SPACE);
                else if (items[i].isLineComment) members.add(items[i].openLabel);
                else { members.add(items[i].openLabel); members.add(items[i].closeLabel); }
                prefixGroups[i] = members.size() == 1 ? members.getFirst() : Label.group(members.toArray(new Label[0]));
            }
        }
        /** 全ての区切りが失敗した周回が登録する 1 件（候補除外の再生が使う）。 */
        Label fullGroup() { return prefixGroups[prefixGroups.length - 1]; }
        /** false なら「どの区切りもこの文字からは始まらない」ことが確定する。 */
        boolean canStart(int ch) {
            if (!startAscii) return true;
            if (ch < 64) return ch >= 0 && (startLow & (1L << ch)) != 0;
            return ch < 128 && (startHigh & (1L << (ch - 64))) != 0;
        }
    }

    /**
     * 候補除外（emit/Prediction）の無効化スイッチ。既定は有効で、参照経路（全候補試行）との
     * 差分検証と計測のためだけに false にする（system property {@code -Dubnfc.predict=false} でも切れる）。
     * 観測結果は両経路で等しくなければならない。
     */
    public static boolean predict = !"false".equals(System.getProperty("ubnfc.predict"));
    /**
     * D-066（排他 predict の空振り短絡）の無効化スイッチ。既定は有効で、参照経路（元順で全候補を
     * 試す経路）との差分検証と計測のためだけに false にする（{@code -Dubnfc.exclusive=false}）。
     * 短絡するのは fast mode（{@code diag=false}）だけなので、公開される観測は両経路で等しい。
     */
    public static boolean exclusive = !"false".equals(System.getProperty("ubnfc.exclusive"));
    /**
     * D-075（認識専用経路の終端が {@code Match} を作らない）の無効化スイッチ。既定は有効で、
     * 参照経路（成功のたびに区間付きの {@code Match} を作る経路）との差分検証と計測のためだけに
     * false にする（{@code -Dubnfc.plainmatch=false}）。返り値の同一性しか変わらないので、
     * 公開される観測は両経路で等しい。
     */
    public static boolean plainMatch = !"false".equals(System.getProperty("ubnfc.plainmatch"));
    /**
     * 認識専用経路の「成功したが情報を持たない」ことだけを表す共有実例（D-075）。
     * 列は全て空・{@code text} と {@code values} は null で、{@code start} / {@code end} は
     * 読まれない（読む側は {@link #plain} の条件で存在しないことが生成時に確かめてある）。
     */
    public static final Match OK = Match.empty(0, 0);
    /**
     * 除外した候補が記録したはずの観測の静的な再生計画（生成時に畳み込む。emit/Prediction 参照）。
     * allowed は「候補が成功し得る先頭 code point」に trivia 区切りの先頭 code point を足した閉区間列で、
     * 呼出し位置の文字がここに無ければ候補は最初の significant token で失敗し、trivia も 1 文字も進まない。
     */
    public static final class Guard {
        private final int[] allowed; private final Label[] labels; private final Delimiters trivia;
        final int[] suffix; final int maxRuleDepth;
        private int[] ids;
        public Guard(int[] allowed, Label[] labels, Delimiters trivia, int[] suffix, int maxRuleDepth) {
            this.allowed = allowed; this.labels = labels; this.trivia = trivia; this.suffix = suffix; this.maxRuleDepth = maxRuleDepth;
        }
        /** 入力終端（-1）は allowed に入らないので除外できる。 */
        boolean allows(int ch) {
            if (ch < 0) return false;
            for (int i = 0; i < allowed.length; i += 2) if (ch >= allowed[i] && ch <= allowed[i + 1]) return true;
            return false;
        }
        /** label id は Label 登録簿の初期化順に依存しないよう遅延解決する（null は trivia の group label）。 */
        int[] ids() {
            int[] result = ids;
            if (result == null) {
                result = new int[labels.length];
                for (int i = 0; i < labels.length; i++) result[i] = (labels[i] == null ? trivia.fullGroup() : labels[i]).id;
                ids = result;
            }
            return result;
        }
    }
    /** Trivia insertion plan of one sequence/repeat: null means no trivia at that boundary. */
    public static final class Trivia {
        public static final Trivia NONE = new Trivia(null, null, null, null, null);
        final Delimiters entry, helperEntry, helperExit, exit; final Delimiters[] afterChild;
        public Trivia(Delimiters entry, Delimiters helperEntry, Delimiters[] afterChild, Delimiters helperExit, Delimiters exit) {
            this.entry = entry; this.helperEntry = helperEntry; this.afterChild = afterChild; this.helperExit = helperExit; this.exit = exit;
        }
    }
    public record Field(String name, String[] sites, String conversion, String cardinality) {}
    /**
     * 規則ごとの field 表。名前配列と conversion / cardinality の判定は規則ごとに不変なので、
     * 生成コードが持つ定数の中で 1 度だけ畳む（rule 呼出しごとに配列を作り直さない）。
     */
    public static final class Fields {
        public static final Fields NONE = new Fields();
        final Field[] items; final String[] names; final boolean[] nodeLike, list, scalar;
        public Fields(Field... items) {
            this.items = items; names = new String[items.length];
            nodeLike = new boolean[items.length]; list = new boolean[items.length]; scalar = new boolean[items.length];
            for (int i = 0; i < items.length; i++) {
                Field field = items[i];
                names[i] = field.name();
                nodeLike[i] = field.conversion().equals("node") || field.conversion().equals("mixed");
                list[i] = field.cardinality().equals("list"); scalar[i] = field.cardinality().equals("scalar");
            }
        }
    }
    public record Effect(String when, String action, String site, String name, String mode) {}
    // memo payload は 1 parse あたり数百件になるので record を作らず 4 本の並列配列に置く。
    public final String source;
    public final Input input;
    public final ParseOptions options;
    public final ScopeStore scope = new ScopeStore();
    private Diagnostics diagnostics;
    private final MemoTable memo;
    private final Payloads payloads = Payloads.acquire();
    // 1 parse で数百件になるので、既定の 10 から育て直すのをやめる。
    private final ArrayList<TokenScanner.Effect> effects = new ArrayList<>(64);
    private final String[] ruleNames; private final Label[] ruleParserLabels;
    private int depth;
    private int[] activeRules = new int[64]; private int activeDepth;
    private int reached;
    private ParseResult<?> syntax;
    private long nextCaptureCompletion;
    public final IdentityHashMap<Object, Span> spans = new IdentityHashMap<>();
    private final IdentityHashMap<Recipe, Object> built = new IdentityHashMap<>();
    /**
     * D-056 の二段構え。{@code tree} が false の認識経路は Match 木・capture 出現・Recipe を作らず、
     * {@code diag} が false の経路は診断観測を記録しない（主位置 {@code reached} だけ残す）。
     * {@code twoMode} は生成側が決める文法ごとの可否（@recover を持つ文法や scope 効果が
     * 反復下の capture site を読む文法は完全経路だけ）。
     */
    public final boolean tree, diag, occ;
    /**
     * D-075: 認識専用経路の成功値が情報を持たなくてよい解析。{@code tree} が false で、かつ
     * 文法が {@code @scope} 効果を 1 つも持たない（生成側が {@code plainRecognition} で渡す）とき、
     * 終端も結合子も {@link #OK} 1 個を返す。この条件下で {@code Match} の区間を読む経路は
     * {@link #applyEffects}（scope 効果）と {@link #wrap} の scoped site だけで、どちらも存在しない。
     */
    public final boolean plain;
    /**
     * issue #35 / D-070: {@link #parse} が {@code DepthLimit} ではなく {@code StackOverflowError}
     * を捕まえたか（＝ {@code options.maxDepth()} にはまだ達していない、JVM stack がこの呼出しの
     * 上では小さいだけの失敗）。呼出し側はこれを見て、{@code maxDepth} から見積もった十分な
     * stack を持つ専用 {@link Thread} で 1 度だけ解析し直すかを決める。
     */
    public boolean stackOverflowTripped;
    /**
     * capture の包み（{@link #capture}）が何もしないことが確定する経路。生成側はこの旗が立つとき
     * 包みを飛ばして中身を直に呼ぶ（site を 1 つも公開せず、失敗 hint も記録しないので観測は同じ）。
     */
    public final boolean bypassCapture;
    /**
     * D-068: 公開出現表も診断も lexical trace も要求しない解析。読まれない capture site しか
     * 持たない式は、包みが意味値の射影だけになるので生成側が包みを飛ばせる。
     * {@code !tree} は {@code !occ} かつ {@code !lexical} を含むので、D-059 の条件を包含する。
     */
    public final boolean bypassWrap;
    /**
     * 左因数分解（D-061）の無効化スイッチ。既定は有効で、参照経路（候補ごとに接頭辞を評価し直す）
     * との差分検証と計測のためだけに false にする（system property {@code -Dubnfc.factor=false} でも切れる）。
     */
    public static boolean factoring = !"false".equals(System.getProperty("ubnfc.factor"));
    /** D-068 の A/B 用。{@code -Dubnfc.astwrap=false} で AST 経路だけ参照経路（包みを通る）に戻す。 */
    public static boolean astBypass = !"false".equals(System.getProperty("ubnfc.astwrap"));
    /**
     * この解析で左因数分解を使ってよいか。診断を記録する経路（主診断 DAG / display frame を組む）と
     * lexical trace を要求する経路では、呼ばれない wrapper の記録と共有要素の trace の付け替えが
     * 要るので参照経路のままにする。D-023 の二段構えにより、失敗・末尾入力は必ず診断モードで
     * 再解析されるので、公開される診断は参照経路のものと一致する。
     */
    public final boolean factor;
    public Session(CharSequence source, ParseOptions options, String[] ruleNames) { this(source, options, ruleNames, false, false); }
    public Session(CharSequence source, ParseOptions options, String[] ruleNames, boolean twoMode) { this(source, options, ruleNames, twoMode, false); }
    public Session(CharSequence source, ParseOptions options, String[] ruleNames, boolean twoMode, boolean plainRecognition) {
        this.tree = !twoMode || options.buildAst() || options.lexical() || options.occurrences();
        this.occ = !twoMode || options.occurrences();
        this.diag = !twoMode || options.diagnostics();
        this.bypassCapture = !this.tree && !this.diag;
        // 認識専用経路（!tree）の迂回は D-059 のまま常に有効。toggle は AST 経路だけを戻す。
        this.bypassWrap = !this.occ && !this.diag && !options.lexical() && (astBypass || !this.tree);
        this.factor = factoring && !this.diag && !options.lexical();
        this.plain = plainMatch && plainRecognition && !this.tree;
        this.source = source.toString(); this.input = new Input(this.source); this.options = options; this.ruleNames = ruleNames.clone();
        // diag=false の経路は診断を 1 件も記録しない（書込みは全て diag で囲ってある）ので、
        // 読み出し専用の空実例を共有する。実例 1 個で 1 parse あたり約 9 KB の確保が消える。
        diagnostics = diag ? new Diagnostics(Label.count(), 1024) : DISABLED;
        ruleParserLabels = new Label[ruleNames.length];
        // 観測: P4 で 1 文字あたり約 1.8 entry。load factor 0.5 の表は長さの 4 倍で足り、
        // 8 倍だと（complex で 128 KB・x64 で 8 MB）表が cache に対して無駄に広くなる。
        memo = new MemoTable(Math.max(1024, Math.min(1 << 20, input.length() * 4)));
    }
    /** 診断を要求しない解析が共有する空の診断（書込みは行わない）。 */
    private static final Diagnostics DISABLED = new Diagnostics(1, 16);
    /**
     * memo payload の並列配列。1 parse あたり数百件になるので record を作らず列で持ち、
     * 大きくなった配列は MemoTable と同じく thread local で次の解析へ持ち越す（中身は持ち越さない）。
     */
    private static final class Payloads {
        private static final ThreadLocal<Payloads> POOL = new ThreadLocal<>();
        Match[] match; boolean[] reset; List<TokenScanner.Effect>[] effects; Diagnostics.Snapshot[] diagnostics;
        int count;
        @SuppressWarnings({"unchecked", "rawtypes"}) private Payloads(int capacity) {
            match = new Match[capacity]; reset = new boolean[capacity];
            effects = new List[capacity]; diagnostics = new Diagnostics.Snapshot[capacity];
        }
        static Payloads acquire() {
            Payloads pooled = POOL.get();
            if (pooled == null) return new Payloads(256);
            POOL.remove(); return pooled;
        }
        int store(Match value, boolean resetFlag, List<TokenScanner.Effect> effect, Diagnostics.Snapshot snapshot) {
            if (count == match.length) {
                int n = count * 2;
                match = Arrays.copyOf(match, n); reset = Arrays.copyOf(reset, n);
                effects = Arrays.copyOf(effects, n); diagnostics = Arrays.copyOf(diagnostics, n);
            }
            int payload = count++;
            match[payload] = value; reset[payload] = resetFlag; effects[payload] = effect; diagnostics[payload] = snapshot;
            return payload;
        }
        /** 参照を捨てて thread local へ返す。返した後も（小さい配列で）正しく動く。 */
        void release() {
            Arrays.fill(match, 0, count, null); Arrays.fill(effects, 0, count, null);
            Arrays.fill(diagnostics, 0, count, null); count = 0;
            Payloads pooled = POOL.get();
            if (pooled == null || pooled.match.length < match.length) {
                var kept = new Payloads(0);
                kept.match = match; kept.reset = reset; kept.effects = effects; kept.diagnostics = diagnostics;
                POOL.set(kept);
            }
            var replacement = new Payloads(16);
            match = replacement.match; reset = replacement.reset;
            effects = replacement.effects; diagnostics = replacement.diagnostics;
        }
    }
    private static final class DepthLimit extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private DepthLimit() { super(null, null, false, false); }
    }
    /**
     * issue #35 / D-070: {@code stackOverflowTripped} を見た呼出し側が、エスカレーション先の
     * {@link Thread} に確保する stack size（byte）。実測（JSON 入れ子で 1 段あたり約 5.6 KiB、
     * docs/reports/2026-09-24-runtime-limits.md）に安全率を見て 1 深さ単位 8 KiB とし、
     * {@code maxDepth} を掛けた上で下限 8 MiB・上限 512 MiB に丸める（Rust 版
     * {@code crates/ubnfc-rust/templates/runtime.rs} の `escalated_thread_stack_bytes` と対）。
     */
    public static long escalatedStackBytes(int maxDepth) {
        long budget = (long) maxDepth * 8192L + 2L * 1024 * 1024;
        return Math.max(8L * 1024 * 1024, Math.min(budget, 512L * 1024 * 1024));
    }
    /** Abort the entire parse: an outer optional/choice must not swallow a resource limit. */
    public Match parse(Call entry, Frame f) {
        long start = mark(); int c0 = f.c, m0 = f.m; boolean reset0 = f.reset;
        try { return entry.parse(this, f); }
        catch (DepthLimit | StackOverflowError limit) {
            restore(start); f.c = c0; f.m = m0; f.reset = reset0;
            if (diag) diagnostics = new Diagnostics(Label.count(), 256);
            String reason = limit instanceof StackOverflowError ? "JVM stack limit; maxDepth=" : "maxDepth=";
            if (limit instanceof StackOverflowError) stackOverflowTripped = true;
            fail(reached, "maximum parse depth exceeded (" + reason + options.maxDepth() + ")");
            return null;
        } finally {
            // memo 表と診断 event log はここから先は読まれない（result() は global の集約だけを見る）。
            // 大きな配列を thread local の pool へ返し、次の解析で中身を捨てて使い回す。
            memo.release(); payloads.release();
        }
    }
    public int cp(int at) { return at < input.length() ? input.codePointAt(at) : -1; }
    public Span span(int start, int end) { return new Span(input.cpIndex(start), input.cpIndex(end)); }
    /** 到達位置の更新。capture の包みを飛ばす生成コードが同じ位置で呼ぶので public。 */
    public void progress(Frame f) {
        int at = Math.max(f.c, f.m); if (at > reached) reached = at; if (diag) diagnostics.reach(at);
    }
    private void progressAt(int at) { if (at > reached) reached = at; if (diag) diagnostics.reach(at); }
    /** 成功の返り値。認識専用経路（{@link #plain}）では区間が読まれないので共有実例を返す。 */
    private Match success(int start, int end) { return plain ? OK : Match.empty(start, end); }
    private void fail(int at, String label) { if (!diag) { if (at > reached) reached = at; return; } fail(at, Label.of(label).id, at, at); }
    private void fail(int at, Label label) { fail(at, label.id, at, at); }
    private void fail(Frame frame, Label label) { fail(Math.max(frame.c, frame.m), label.id, frame.c, frame.m); }
    private void fail(int at, int id, int consumed, int matched) {
        if (diag) diagnostics.failAt(at, id, consumed, matched);
        if (at > reached) reached = at;
    }
    private long mark() { return ((long) scope.checkpoint() << 32) | effects.size(); }
    /** 印から何も増えていない巻戻しが大多数（P4 complex で 2,070 回中 1,930 回）なので、判定だけ inline させる。 */
    private void restore(long mark) {
        if (mark != mark()) undo(mark);
    }
    private void undo(long mark) {
        int checkpoint = (int) (mark >>> 32);
        if (checkpoint != scope.checkpoint()) scope.restore(checkpoint);
        int size = (int) mark;
        // subList(...).clear() は SubList を 1 個確保する。取消しは 1 parse あたり数千回なので末尾から外す。
        for (int i = effects.size(); i > size; i--) effects.remove(i - 1);
    }
    /**
     * memo payload の効果列。{@code List.copyOf(subList)} は SubList と複製の 2 個を作るので配列 1 本にする。
     * 空のときは null を返す（1 parse の memo hit 464 件のうち効果を持つのは数件なので、再生の呼出しごと省く）。
     */
    private List<TokenScanner.Effect> effectsSince(long mark) {
        int size = (int) mark, n = effects.size();
        if (n == size) return null;
        var copy = new TokenScanner.Effect[n - size];
        for (int i = 0; i < copy.length; i++) copy[i] = effects.get(size + i);
        return Arrays.asList(copy);
    }
    /** null は「効果なし」。呼出し側が毎回 null 検査をするより、ここで畳んで inline させる。 */
    private void replay(List<TokenScanner.Effect> replay) {
        if (replay == null) return;
        replayAll(replay);
    }
    private void replayAll(List<TokenScanner.Effect> replay) { for (int i = 0, n = replay.size(); i < n; i++) effect(replay.get(i)); }
    private void effect(TokenScanner.Effect effect) {
        effects.add(effect);
        switch (effect.action()) {
            case "enter" -> scope.enter(); case "leave" -> scope.leave(); case "clear" -> scope.clearDiagnostics();
            case "declare" -> scope.declare(effect.name(), effect.offsetCp());
            case "use" -> {
                scope.addReference(effect.name(), effect.offsetCp(), effect.lengthCp());
                if (!scope.isDeclared(effect.name())) scope.addDiagnostic("未定義のシンボル: '" + effect.name() + "'", effect.offsetCp(), effect.lengthCp(), Severity.WARNING);
            }
            case "error" -> scope.addDiagnostic(effect.name(), effect.offsetCp(), effect.lengthCp(), Severity.ERROR);
            default -> throw new IllegalArgumentException("Unsupported scanner effect: " + effect.action());
        }
    }
    public Match rule(Frame f, int id, int memoId, boolean successMemo, boolean failureMemo, Call body,
                      String recipe, String fallback, String recipeKind, Fields fields, Effect[] scopeEffects, boolean skip, Label[] recoveryHints, boolean rightAssociative, Label[] failureHints) {
        int c = f.c, m = f.m, state = scope.stateVersion();
        int mode = (f.matched ? 1 : 0) | (f.invert ? 2 : 0) | (f.reset ? 4 : 0);
        boolean cache = options.memo() && (successMemo || failureMemo);
        if (cache) {
            int slot = memo.get(memoId, c, m, mode, state);
            if (slot >= 0) {
                int payload = memo.payload(slot);
                if (diag) diagnostics.merge(payloads.diagnostics[payload]);
                replay(payloads.effects[payload]);
                f.c = memo.consumedEnd(slot); f.m = memo.matchedEnd(slot); f.reset = payloads.reset[payload];
                return payloads.match[payload];
            }
        }
        progress(f);
        if (depth >= options.maxDepth()) throw new DepthLimit();
        depth++;
        // @rightAssoc の span 補正は「外側の rule が末尾 trivia を消費する」
        // 入れ子呼出しだけに掛ける（Rust emit.rs と同一条件）。entry 規則は補正しない。
        boolean nested = depth > 1;
        if (activeDepth == activeRules.length) activeRules = Arrays.copyOf(activeRules, activeDepth * 2);
        activeRules[activeDepth++] = id;
        long mark = mark(); if (diag) diagnostics.enterRule(id); progress(f);
        if (scopeEffects.length > 0) applyEffects(scopeEffects, "entry", Match.empty(c, c));
        Match result = body.parse(this, f);
        if (result != null) {
            if (scopeEffects.length > 0) {
                applyEffects(scopeEffects, "successBeforeLeave", result);
                applyEffects(scopeEffects, "successAfterLeave", result);
                applyEffects(scopeEffects, "afterDeclarations", result);
            }
            List<Recipe> nodes = result.nodes();
            boolean skipped = skip || "skip".equals(recipeKind);
            if (!tree) nodes = List.of();
            else if (skipped) nodes = List.of();
            else if (recipe != null && !"transparent".equals(recipeKind) && !"text".equals(recipeKind)) {
                Field[] items = fields.items; List<Value>[] lists = Recipe.lists(items.length);
                List<Occurrence> local = result.local();
                int localCount = local.size();
                for (int fi = 0; fi < items.length; fi++) {
                    String[] sites = items[fi].sites();
                    boolean nodeLike = fields.nodeLike[fi], list = fields.list[fi], scalar = fields.scalar[fi];
                    // 1 値しか集まらない field が多数派なので、2 件目が出るまで list を確保しない。
                    Value single = null; ArrayList<Value> captures = null;
                    for (int k = 0; k < localCount; k++) { Occurrence occurrence = local.get(k); if (!contains(sites, occurrence.site())) continue;
                        Value v = occurrence.value();
                        if (nodeLike && !scalar && v.elements() != null) {
                            List<Value> elements = v.elements();
                            if (elements.isEmpty()) continue;
                            if (captures == null) { captures = new ArrayList<>(elements.size() + 1); if (single != null) { captures.add(single); single = null; } }
                            captures.addAll(elements);
                        } else if (nodeLike && list && !v.nodes().isEmpty()) {
                            List<Recipe> children = v.nodes();
                            if (captures == null && children.size() == 1 && single == null) {
                                Recipe node = children.getFirst(); single = new Value(node.start(), node.end(), children);
                                continue;
                            }
                            if (captures == null) { captures = new ArrayList<>(children.size() + 1); if (single != null) { captures.add(single); single = null; } }
                            for (int ni = 0, nn = children.size(); ni < nn; ni++) { Recipe node = children.get(ni); captures.add(new Value(node.start(), node.end(), List.of(node))); }
                        } else if (!(nodeLike && list && v.start() == v.end() && v.nodes().isEmpty())) {
                            if (captures != null) captures.add(v);
                            else if (single == null) single = v;
                            else { captures = new ArrayList<>(); captures.add(single); captures.add(v); single = null; }
                        }
                    }
                    lists[fi] = captures != null ? List.copyOf(captures) : single == null ? List.of() : List.of(single);
                }
                int nodeEnd = f.c;
                if (rightAssociative && nested) {
                    int max = Integer.MIN_VALUE;
                    for (List<Value> list : lists) for (int k = 0; k < list.size(); k++) max = Math.max(max, list.get(k).end());
                    if (max != Integer.MIN_VALUE) nodeEnd = max;
                }
                nodes = List.of(new Recipe(recipe, c, nodeEnd, fields.names, lists, fallback));
            }
            List<Occurrence> completed = result.local();
            for (int k = 0, n = completed.size(); k < n; k++) completed.get(k).completed = nextCaptureCompletion++;
            if (!tree) result = success(c, f.c);
            else {
                List<Value> values = result.values();
                if (skipped || nodes != result.nodes()) {
                    values = nodes.isEmpty() ? List.of() : List.of(new Value(nodes.getFirst().start(), nodes.getFirst().end(), nodes));
                }
                result = new Match(nodes, List.of(), result.captures(), result.traces(), c, f.c,
                    result.mappingFailure(), result.recoveries(), List.of(), result.text(), values);
            }
        } else {
            if (diag) {
                int localFarthest = diagnostics.localFarthest();
                if (localFarthest == Math.max(c, m)) for (Label hint : recoveryHints) fail(localFarthest, hint);
                for (Label hint : failureHints) fail(Math.max(c, m), hint);
            }
            restore(mark); f.c = c; f.m = m;
        }
        depth--; activeDepth--;
        boolean store = cache && (result != null ? successMemo : failureMemo);
        var diagnostic = diag ? diagnostics.leaveRule(store) : null;
        if (store) {
            int payload = payloads.store(result, f.reset, effectsSince(mark), diagnostic);
            memo.put(memoId, c, m, mode, state, result != null, f.c, f.m, payload);
        }
        return result;
    }
    private static boolean contains(String[] sites, String site) {
        for (String candidate : sites) if (candidate == site || candidate.equals(site)) return true;
        return false;
    }
    public Match expression(Frame f, int id, boolean successMemo, boolean failureMemo, Call body) {
        if (!options.memo() || !successMemo && !failureMemo) return body.parse(this, f);
        int c = f.c, m = f.m, state = scope.stateVersion();
        int mode = (f.matched ? 1 : 0) | (f.invert ? 2 : 0) | (f.reset ? 4 : 0);
        int slot = memo.get(id, c, m, mode, state);
        if (slot >= 0) {
            int payload = memo.payload(slot);
            if (diag) diagnostics.merge(payloads.diagnostics[payload]);
            replay(payloads.effects[payload]);
            f.c = memo.consumedEnd(slot); f.m = memo.matchedEnd(slot); f.reset = payloads.reset[payload];
            return payloads.match[payload];
        }
        long mark = mark(); if (diag) diagnostics.enterExpression(); progress(f);
        Match result = body.parse(this, f);
        boolean store = result != null ? successMemo : failureMemo;
        var diagnostic = diag ? diagnostics.leaveRule(store) : null;
        if (store) {
            int payload = payloads.store(result, f.reset, effectsSince(mark), diagnostic);
            memo.put(id, c, m, mode, state, result != null, f.c, f.m, payload);
        }
        return result;
    }
    private void applyEffects(Effect[] definitions, String when, Match result) {
        HashMap<String, String> declared = null;
        for (Effect definition : definitions) {
            if (!definition.when().equals(when)) continue;
            if (definition.site() == null) {
                // D-029: 名前を持たない declare / use は記号を指さないので記録しない。
                if ((definition.action().equals("declare") || definition.action().equals("use"))
                    && (definition.name() == null || definition.name().isEmpty())) continue;
                effect(new TokenScanner.Effect(definition.action(), definition.name(), result == null ? 0 : input.cpIndex(result.end()), 0, definition.mode())); continue;
            }
            if (result == null) continue;
            List<Occurrence> locals = result.local();
            for (int li = 0, ln = locals.size(); li < ln; li++) { Occurrence capture = locals.get(li); if (!capture.site().equals(definition.site())) continue;
                Value v = capture.value();
                int trimmedStart = v.start(), trimmedEnd = v.end();
                while (trimmedStart < trimmedEnd && source.charAt(trimmedStart) <= 0x20) trimmedStart++;
                while (trimmedEnd > trimmedStart && source.charAt(trimmedEnd - 1) <= 0x20) trimmedEnd--;
                String name = source.substring(trimmedStart, trimmedEnd);
                if (name.isEmpty()) continue;
                if (definition.mode().equals("sameRuleText")) {
                    String key = definition.name() == null ? capture.name() : definition.name();
                    // Declarations can belong to an earlier phase; inspect the selected local captures.
                    String target = declared == null ? null : declared.get(key);
                    for (int oi = 0; oi < ln; oi++) { Occurrence own = locals.get(oi); if (!own.name().equals(key)) continue;
                        target = source.substring(own.value().start(), own.value().end()).trim(); break;
                    }
                    if (definition.action().equals("declare")) { if (declared == null) declared = new HashMap<>(); declared.put(key, name); }
                    else if (definition.action().equals("use") && target != null && !name.equals(target))
                        effect(new TokenScanner.Effect("error", "back-reference mismatch: expected '" + target + "' but got '" + name + "'",
                            input.cpIndex(trimmedStart), input.cpLength(trimmedStart, trimmedEnd)));
                } else effect(new TokenScanner.Effect(definition.action(), name, input.cpIndex(trimmedStart), input.cpLength(trimmedStart, trimmedEnd), definition.mode()));
            }
        }
    }
    public Match capture(Frame f, Call call, String[] sites, String[] names, String[] spanRules, boolean[] skips, boolean[] scoped,
                         String rule, String expr, boolean quantified, boolean terminal, String semanticKind, boolean semanticMany, Label[] failureHints) {
        int c = f.c, m = f.m; Match result = call.parse(this, f);
        progress(f);
        return wrap(f, c, m, result, sites, names, spanRules, skips, scoped, rule, expr, quantified, terminal, semanticKind, semanticMany, failureHints);
    }
    /** {@link #capture} の「包む側」だけ。左因数分解（D-061）は中身を自分で評価してからここへ渡す。 */
    private Match wrap(Frame f, int c, int m, Match result, String[] sites, String[] names, String[] spanRules, boolean[] skips, boolean[] scoped,
                       String rule, String expr, boolean quantified, boolean terminal, String semanticKind, boolean semanticMany, Label[] failureHints) {
        if (result == null) {
            if (diag) for (Label hint : failureHints) fail(Math.max(c, m), hint);
            return null;
        }
        if (!tree) {
            // 認識経路は出現表を公開しない（D-035 の Java 版）。scope 効果が site を読む
            // capture だけ局所出現として残す（生成側の scoped がその集合）。
            boolean needed = false;
            for (int i = 0; i < scoped.length; i++) if (scoped[i] && !skips[i]) { needed = true; break; }
            if (!needed) return result;
        }
        List<Value> values = tree ? projected(result, semanticKind, semanticMany) : result.values();
        if (quantified && sites.length > 0) {
            var items = new ArrayList<Match>();
            for (Match item : result.items()) {
                Match selected = item;
                if (selected.items().size() == 1) selected = selected.items().getFirst();
                String text = selected.text() == null ? source.substring(selected.start(), selected.end()).strip() : selected.text();
                var value = new Match(item.nodes(), item.local(), item.captures(), item.traces(), item.start(), item.end(),
                    item.mappingFailure(), item.recoveries(), item.items(), text, item.values());
                for (int i = 0; i < sites.length; i++) {
                    if (!tree && !scoped[i]) continue;
                    value = captured(value, item.start(), item.end(), sites[i], names[i], skips[i]);
                }
                items.add(value);
            }
            Match joined = combine(items, result.start(), result.end());
            return new Match(result.nodes(), joined.local(), joined.captures(), result.traces(), result.start(), result.end(),
                result.mappingFailure(), result.recoveries(), items, result.text(), values);
        }
        // 出現を site ごとに Match へ包み直すと 1 site につき 1 個の Match を捨てることになる。
        // nodes / text / values は出現を足しても変わらないので、列だけ足して最後に 1 個だけ作る。
        List<Occurrence> local = result.local(), captures = result.captures();
        for (int i = 0; i < sites.length; i++) {
            if (!tree && !scoped[i] || skips[i]) continue;
            int start = spanRules[i].equals("matchedExtent") ? m : spanRules[i].equals("selectedChildExtent") ? result.start() : c;
            int end = spanRules[i].equals("matchedExtent") ? f.m : spanRules[i].equals("selectedChildExtent") ? result.end() : f.c;
            var occurrence = new Occurrence(sites[i], names[i], new Value(start, Math.max(start, end), result.nodes(), result.text(), values));
            local = appended(local, occurrence);
            if (occ) captures = appended(captures, occurrence);
        }
        if (!options.lexical()) return local == result.local() && values == result.values() ? result
            : new Match(result.nodes(), local, captures, result.traces(), result.start(), result.end(),
                result.mappingFailure(), result.recoveries(), result.items(), result.text(), values);
        List<Trace> traces = List.of(new Trace(rule, expr, c, f.c, result.traces(), terminal ? source.substring(result.start(), result.end()) : null));
        return new Match(result.nodes(), local, captures, traces, result.start(), result.end(),
            result.mappingFailure(), result.recoveries(), result.items(), result.text(), values);
    }
    /**
     * {@link #wrap} の意味値射影だけを行う（D-068）。読まれる site を持たない式の迂回で使う。
     * {@code projected} と同じ結果を返し、値列が変わらなければ {@code Match} を作らない。
     */
    public Match project(Match result, boolean mixedScalar) {
        if (result == null || !tree) return result;
        List<Value> values = result.values();
        if (values == null) values = List.of(new Value(result.start(), result.end(), result.nodes(), result.text()));
        if (mixedScalar && !values.isEmpty() && allLeaves(values))
            values = List.of(new Value(result.start(), result.end(), List.of(), result.text()));
        if (values == result.values()) return result;
        return new Match(result.nodes(), result.local(), result.captures(), result.traces(), result.start(), result.end(),
            result.mappingFailure(), result.recoveries(), result.items(), result.text(), values);
    }
    /**
     * D-078: 意味値を持つ選択の text だけの候補。values を plain text（null）に戻し、選択の射影が
     * 一致範囲の字句 1 つを値にする。内側の空反復の「値なし」（空の values）で字句を消さない。
     */
    public Match textAlternative(Match result) {
        if (result == null || !tree || result.values() == null) return result;
        return new Match(result.nodes(), result.local(), result.captures(), result.traces(), result.start(), result.end(),
            result.mappingFailure(), result.recoveries(), result.items(), result.text(), null);
    }
    private Match captured(Match result, int start, int end, String site, String name, boolean skip) {
        if (skip) return result;
        var occurrence = new Occurrence(site, name, new Value(start, end, result.nodes(), result.text(), result.values()));
        return new Match(result.nodes(), appended(result.local(), occurrence),
            occ ? appended(result.captures(), occurrence) : result.captures(), result.traces(), result.start(), result.end(),
            result.mappingFailure(), result.recoveries(), result.items(), result.text(), result.values());
    }
    /** {@code List.of(Object[])} は配列をもう一度複製するので、確保済みの配列をそのまま包む。 */
    private static List<Occurrence> appended(List<Occurrence> list, Occurrence occurrence) {
        if (list.isEmpty()) return List.of(occurrence);
        var copy = new Occurrence[list.size() + 1]; list.toArray(copy); copy[list.size()] = occurrence; return Arrays.asList(copy);
    }
    /** 意味値の射影。Match を包み直さず値列だけ返し、呼出し側が出現の追加と 1 度にまとめる。 */
    private static List<Value> projected(Match result, String kind, boolean many) {
        if (kind.equals("text")) return result.values();
        if (kind.equals("textAlternative")) return null;
        List<Value> values = result.values();
        if (values == null) values = List.of(new Value(result.start(), result.end(), result.nodes(), result.text()));
        if (kind.equals("mixed") && !many && !values.isEmpty() && allLeaves(values))
            values = List.of(new Value(result.start(), result.end(), List.of(), result.text()));
        return values;
    }
    private static boolean allLeaves(List<Value> values) {
        for (int k = 0, n = values.size(); k < n; k++) if (!values.get(k).nodes().isEmpty()) return false;
        return true;
    }
    /** 認識経路の局所出現だけを足し込む（列の畳み込みをしない）。 */
    private static ArrayList<Occurrence> keep(ArrayList<Occurrence> accumulator, Match item) {
        List<Occurrence> local = item.local();
        if (local.isEmpty()) return accumulator;
        if (accumulator == null) accumulator = new ArrayList<>(local.size() + 2);
        accumulator.addAll(local); return accumulator;
    }
    private Match fast(ArrayList<Occurrence> local, int start, int end) {
        return local == null ? success(start, end)
            : new Match(List.of(), local, List.of(), List.of(), start, end);
    }
    private Match combine(List<Match> items, int start, int end) {
        if (!tree) {
            int localN = 0;
            for (int k = 0, n = items.size(); k < n; k++) localN += items.get(k).local().size();
            return localN == 0 ? success(start, end)
                : new Match(List.of(), join(items, LOCAL, localN), List.of(), List.of(), start, end);
        }
        if (items.size() == 1) {
            Match only = items.getFirst();
            return new Match(only.nodes(), only.local(), only.captures(), only.traces(), start, end, only.mappingFailure(), only.recoveries(), items, only.text(), only.values());
        }
        // 1 回目の走査で各列の合計要素数だけを数え、2 回目で合計長の list を 1 個だけ確保する。
        // 逐次 concat は k 番目までの合計を毎回複製するので、要素数 T・項目数 n に対して O(T*n)
        // （512 atom の Operators で 1 parse あたり 8.9 MB）になっていた。合計を先に数えれば O(T)。
        int nodesN = 0, localN = 0, capturesN = 0, tracesN = 0, recoveriesN = 0, valuesN = 0;
        boolean anyValues = false, failure = false;
        for (int k = 0, n = items.size(); k < n; k++) {
            Match item = items.get(k);
            nodesN += item.nodes().size(); localN += item.local().size(); capturesN += item.captures().size();
            tracesN += item.traces().size(); recoveriesN += item.recoveries().size();
            List<Value> itemValues = item.values();
            if (itemValues != null) { anyValues = true; valuesN += itemValues.size(); }
            failure |= item.mappingFailure();
        }
        List<Recipe> nodes = join(items, NODES, nodesN);
        List<Occurrence> local = join(items, LOCAL, localN), captures = join(items, CAPTURES, capturesN);
        List<Trace> traces = join(items, TRACES, tracesN);
        List<RecoveryEvent> recoveries = join(items, RECOVERIES, recoveriesN);
        List<Value> values = anyValues ? join(items, VALUES, valuesN) : null;
        return new Match(nodes, local, captures, traces, start, end, failure, recoveries, items, null, values);
    }
    private static final int NODES = 0, LOCAL = 1, CAPTURES = 2, TRACES = 3, RECOVERIES = 4, VALUES = 5;
    private static List<?> column(Match item, int column) {
        return switch (column) {
            case NODES -> item.nodes(); case LOCAL -> item.local(); case CAPTURES -> item.captures();
            case TRACES -> item.traces(); case RECOVERIES -> item.recoveries(); default -> item.values();
        };
    }
    /**
     * 1 列ぶんの多項連結。合計 {@code total} は呼出し側が数えてあるので確保は最大 1 回で、
     * 寄与する項目が 1 つだけなら（従来の 2 項 concat と同じく）その list をそのまま返す。
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> join(List<Match> items, int column, int total) {
        if (total == 0) return List.of();
        ArrayList<T> merged = null;
        for (int k = 0, n = items.size(); k < n; k++) {
            List<T> part = (List<T>) column(items.get(k), column);
            if (part == null || part.isEmpty()) continue;
            if (merged == null) { if (part.size() == total) return part; merged = new ArrayList<>(total); }
            merged.addAll(part);
        }
        return merged;
    }
    // Transactions run on the caller's frame in place: begin saves (c, m, reset) and applies
    // "m := c when reset"; failure restores c/m; either outcome restores reset (a commit never
    // copied the child's reset flag). Observable cursor behaviour equals the frame-copy version.
    public Match sequence(Frame f, Call[] children, Trivia trivia) {
        long mark = mark(); int c0 = f.c, m0 = f.m; boolean reset0 = f.reset;
        if (reset0) f.m = f.c;
        int start = f.c;
        if (trivia.entry != null) trivia(f, trivia.entry); if (trivia.helperEntry != null) trivia(f, trivia.helperEntry);
        if (!tree) {
            ArrayList<Occurrence> local = null;
            for (int i = 0; i < children.length; i++) {
                Match result = children[i].parse(this, f);
                if (result == null) { restore(mark); f.c = c0; f.m = m0; f.reset = reset0; return null; }
                local = keep(local, result);
                if (trivia.afterChild != null && trivia.afterChild[i] != null) trivia(f, trivia.afterChild[i]);
            }
            if (trivia.helperExit != null) trivia(f, trivia.helperExit); if (trivia.exit != null) trivia(f, trivia.exit);
            f.reset = reset0; return fast(local, start, f.c);
        }
        if (children.length == 0) {
            if (trivia.helperExit != null) trivia(f, trivia.helperExit); if (trivia.exit != null) trivia(f, trivia.exit);
            f.reset = reset0; return Match.empty(start, f.c);
        }
        // 失敗する連接の多数は最初の子で落ちる（1 parse の 759 件中 503 件）。
        // 子の配列は最初の子が通ってから確保する。
        Match head = children[0].parse(this, f);
        if (head == null) { restore(mark); f.c = c0; f.m = m0; f.reset = reset0; return null; }
        if (trivia.afterChild != null && trivia.afterChild[0] != null) trivia(f, trivia.afterChild[0]);
        var matches = new Match[children.length];
        matches[0] = head;
        for (int i = 1; i < children.length; i++) {
            Match result = children[i].parse(this, f);
            if (result == null) { restore(mark); f.c = c0; f.m = m0; f.reset = reset0; return null; }
            matches[i] = result; if (trivia.afterChild != null && trivia.afterChild[i] != null) trivia(f, trivia.afterChild[i]);
        }
        if (trivia.helperExit != null) trivia(f, trivia.helperExit); if (trivia.exit != null) trivia(f, trivia.exit);
        f.reset = reset0; return combine(Arrays.asList(matches), start, f.c);
    }
    /**
     * D-066（Rust backend の {@code expressions.rs::predict_literals} の Java 版）。front が
     * {@code exclusiveCandidates} を証明した選択——候補が全て非 nullable・trivia なし・capture なしの
     * 単一 code point の case-sensitive literal で、先頭文字が EOF 込みで互いに素——は、呼出し位置の
     * 文字がどの候補の先頭でもない時点で全候補の失敗が確定する。元順で全部呼んでも、各候補は入口の
     * literal で失敗して 0 文字消費するだけなので、fast mode（{@code diag=false}）がその走行から
     * 残すのは到達位置 {@code reached} の更新だけで、それは {@link #exclusiveFail} が同じ位置へ入れる。
     *
     * <p>短絡してよい条件を「否定先読みの中でない」「記録位置と読み取り位置が一致する
     * （{@code f.reset || f.c == f.m}）」に限るのは候補除外（{@link Guard}）と同じで、これにより
     * 候補が literal を読む位置が {@code f.c} に確定する。診断モードは条件を満たさないので従来どおり
     * 元順で全候補を走らせる（観測は構成上一致）。{@code -Dubnfc.exclusive=false} で参照経路へ戻る。
     */
    public boolean exclusiveReady(Frame f) {
        return exclusive && !diag && !f.invert && (f.reset || f.c == f.m);
    }
    /** 排他 predict が外れた選択の失敗。全候補が入口で失敗したときと同じ到達位置だけを残す。 */
    public Match exclusiveFail(Frame f) { progressAt(f.c); return null; }
    public Match choice(Frame f, Call[] alternatives, boolean longest, boolean[] eligible, boolean fallback) {
        return choice(f, alternatives, longest, eligible, fallback, null);
    }
    public Match choice(Frame f, Call[] alternatives, boolean longest, boolean[] eligible, boolean fallback, Guard[] guards) {
        Match best = null; int bestC = 0, bestM = 0; List<TokenScanner.Effect> bestEffects = null;
        int c0 = f.c, m0 = f.m; boolean reset0 = f.reset;
        // 候補除外は「記録位置 max(c,m) と読み取り位置 position() が一致し、否定先読みの中でない」ときだけ。
        int probe = predict && guards != null && !f.invert && (reset0 || c0 == m0) ? c0 : -1;
        int probed = probe < 0 ? -2 : cp(probe);
        for (int pass = 0; pass < (fallback ? 2 : 1); pass++) {
            for (int i = 0; i < alternatives.length; i++) {
                if (pass == 0 && eligible != null && !eligible[i]) continue;
                if (probe >= 0 && guards[i] != null && !guards[i].allows(probed)
                    && depth + guards[i].maxRuleDepth < options.maxDepth()) {
                    Guard guard = guards[i];
                    progressAt(probe);
                    if (diag) diagnostics.failBatch(probe, guard.ids(), guard.suffix, probe, probe);
                    continue;
                }
                long mark = mark(); f.c = c0; f.m = reset0 ? c0 : m0; f.reset = reset0;
                Match result = alternatives[i].parse(this, f);
                if (result != null && !longest) { f.reset = reset0; return result; }
                if (result != null && (best == null || f.c > bestC)) {
                    best = result; bestC = f.c; bestM = f.m; bestEffects = effectsSince(mark);
                }
                restore(mark);
            }
            if (best != null) break;
        }
        f.reset = reset0;
        if (best != null) { f.c = bestC; f.m = bestM; replay(bestEffects); } else { f.c = c0; f.m = m0; }
        return best;
    }
    /**
     * 左因数分解（D-061。`crates/ubnfc-rust/src/factor.rs` の Java 版）。順序付き選択の連続候補で
     * 先頭要素列が構造的に等しいもの（`X A | X B`）を、共有接頭辞 1 回の評価で走らせる。
     * PEG の意味論では X は同じ状態で同じ結果を返す（memo が保証する決定性）ので
     * `X A | X B ≡ X (A | B)`。参照経路は {@code -Dubnfc.factor=false} で戻せる。
     */
    public static final class Cap {
        final String[] sites, names, spanRules; final boolean[] skips, scoped;
        final String rule, expr, semanticKind; final boolean quantified, terminal, semanticMany, bypass;
        final boolean treeBypass, textKind, textAlternative, mixedScalar;
        final Label[] failureHints;
        public Cap(String[] sites, String[] names, String[] spanRules, boolean[] skips, boolean[] scoped, String rule, String expr,
                   boolean quantified, boolean terminal, String semanticKind, boolean semanticMany, Label[] failureHints,
                   boolean bypass, boolean treeBypass) {
            this.sites = sites; this.names = names; this.spanRules = spanRules; this.skips = skips; this.scoped = scoped;
            this.rule = rule; this.expr = expr; this.quantified = quantified; this.terminal = terminal;
            this.semanticKind = semanticKind; this.semanticMany = semanticMany; this.failureHints = failureHints; this.bypass = bypass;
            this.treeBypass = treeBypass; this.textKind = semanticKind.equals("text");
            this.textAlternative = semanticKind.equals("textAlternative");
            this.mixedScalar = semanticKind.equals("mixed") && !semanticMany;
        }
    }
    /** 群の 1 段: 共有要素を 1 回評価してから、次の段の枝を宣言順に試す。 */
    public static final class Level {
        final Call element; final Trivia trivia; final int depth; final Branch[] branches;
        public Level(Call element, Trivia trivia, int depth, Branch... branches) {
            this.element = element; this.trivia = trivia; this.depth = depth; this.branches = branches;
        }
    }
    /** 枝: さらに共有する段（{@code level}）か、単独候補の残り（{@code rest} 以下）。 */
    public static final class Branch {
        final Level level; final Call[] rest; final Trivia trivia; final int from; final Cap cap; final String[] retag;
        public Branch(Level level) { this(level, null, null, 0, null, null); }
        public Branch(Call[] rest, Trivia trivia, int from, Cap cap, String[] retag) { this(null, rest, trivia, from, cap, retag); }
        private Branch(Level level, Call[] rest, Trivia trivia, int from, Cap cap, String[] retag) {
            this.level = level; this.rest = rest; this.trivia = trivia; this.from = from; this.cap = cap; this.retag = retag;
        }
    }
    /** 先頭要素を共有する連続候補の区間 [start, end)（候補 index は選択の宣言順）。 */
    public static final class Group {
        final int start, end, width; final boolean setFalse; final Trivia trivia; final Level root;
        public Group(int start, int end, boolean setFalse, Trivia trivia, int width, Level root) {
            this.start = start; this.end = end; this.setFalse = setFalse; this.trivia = trivia; this.width = width; this.root = root;
        }
    }
    /** 群 1 つを走らせる。候補 Seq の入口（transaction / 入口 trivia）は群で共通。 */
    private Match factored(Frame f, Group g) {
        int c = f.c, m = f.m;
        if (g.setFalse) f.reset = false;
        long mark = mark(); int c0 = f.c, m0 = f.m; boolean reset0 = f.reset;
        if (reset0) f.m = f.c;
        int start = f.c;
        Trivia trivia = g.trivia;
        if (trivia.entry != null) trivia(f, trivia.entry); if (trivia.helperEntry != null) trivia(f, trivia.helperEntry);
        Match result = level(f, g.root, new Match[g.width], start, reset0, c, m);
        if (result == null) { restore(mark); f.c = c0; f.m = m0; f.reset = reset0; }
        return result;
    }
    private Match level(Frame f, Level lv, Match[] buf, int start, boolean reset0, int c, int m) {
        Match head = lv.element.parse(this, f);
        if (head == null) return null;
        buf[lv.depth] = head;
        Delimiters[] after = lv.trivia.afterChild;
        if (after != null && after[lv.depth] != null) trivia(f, after[lv.depth]);
        Branch[] branches = lv.branches;
        long mark = mark(); int bc = f.c, bm = f.m; boolean reset = f.reset;
        for (int i = 0; i < branches.length; i++) {
            if (i > 0) { restore(mark); f.c = bc; f.m = bm; f.reset = reset; }
            Branch branch = branches[i];
            Match result = branch.level != null ? level(f, branch.level, buf, start, reset0, c, m)
                : tail(f, branch, buf, start, reset0, c, m);
            if (result != null) return result;
        }
        return null;
    }
    private Match tail(Frame f, Branch b, Match[] buf, int start, boolean reset0, int c, int m) {
        Call[] rest = b.rest; Delimiters[] after = b.trivia.afterChild;
        for (int i = 0; i < rest.length; i++) {
            Match result = rest[i].parse(this, f);
            if (result == null) return null;
            buf[b.from + i] = result;
            if (after != null && after[b.from + i] != null) trivia(f, after[b.from + i]);
        }
        if (b.trivia.helperExit != null) trivia(f, b.trivia.helperExit);
        if (b.trivia.exit != null) trivia(f, b.trivia.exit);
        f.reset = reset0;
        // 共有要素は群の先頭候補の式で評価したので、採用候補の site へ出現を付け替える。
        String[] retag = b.retag;
        if (retag != null) for (int d = 0; d < b.from; d++) if (retag[2 * d] != null) retag(buf[d], retag[2 * d], retag[2 * d + 1]);
        int n = b.from + rest.length;
        Match seq;
        if (n == 0) seq = success(start, f.c);
        else { var items = new Match[n]; System.arraycopy(buf, 0, items, 0, n); seq = combine(Arrays.asList(items), start, f.c); }
        progress(f);
        Cap k = b.cap;
        if (k.treeBypass && bypassWrap) return k.textKind || !tree ? seq : k.textAlternative ? textAlternative(seq) : project(seq, k.mixedScalar);
        if (k.bypass && bypassCapture) return seq;
        return wrap(f, c, m, seq, k.sites, k.names, k.spanRules, k.skips, k.scoped, k.rule, k.expr,
            k.quantified, k.terminal, k.semanticKind, k.semanticMany, k.failureHints);
    }
    /** 共有要素の局所出現（site を 1 つだけ持つ要素の 1 件）を採用候補のものに付け替える。 */
    private static void retag(Match head, String site, String name) {
        List<Occurrence> local = head.local();
        if (!local.isEmpty()) local.get(local.size() - 1).retag(site, name);
    }
    /** 群を持つ順序付き選択。{@code groups} は候補 index の昇順・非重複。 */
    public Match choice(Frame f, Call[] alternatives, boolean longest, boolean[] eligible, boolean fallback, Guard[] guards, Group[] groups) {
        if (!factor || f.invert || longest || eligible != null) return choice(f, alternatives, longest, eligible, fallback, guards);
        int c0 = f.c, m0 = f.m; boolean reset0 = f.reset;
        int probe = predict && guards != null && (reset0 || c0 == m0) ? c0 : -1;
        int probed = probe < 0 ? -2 : cp(probe);
        int next = 0;
        for (int i = 0; i < alternatives.length; i++) {
            while (next < groups.length && groups[next].end <= i) next++;
            Group group = next < groups.length && groups[next].start == i ? groups[next] : null;
            // 群の候補は候補除外の条件が一致することを生成時に確かめてあるので、群ごとに 1 度だけ判定する。
            if (probe >= 0 && guards[i] != null && !guards[i].allows(probed)
                && depth + guards[i].maxRuleDepth < options.maxDepth()) {
                progressAt(probe);
                if (group != null) i = group.end - 1;
                continue;
            }
            long mark = mark(); f.c = c0; f.m = reset0 ? c0 : m0; f.reset = reset0;
            Match result = group != null ? factored(f, group) : alternatives[i].parse(this, f);
            if (result != null) { f.reset = reset0; return result; }
            restore(mark);
            if (group != null) i = group.end - 1;
        }
        f.reset = reset0; f.c = c0; f.m = m0;
        return null;
    }
    public Match optional(Frame parent, Call child) {
        return repeat(parent, child, 0, 1, null, Trivia.NONE);
    }
    public Match repeat(Frame parent, Call child, int min, int max, Call terminator, Trivia trivia) {
        return repeat(parent, child, min, max, terminator, trivia, L_REPEAT);
    }
    public Match repeat(Frame parent, Call child, int min, int max, Call terminator, Trivia trivia, String label) {
        return repeat(parent, child, min, max, terminator, trivia, Label.of(label));
    }
    public Match repeat(Frame f, Call child, int min, int max, Call terminator, Trivia trivia, Label label) {
        long whole = mark(); int c0 = f.c, m0 = f.m; boolean reset0 = f.reset, matched0 = f.matched;
        if (reset0) f.m = f.c;
        int start = f.c, count = 0;
        if (trivia.entry != null) trivia(f, trivia.entry); if (trivia.helperEntry != null) trivia(f, trivia.helperEntry);
        ArrayList<Match> matches = tree ? new ArrayList<>() : null;
        ArrayList<Occurrence> local = null;
        while (true) {
            int before = f.position();
            if (terminator != null) {
                // The terminator runs in consumed mode on a begun frame; its cursors commit only when the loop itself is in consumed mode.
                long mark = mark(); int c1 = f.c, m1 = f.m; boolean reset1 = f.reset;
                if (reset1) f.m = f.c;
                f.matched = false;
                Match done = terminator.parse(this, f);
                f.matched = matched0; f.reset = reset1;
                if (done != null) {
                    if (!matched0) { if (tree) matches.add(done); else local = keep(local, done); }
                    else { restore(mark); f.c = c1; f.m = m1; }
                    break;
                }
                restore(mark); f.c = c1; f.m = m1;
            }
            long mark = mark(); Match result = child.parse(this, f);
            if (result == null) { restore(mark); break; }
            if (tree) matches.add(result); else local = keep(local, result);
            count++;
            if (before == f.position() || count >= max) break;
        }
        if (count < min || count > max) {
            int at = Math.max(c0, m0), fc = f.c, fm = f.m;
            f.c = c0; f.m = m0; f.reset = reset0;
            if (diag) {
                fail(at, label.id, c0, m0);
                if (label.text.startsWith("'") && activeDepth > 0) {
                    int rule = activeRules[activeDepth - 1];
                    if (ruleParserLabels[rule] == null) ruleParserLabels[rule] = Label.of(ruleNames[rule] + "Parser");
                    fail(at, ruleParserLabels[rule]);
                }
                diagnostics.failureCursor(at, fc, fm);
            } else if (at > reached) reached = at;
            restore(whole); return null;
        }
        if (trivia.helperExit != null) trivia(f, trivia.helperExit); if (trivia.exit != null) trivia(f, trivia.exit);
        f.reset = reset0;
        if (!tree) return fast(local, start, f.c);
        Match combined = combine(matches, start, f.c);
        return new Match(combined.nodes(), combined.local(), combined.captures(), combined.traces(), start, f.c, combined.mappingFailure(), combined.recoveries(), matches, combined.text(),
            matches.isEmpty() ? List.of() : combined.values());
    }
    public Match separated(Frame f, Call element, Call separator, int min, Trivia trivia) {
        long whole = mark(); int c0 = f.c, m0 = f.m; boolean reset0 = f.reset;
        if (reset0) f.m = f.c;
        int start = f.c;
        if (trivia.entry != null) trivia(f, trivia.entry); if (trivia.helperEntry != null) trivia(f, trivia.helperEntry);
        ArrayList<Match> matches = tree ? new ArrayList<>() : null, elements = tree ? new ArrayList<>() : null;
        ArrayList<Occurrence> local = null; int parts = 0; long first = mark();
        Match initial = element.parse(this, f);
        if (initial == null) {
            restore(first);
            if (min > 0) { restore(whole); f.c = c0; f.m = m0; f.reset = reset0; return null; }
            f.c = c0; f.m = reset0 ? c0 : m0; f.reset = reset0;
        } else {
            if (tree) { matches.add(initial); elements.add(initial); } else local = keep(local, initial);
            parts = 1;
            while (true) {
                long mark = mark(); int c1 = f.c, m1 = f.m; boolean reset1 = f.reset;
                if (reset1) f.m = f.c;
                int before = f.position();
                Match sep = separator.parse(this, f); Match next = sep == null ? null : element.parse(this, f);
                f.reset = reset1;
                if (next == null) { restore(mark); f.c = c1; f.m = m1; break; }
                if (tree) { matches.add(sep); matches.add(next); elements.add(next); }
                else { local = keep(local, sep); local = keep(local, next); }
                parts += 2;
                if (before == f.position()) break;
            }
        }
        if (initial != null && (parts + 1) / 2 < min) { restore(whole); f.c = c0; f.m = m0; f.reset = reset0; return null; }
        if (trivia.helperExit != null) trivia(f, trivia.helperExit); if (trivia.exit != null) trivia(f, trivia.exit);
        f.reset = reset0;
        if (!tree) return fast(local, start, f.c);
        Match result = combine(matches, start, f.c);
        return new Match(result.nodes(), result.local(), result.captures(), result.traces(), start, f.c,
            result.mappingFailure(), result.recoveries(), elements, result.text(), elements.isEmpty() ? List.of() : result.values());
    }
    public Match look(Frame parent, Call child, boolean negative, String label) { return look(parent, child, negative, Label.of(label)); }
    public Match look(Frame f, Call child, boolean negative, Label label) {
        long mark = mark(); int c0 = f.c, m0 = f.m; boolean reset0 = f.reset, matched0 = f.matched;
        if (reset0) f.m = f.c;
        f.matched = true;
        var diagnosticMark = negative && diag ? diagnostics.checkpoint() : null;
        int priorReached = reached;
        Match result;
        try { result = child.parse(this, f); }
        finally { restore(mark); f.matched = matched0; f.reset = reset0; }
        if (negative) {
            f.c = c0; f.m = m0;
            if (result != null) { fail(f, label); return null; }
            if (diagnosticMark != null) diagnostics.restore(diagnosticMark);
            reached = priorReached;
            f.m = reset0 ? c0 : m0; return success(c0, c0);
        }
        if (result == null) { f.c = c0; f.m = m0; return null; }
        return success(f.c, f.c);
    }
    public Match literal(Frame f, String word, boolean sensitive, String label) { return literal(f, word, sensitive, false, Label.of(label)); }
    public Match literal(Frame f, String word, boolean sensitive, Label label) { return literal(f, word, sensitive, false, label); }
    public Match literal(Frame f, String word, boolean sensitive, boolean boundary, String label) { return literal(f, word, sensitive, boundary, Label.of(label)); }
    /** boundary は D-040 の語境界。境界違反はリテラル不一致と同じ失敗記録になる。 */
    public Match literal(Frame f, String word, boolean sensitive, boolean boundary, Label label) {
        int at = f.position();
        boolean ok = !word.isEmpty() && (sensitive ? input.startsWith(at, word) : input.startsWithIgnoreCase(at, word));
        if (ok && boundary && identifierChar(cp(at + word.length()))) ok = false;
        if (!ok) { fail(f, label); f.advance(0); return null; }
        f.advance(word.length()); return success(at, at + word.length());
    }
    /** ASCII [A-Za-z0-9_]。組込み IdentifierParser の継続文字集合（D-040）。 */
    private static boolean identifierChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }
    public Match any(Frame f, String label) { return any(f, Label.of(label)); }
    public Match any(Frame f, Label label) {
        int at = f.position(); if (cp(at) < 0) { fail(f, label); f.advance(0); return null; }
        int length = Character.charCount(cp(at)); f.advance(length); return success(at, at + length);
    }
    public Match empty(Frame f) { return optional(f, (s, p) -> s.look(p, (ss, pp) -> ss.any(pp, L_ANY), false, L_EMPTY)); }
    public Match eof(Frame f) { return repeat(f, (s, p) -> s.any(p, L_WILDCARD_CHAR), 0, 0, null, Trivia.NONE, L_EOS); }
    public Match error(Frame f, String message) { fail(Math.max(f.c, f.m), message); return null; }
    public Match error(Frame f, Label message) { fail(Math.max(f.c, f.m), message); return null; }
    private boolean digit(int p) { return cp(p) >= '0' && cp(p) <= '9'; }
    public Match number(Frame f) {
        int at = f.position(), p = at;
        if (cp(p) == '+' || cp(p) == '-') p++; else fail(p, L_SIGN);
        int digits = p; while (digit(p)) p++;
        fail(p, L_DIGIT);
        boolean hasDigits = p > digits;
        if (!hasDigits) fail(p, L_ONE_OR_MORE);
        if (cp(p) == '.') {
            p++; int fraction = p; while (digit(p)) p++;
            fail(p, L_DIGIT);
            if (p == fraction) fail(p, L_ONE_OR_MORE);
            if (!hasDigits && p == fraction) { fail(at, L_CHAIN); fail(at, L_DOT); fail(at, L_NUMBER); f.advance(0); return null; }
        } else {
            fail(p, L_DOT);
            if (!hasDigits) { fail(digits, L_CHAIN); fail(at, L_NUMBER); f.advance(0); return null; }
        }
        int mantissa = p;
        if (cp(p) == 'e' || cp(p) == 'E') {
            p++; if (cp(p) == '+' || cp(p) == '-') p++; else fail(p, L_SIGN);
            int exponent = p; while (digit(p)) p++;
            fail(p, L_DIGIT);
            if (p == exponent) { fail(p, L_ONE_OR_MORE); p = mantissa; }
        } else { fail(p, L_E); fail(p, L_EXPONENT); }
        f.advance(p - at); return success(at, p);
    }
    public Match builtin(Frame f, String kind, int[] characters, String label) { return builtin(f, kind, characters, Label.of(label)); }
    public Match builtin(Frame f, String kind, int[] characters, Label label) {
        if (kind.equals("Number")) return number(f);
        if (kind.equals("EndOfSource")) return eof(f);
        int start = f.position(), p = start;
        if (kind.equals("Quoted") || kind.equals("SingleQuoted")) {
            int quote = kind.equals("Quoted") ? '"' : '\'';
            if (cp(p) != quote) return error(f, kind);
            p++;
            while (cp(p) >= 0 && cp(p) != quote) {
                if (cp(p) == '\\') { p++; if (cp(p) < 0) { fail(p, kind); return null; } }
                p += Character.charCount(cp(p));
            }
            if (cp(p) < 0) { fail(p, kind); return null; } p++;
        } else if (kind.equals("Identifier")) {
            if (!alphabet(cp(p)) && cp(p) != '_') { fail(f, L_ALPHABET_UNDERSCORE); return error(f, L_IDENTIFIER); }
            p++; while (alphabet(cp(p)) || digit(p) || cp(p) == '_') p++;
            fail(p, L_ALNUM_UNDERSCORE);
        } else {
            int ch = cp(p);
            boolean ok = ch >= 0 && switch (kind) {
                case "Digit" -> digit(p); case "Alphabet" -> alphabet(ch);
                case "AlphabetUnderScore" -> alphabet(ch) || ch == '_';
                case "AlphabetNumericUnderScore" -> alphabet(ch) || digit(p) || ch == '_';
                case "Space" -> ch == 32 || ch >= 9 && ch <= 13;
                case "CodePointSet" -> Arrays.binarySearch(characters, ch) >= 0;
                default -> throw new IllegalArgumentException(kind);
            };
            if (!ok) return error(f, label); p += Character.charCount(ch);
        }
        f.advance(p - start);
        if (plain) return OK;
        return new Match(List.of(), List.of(), List.of(), List.of(), start, p, false, List.of(), List.of(),
            kind.equals("Quoted") || kind.equals("SingleQuoted") ? source.substring(start + 1, p - 1) : null);
    }
    private static boolean alphabet(int ch) { return ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z'; }
    public Match range(Frame f, int min, int max, String excluded, String label) { return range(f, min, max, excluded, Label.of(label)); }
    public Match range(Frame f, int min, int max, String excluded, Label label) {
        int at = f.position(), ch = cp(at);
        if (ch < 0 || ch < min || ch > max || excluded != null && excluded.codePoints().anyMatch(v -> v == ch)) { Match failed = error(f, label); f.advance(0); return failed; }
        f.advance(Character.charCount(ch)); return success(at, f.position());
    }
    public Match dfa(Frame f, int state, int[] accepting, int[][] transitions, String label) { return dfa(f, state, accepting, transitions, Label.of(label)); }
    public Match dfa(Frame f, int state, int[] accepting, int[][] transitions, Label label) {
        int start = f.position(), at = start, candidate = -1;
        while (at < input.length()) {
            int point = cp(at), next = -1;
            for (int[] transition : transitions) if (transition[0] == state && transition[1] <= point && point <= transition[2]) {
                next = transition[3]; break;
            }
            if (next < 0) break;
            state = next; at += Character.charCount(point);
            for (int accepted : accepting) if (state == accepted) { candidate = at; break; }
        }
        if (candidate <= start) return error(f, label);
        f.advance(candidate - start); return success(start, candidate);
    }
    public Match regex(Frame f, Pattern pattern, String label) { return regex(f, pattern, Label.of(label)); }
    public Match regex(Frame f, Pattern pattern, Label label) {
        int at = f.position(); var matcher = pattern.matcher(source).region(at, source.length());
        if (!matcher.lookingAt() || matcher.end() == at) return error(f, label);
        input.cpIndex(matcher.end()); f.advance(matcher.end() - at); return success(at, matcher.end());
    }
    public Match until(Frame f, String terminator) {
        Label label = Label.of("'" + terminator + "'");
        return repeat(f, (s, p) -> s.any(p, L_ANY), 0, Integer.MAX_VALUE,
            terminator.isEmpty() ? null : (s, p) -> s.look(p, (ss, pp) -> ss.literal(pp, terminator, true, label), false, label), Trivia.NONE);
    }
    private TokenScanner[] scannerCache = new TokenScanner[0];
    public Match external(Frame f, String id) { return external(f, id, -1); }
    public Match external(Frame f, String id, int index) {
        TokenScanner scanner = index >= 0 && index < scannerCache.length ? scannerCache[index] : null;
        if (scanner == null) {
            scanner = options.scanners().get(id);
            if (scanner == null) throw new IllegalArgumentException("Missing TokenScanner: " + id);
            if (index >= 0) { if (index >= scannerCache.length) scannerCache = Arrays.copyOf(scannerCache, index + 1); scannerCache[index] = scanner; }
        }
        var result = scanner.scan(input, new TokenScanner.ScanState(f.c, f.m, f.matched ? TokenScanner.Mode.matchOnly : TokenScanner.Mode.consumed, f.invert, f.reset));
        for (var diagnostic : result.diagnostics()) { input.cpIndex(diagnostic.offsetUtf16()); fail(diagnostic.offsetUtf16(), diagnostic.expected()); }
        if (!result.ok()) return null;
        int valueStart = result.valueSpan() == null ? f.position() : result.valueSpan().start();
        int valueEnd = result.valueSpan() == null ? (f.matched ? result.matchedEnd() : result.consumedEnd()) : result.valueSpan().end();
        input.cpIndex(result.consumedEnd()); input.cpIndex(result.matchedEnd()); input.cpIndex(valueStart); input.cpIndex(valueEnd);
        if (result.consumedEnd() < f.c || result.matchedEnd() < result.consumedEnd() || valueEnd < valueStart
            || f.matched && result.consumedEnd() != f.c) throw new IllegalArgumentException("Invalid TokenScanner cursors: " + id);
        if (f.matched) f.m = result.matchedEnd(); else { f.c = result.consumedEnd(); f.m = result.matchedEnd(); }
        replay(result.effects());
        if (plain) return OK;
        return new Match(List.of(), List.of(), List.of(), List.of(), valueStart, valueEnd, false, List.of(), List.of(), source.substring(valueStart, valueEnd));
    }
    /**
     * Each failing round registers the hints of its failing delimiter prefix as one group id
     * (the same set as registering ' ', '//', '/*', '*\/' ... individually at that position). An
     * unterminated block comment keeps the individual registration path. A bare @comment delimiter
     * (CPPComment) registers its own label again, which is a set no-op.
     */
    private void trivia(Frame f, Delimiters set) {
        Delimiter[] delimiters = set.items;
        boolean again = true;
        while (again) {
            again = false;
            int p = f.position(), at = Math.max(f.c, f.m), failed = 0;
            // 高速経路: 先頭 1 文字がどの区切りの開始にもなり得ないなら、一般経路は必ず
            // 全 delimiter を失敗して fullGroup を登録して終わる。同じ 1 件だけ登録して返る。
            if (!set.canStart(p < input.length() ? input.codePointAt(p) : -1)) { fail(at, set.fullGroup()); return; }
            for (var delimiter : delimiters) {
                int next = p, terminatorEnd = -1;
                if (delimiter.isCharacters) {
                    int ch = cp(p);
                    if (ch >= 0 && delimiter.contains(ch)) next += Character.charCount(ch);
                } else if (p < input.length() && input.charAt(p) == delimiter.first && input.startsWith(p, delimiter.open)) {
                    if (delimiter.isLineComment) {
                        next += delimiter.open.length();
                        while (cp(next) >= 0 && cp(next) != '\r' && cp(next) != '\n') next += Character.charCount(cp(next));
                        terminatorEnd = cp(next) == '\r' && cp(next + 1) == '\n' ? next + 2 : cp(next) >= 0 ? next + 1 : next;
                    } else {
                        int end = source.indexOf(delimiter.close, p + delimiter.open.length());
                        if (end < 0) {
                            // Started but unterminated: hints go to the end of input, not to this position.
                            // The candidate names the declared close string (D-018 comment-trivia).
                            if (failed > 0) fail(at, set.prefixGroups[failed - 1]);
                            failed = -1;
                            fail(input.length(), delimiter.closeLabel); fail(input.length(), L_WILDCARD_STRING);
                        } else next = end + delimiter.close.length();
                    }
                }
                if (next > p) {
                    if (failed > 0) fail(at, set.prefixGroups[failed - 1]);
                    f.advance(next - p);
                    // CPPComment's MatchOnly(LineTerminator) commits M, leaving C before the newline.
                    if (!f.matched && terminatorEnd >= 0) f.m = terminatorEnd;
                    progress(f);
                    again = true; failed = -1; break;
                }
                if (failed >= 0) failed++;
            }
            if (failed > 0) fail(at, set.prefixGroups[failed - 1]);
        }
    }
    public Match recover(Frame f, Call child, String rule, String mode, String[] patterns, boolean consumeSync,
                         String noSync, String projection, String recipe) {
        long mark = mark(); int c0 = f.c, m0 = f.m; boolean reset0 = f.reset;
        // @recover を持つ文法は二段構えの対象外（生成側が twoMode=false を渡す）ので diag は常に真。
        diagnostics.enterExpression();
        Match result = child.parse(this, f);
        var failure = diagnostics.leaveRule();
        f.reset = reset0;
        if (result != null) return result;
        f.c = c0; f.m = m0;
        restore(mark); int start = f.position(), syncStart = -1, syncLength = 0;
        String selected = null;
        for (String pattern : patterns) {
            int found = source.indexOf(pattern, start);
            if (found >= 0 && (syncStart < 0 || found < syncStart)) { syncStart = found; syncLength = pattern.length(); selected = pattern; }
        }
        int end;
        if (mode.equals("SKIP")) {
            if (start >= input.length()) return null;
            end = patterns.length == 0 || syncStart == start ? start + Character.charCount(cp(start))
                : syncStart < 0 ? input.length() : syncStart;
        } else if (syncStart < 0) {
            if (noSync.equals("fail")) return null;
            end = noSync.equals("consumeToEof") ? input.length() : start;
        } else end = syncStart + (consumeSync ? syncLength : 0);
        if (end <= start) return null;
        f.advance(end - start);
        var hints = Label.texts(failure.expected());
        var diagnostic = new Diagnostic("recovery", input.cpIndex(start), failure.farthest() < 0 ? input.cpIndex(start) : input.cpIndex(failure.farthest()),
            hints, failure.deepestRule() < 0 ? null : ruleNames[failure.deepestRule()],
            Arrays.stream(failure.ruleStack()).mapToObj(i -> ruleNames[i]).toList(), hints, "ERROR", input.cpLength(start, end), "recovery");
        String id = projection.equals("explicitRecipe") ? recipe : "#recovery";
        Recipe marker = new Recipe(id, start, end, Map.of(), null);
        var recovery = new RecoveryEvent(rule, mode, start, end, selected, syncStart, syncStart < 0 ? -1 : syncStart + syncLength, diagnostic, marker);
        return new Match(List.of(marker), List.of(), List.of(), List.of(), start, end,
            projection.equals("mappingFailure"), List.of(recovery));
    }
    public <N> ParseResult<N> result(Frame f, Match match, Map<String, String> catalogs) {
        boolean ok = match != null; String kind = ok ? "none" : "syntax"; int offset = -1;
        if (ok && options.requireFullInput() && f.c != input.length()) { ok = false; kind = "trailing_input"; offset = f.c; }
        if (!ok && offset < 0) offset = diagnostics.farthest() < 0 ? reached : diagnostics.farthest();
        // 出現表・lexical・catalog・scope event は要求されなければ空のままなので、空なら何も確保しない。
        List<Capture> captures = List.of(); List<ParseResult.Lexical> lexical = List.of();
        Map<String, List<Capture>> catalog = Map.of();
        if (ok || match != null && !match.recoveries().isEmpty()) {
            List<Occurrence> published = match.captures();
            if (!published.isEmpty()) {
                var collected = new ArrayList<Capture>(published.size()); captures = collected;
                LinkedHashMap<String, List<Capture>> byContext = null;
                long occurrence = 0;
                for (int i = 0, n = published.size(); i < n; i++) { Occurrence capture = published.get(i);
                    var value = new Capture(occurrence, capture.site(), capture.name(), span(capture.value().start(), capture.value().end()), occurrence++, capture.completed);
                    collected.add(value);
                    String context = catalogs.get(capture.site());
                    if (context != null) {
                        if (byContext == null) byContext = new LinkedHashMap<>();
                        byContext.computeIfAbsent(context, key -> new ArrayList<>()).add(value);
                    }
                }
                if (byContext != null) { byContext.replaceAll((key, values) -> List.copyOf(values)); catalog = byContext; }
            }
            if (!match.traces().isEmpty()) {
                var collected = new ArrayList<ParseResult.Lexical>(); lexical = collected;
                long[] counter = {0, 0}; for (var trace : match.traces()) trace(trace, -1, counter, collected);
            }
        }
        var farthestExpected = Label.texts(diagnostics.expected());
        List<ParseResult.ScopeEvent> scopeEvents = List.of();
        if (!effects.isEmpty()) {
            var collected = new ArrayList<ParseResult.ScopeEvent>(effects.size()); scopeEvents = collected;
            for (int i = 0, n = effects.size(); i < n; i++) { var effect = effects.get(i);
                collected.add(new ParseResult.ScopeEvent(collected.size(), effect.action(), effect.mode(), effect.name(), effect.offsetCp(), effect.lengthCp())); }
        }
        var diagnostic = new Diagnostic(kind, offset < 0 ? -1 : input.cpIndex(offset), diagnostics.farthest() < 0 ? kind.equals("trailing_input") ? input.cpIndex(Math.max(diagnostics.reached(), Math.max(f.c, f.m))) : -1 : input.cpIndex(diagnostics.farthest()),
            kind.equals("trailing_input") ? List.of("end of input") : farthestExpected,
            diagnostics.deepestRule() < 0 ? null : ruleNames[diagnostics.deepestRule()],
            ruleNames(diagnostics.ruleStack()), farthestExpected);
        List<ParseResult.Recovery> recoveryList = List.of();
        if (match != null && !match.recoveries().isEmpty()) {
        var recoveries = new ArrayList<ParseResult.Recovery>(match.recoveries().size()); recoveryList = recoveries;
        for (RecoveryEvent recovery : match.recoveries()) {
            var occurrences = new ArrayList<Long>();
            for (int i = 0; i < match.captures().size(); i++)
                if (match.captures().get(i).value().nodes().stream().anyMatch(n -> n == recovery.marker())) occurrences.add((long) i);
            recoveries.add(new ParseResult.Recovery(recovery.rule(), recovery.mode(), span(recovery.start(), recovery.end()), recovery.pattern(),
                span(recovery.start(), recovery.mode().equals("SKIP") || recovery.syncStart() < 0 ? recovery.end() : Math.min(recovery.end(), recovery.syncStart())),
                recovery.syncStart() < 0 ? null : span(recovery.syncStart(), recovery.syncEnd()), occurrences, recovery.diagnostic()));
        }
        }
        var result = new ParseResult<N>(ok, input.cpIndex(f.c), input.cpIndex(f.m), Optional.empty(), Map.of(), captures, lexical,
            new ParseResult.Scope(scope.allDeclarations(), scope.references(), scope.diagnostics(), scopeEvents, scope.depth(), resolved()), diagnostic, catalog, recoveryList,
            new ParseResult.Failure(diagnostics.farthest() < 0 ? input.cpIndex(f.c) : input.cpIndex(diagnostics.farthest()),
                input.cpIndex(diagnostics.farthest() < 0 ? f.c : diagnostics.snapshot().consumed()), input.cpIndex(diagnostics.farthest() < 0 ? f.m : diagnostics.snapshot().matched()), farthestExpected));
        syntax = result; return result;
    }
    private List<String> ruleNames(int[] stack) {
        if (stack.length == 0) return List.of();
        var names = new String[stack.length];
        for (int i = 0; i < stack.length; i++) names[i] = ruleNames[stack[i]];
        return List.of(names);
    }
    private Map<String, ScopeStore.Decl> resolved() {
        var resolved = new LinkedHashMap<String, ScopeStore.Decl>();
        for (var declaration : scope.allDeclarations()) {
            var visible = scope.resolve(declaration.name());
            if (visible != null) resolved.put(declaration.name(), visible);
        }
        return resolved;
    }
    public void rollback(Frame f) { scope.restore(0); effects.clear(); f.c = 0; f.m = 0; }
    private void trace(Trace trace, long parent, long[] counter, List<ParseResult.Lexical> out) {
        long id = counter[0]++;
        for (var child : trace.children()) trace(child, id, counter, out);
        out.add(new ParseResult.Lexical(id, parent, trace.rule(), trace.expr(), span(trace.start(), trace.end()), counter[1]++, trace.token()));
    }
    public String text(Value value) {
        String text = new String(value.text() == null ? source.substring(value.start(), value.end()).strip() : value.text());
        spans.put(text, span(value.start(), value.end())); return text;
    }
    public int number(Value value) {
        try { return Integer.parseInt(text(value)); }
        catch (NumberFormatException error) { throw mapping(value.start(), value.end(), "NUMBER cannot be mapped to int", error); }
    }
    public Recipe node(Value value, Recipe owner) {
        if (value.nodes().size() > 1) throw mapping(value.start(), value.end(), "Scalar capture has multiple mapped nodes", null);
        if (!value.nodes().isEmpty()) return value.nodes().getFirst();
        if (owner.fallback() != null) return new Recipe("#leaf:" + owner.fallback(), value.start(), value.end(), Map.of(), null);
        throw mapping(value.start(), value.end(), "Missing mapped node", null);
    }
    // field は生成時に確定した添字で引く（名前の線形走査は 1 AST ノードにつき field 数回になる）。
    public <T> T scalar(Recipe recipe, int index, String field, Function<Value, T> conversion) {
        List<Value> values = recipe.fieldValues(index);
        if (values.isEmpty()) throw mapping(recipe.start(), recipe.end(), "Missing scalar capture: " + field, null);
        T value = conversion.apply(values.getFirst());
        if (value == null && !values.getFirst().nodes().isEmpty()) throw new RecoveredNodeMissing();
        return value;
    }
    public <T> Optional<T> optional(Recipe recipe, int index, String field, Function<Value, T> conversion) {
        List<Value> values = recipe.fieldValues(index);
        return values.isEmpty() ? Optional.empty() : Optional.ofNullable(conversion.apply(values.getFirst()));
    }
    public <T> List<T> list(Recipe recipe, int index, String field, Function<Value, T> conversion) {
        List<Value> values = recipe.fieldValues(index);
        if (values.isEmpty()) return List.of();
        var result = new ArrayList<T>(values.size());
        for (int k = 0, n = values.size(); k < n; k++) { T converted = conversion.apply(values.get(k)); if (converted != null) result.add(converted); }
        return List.copyOf(result);
    }
    public MappingException mapping(int start, int end, String message, Throwable cause) { return new MappingException(syntax, span(start, end), message, cause); }
    /** A missing recovered scalar omits its parent; optional/list callers absorb that absence. */
    public static final class RecoveredNodeMissing extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private RecoveredNodeMissing() { super(null, null, false, false); }
    }
    public void typeof(Recipe recipe, String ownName, Object own, String refName, Object referenced) {
        if (own != null && referenced != null && !own.getClass().equals(referenced.getClass()))
            {
            String message = "@typeof constraint violated: " + ownName + " must be same type as " + refName
                + ", expected " + referenced.getClass().getSimpleName() + " but got " + own.getClass().getSimpleName();
            throw mapping(recipe.start(), recipe.end(), message, new IllegalArgumentException(message));
        }
    }
    public boolean hasBuilt(Recipe recipe) { return built.containsKey(recipe); }
    public Object built(Recipe recipe) { return built.get(recipe); }
    public Object remember(Recipe recipe, Object object) { built.put(recipe, object); if (object != null) spans.put(object, span(recipe.start(), recipe.end())); return object; }
}
