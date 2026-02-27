package org.unlaxer.calculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.FormulaParser;

/**
 * LSP server for TinyExpression.
 * Provides:
 * - Auto-completion for TinyExpression keywords/functions
 * - Syntax validation with highlighting (valid=green, invalid=red)
 */
public class CalculatorLanguageServer implements LanguageServer, LanguageClientAware {

    private static final Pattern CATALOG_CODE_PATTERN = Pattern.compile("^\\[(TE\\d{3})\\]\\s*");
    private static final Pattern BARE_IDENTIFIER_PATTERN =
            Pattern.compile("^[\\p{L}_][\\p{L}\\p{N}_]*$");
    private static final Pattern VALID_IMPORT_PATTERN =
            Pattern.compile("^import\\s+.+\\bas\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;\\s*$");
    private static final Pattern VALID_VAR_HEAD_PATTERN =
            Pattern.compile("^(?:var|variable)\\s+\\$[\\p{L}_][\\p{L}\\p{N}_]*\\b.*$");
    private static final Pattern VALID_DESCRIPTION_PATTERN =
            Pattern.compile("\\bdescription\\s*=\\s*'[^']*'");
    private static final Pattern MISPLACED_TYPE_HINT_PATTERN =
            Pattern.compile("\\bas\\s+(?:Number|number|Float|float|String|string|Boolean|boolean|Object|object)\\s+\\$");
    private LanguageClient client;
    private final Map<String, DocumentState> documents = new HashMap<>();
    private final SuggestableParser suggestableParser = new TinyExpressionSuggestableParser();
    private final CalculatorTextDocumentService textDocumentService;
    private CalculatorAstAnalyzer astAnalyzer;

    public CalculatorLanguageServer() {
        this(new CalculatorAstAnalyzer());
    }

    CalculatorLanguageServer(CalculatorAstAnalyzer astAnalyzer) {
        this.astAnalyzer = astAnalyzer == null ? new CalculatorAstAnalyzer() : astAnalyzer;
        this.textDocumentService = new CalculatorTextDocumentService(this);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        configureAnalyzerFromInitializationOptions(params);
        ServerCapabilities capabilities = new ServerCapabilities();

        // Text document sync
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        // Completion support
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(this.suggestableParser.getTriggerCharacters());
        completionOptions.setResolveProvider(false);
        capabilities.setCompletionProvider(completionOptions);

        // Semantic tokens for syntax highlighting
        SemanticTokensWithRegistrationOptions semanticTokensOptions =
            new SemanticTokensWithRegistrationOptions();
        semanticTokensOptions.setFull(true);
        semanticTokensOptions.setLegend(new SemanticTokensLegend(
            List.of("valid", "invalid", "function", "number", "operator"),
            List.of()
        ));
        capabilities.setSemanticTokensProvider(semanticTokensOptions);

        // Hover support
        capabilities.setHoverProvider(true);

        // CodeLens support
        CodeLensOptions codeLensOptions = new CodeLensOptions();
        codeLensOptions.setResolveProvider(false);
        capabilities.setCodeLensProvider(codeLensOptions);
        capabilities.setCodeActionProvider(true);

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    private void configureAnalyzerFromInitializationOptions(InitializeParams params) {
        if (params == null || params.getInitializationOptions() == null) {
            return;
        }
        String catalogPath = readCatalogPathFromInitializationOptions(params.getInitializationOptions());
        if (catalogPath == null || catalogPath.isBlank()) {
            return;
        }
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalogPath, "initializeOptions.catalogPath");
        if (rules.isEmpty()) {
            return;
        }
        this.astAnalyzer = new CalculatorAstAnalyzer(rules);
        System.err.println("[tinyExpressionLsp] catalog loaded from initializationOptions: " + rules.source());
    }

    @SuppressWarnings("unchecked")
    private String readCatalogPathFromInitializationOptions(Object initializationOptions) {
        if (initializationOptions instanceof Map<?, ?> map) {
            Object value = map.get("catalogPath");
            if (value instanceof String text) {
                return text.trim();
            }
            return null;
        }
        return null;
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // Clean up
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new CalculatorWorkspaceService();
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    public LanguageClient getClient() {
        return client;
    }

    public Map<String, DocumentState> getDocuments() {
        return documents;
    }

    /**
     * Parse document and update state.
     */
    public ParseResult parseDocument(String uri, String content) {
        ParseResult parseResult = parseExpression(content);

        CalculatorAstAnalyzer.AnalysisResult analysis = astAnalyzer.analyze(content, parseResult);
        DocumentState state = new DocumentState(uri, content, parseResult, analysis);
        documents.put(uri, state);

        // Publish diagnostics
        if (client != null) {
            publishDiagnostics(state);
        }

        return parseResult;
    }

    ParseResult parseExpression(String content) {
        Parser parser = Parser.get(FormulaParser.class);
        ParseContext context = new ParseContext(new StringSource(content));
        try {
            Parsed result = parser.parse(context);
            Object failureDiagnostics = readParseFailureDiagnosticsCompat(context);

            int consumedLength = inferConsumedLength(content, context, result, failureDiagnostics);

            return new ParseResult(
                    result.isSucceeded(),
                    consumedLength,
                    content.length(),
                    result,
                    failureDiagnostics
            );
        } catch (Throwable parseError) {
            System.err.println("[tinyExpressionLsp] parseExpression failed: " + parseError);
            Object failureDiagnostics = readParseFailureDiagnosticsCompat(context);
            int consumedLength = inferConsumedLength(content, context, Parsed.FAILED, failureDiagnostics);
            return new ParseResult(
                    false,
                    consumedLength,
                    content.length(),
                    Parsed.FAILED,
                    failureDiagnostics
            );
        } finally {
            closeParseContextQuietly(context);
        }
    }

    /**
     * Publish diagnostics (errors) to the client.
     */
    private void publishDiagnostics(DocumentState state) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<CalculatorAstAnalyzer.AstError> astErrors = state.analysis.errors();
        for (CalculatorAstAnalyzer.AstError astError : astErrors) {
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setRange(astError.range());
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            extractCatalogCode(astError.message()).ifPresent(code -> diagnostic.setCode(Either.forLeft(code)));
            diagnostic.setMessage(astError.message());
            diagnostic.setSource("tinyexpression");
            diagnostics.add(diagnostic);
        }

        ParseResult result = state.parseResult;
        String content = state.content;
        String uri = state.uri;

        if (result.consumedLength < result.totalLength) {
            // Part of the input is invalid
            int errorStart = result.consumedLength;
            int errorEnd = result.totalLength;

            ParseFailureDescription failure =
                    describeParseFailure(content, errorStart, result.failureDiagnostics);
            errorStart = failure.startOffset();

            Position startPos = offsetToPosition(content, errorStart);
            Position endPos = offsetToPosition(content, errorEnd);

            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setRange(new Range(startPos, endPos));
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            applyErrorCatalog(
                    diagnostic,
                    content,
                    failure,
                    "Invalid expression: "
                            + failure.message()
                            + createParseFailureHint(result, failure.message()));
            diagnostic.setSource("tinyexpression");
            diagnostics.add(diagnostic);
        } else if (false == result.succeeded && result.totalLength > 0) {
            // Entire input is invalid
            ParseFailureDescription failure = describeParseFailure(content, 0, result.failureDiagnostics);
            int start = Math.max(0, Math.min(content.length(), failure.startOffset()));
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setRange(new Range(
                offsetToPosition(content, start),
                offsetToPosition(content, content.length())
            ));
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            applyErrorCatalog(
                    diagnostic,
                    content,
                    failure,
                    "Invalid expression: "
                            + failure.message()
                            + createParseFailureHint(result, failure.message()));
            diagnostic.setSource("tinyexpression");
            diagnostics.add(diagnostic);
        }

        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    private void applyErrorCatalog(
            Diagnostic diagnostic,
            String content,
            ParseFailureDescription failure,
            String fallbackMessage) {
        ErrorCatalogEntry catalog = resolveErrorCatalogEntry(content, failure);
        if (catalog == null) {
            diagnostic.setMessage(fallbackMessage);
            return;
        }
        diagnostic.setCode(Either.forLeft(catalog.code()));
        diagnostic.setMessage(formatCatalogMessage(catalog, failure.message()));
    }

    private ErrorCatalogEntry resolveErrorCatalogEntry(
            String content,
            ParseFailureDescription failure) {
        String message = failure.message();
        if (message == null || message.isBlank()) {
            return TE020_OTHER_SYNTAX;
        }
        if (message.startsWith("bare identifier expression")) {
            return TE002_BARE_IDENTIFIER_EXPRESSION;
        }
        if (message.startsWith("string literal quote invalid")) {
            return TE003_STRING_QUOTE_INVALID;
        }
        if (message.startsWith("description syntax invalid")) {
            return TE007_DESCRIPTION_SYNTAX_INVALID;
        }
        if (message.startsWith("invalid non-ascii punctuation character")) {
            return TE008_INVALID_CHARACTER;
        }
        if (message.startsWith("import declaration invalid")) {
            return TE016_IMPORT_DECLARATION_INVALID;
        }
        if (message.startsWith("variable declaration invalid")) {
            return TE017_VARIABLE_DECLARATION_INVALID;
        }
        if (message.startsWith("type hint position invalid")) {
            return TE018_TYPE_HINT_POSITION_INVALID;
        }
        if (message.startsWith("get/orElse syntax invalid")) {
            return TE019_GET_ORELSE_INVALID;
        }
        if (message.startsWith("if condition")) {
            return TE011_IF_CONDITION_INVALID;
        }
        if (message.startsWith("function argument count invalid for min/max")) {
            return TE015_FUNCTION_ARITY_INVALID;
        }
        if (message.startsWith("operator or notation invalid")) {
            return TE023_OPERATOR_NOTATION_INVALID;
        }
        if (message.startsWith("expected ')'")) {
            return TE004_MISSING_RIGHT_PAREN;
        }
        if (message.startsWith("expected '}'")) {
            return TE005_MISSING_RIGHT_BRACE;
        }
        if (message.startsWith("missing ';' after var declaration")) {
            return TE006_MISSING_SEMICOLON;
        }
        if (message.startsWith("missing ',' before default case")) {
            return TE014_DEFAULT_CASE_INVALID;
        }
        if (message.startsWith("missing ',' between match cases")) {
            return TE013_MATCH_SYNTAX_INVALID;
        }
        if (message.startsWith("expected expression after '->'")
                || message.startsWith("unexpected trailing ',' before '}'")) {
            return TE013_MATCH_SYNTAX_INVALID;
        }
        if (message.startsWith("undeclared or untyped variable")) {
            return TE022_UNDEFINED_VARIABLE_REFERENCE;
        }
        if (message.startsWith("expected '{'")) {
            if (isLikelyMatchContext(content, failure.startOffset())) {
                return TE013_MATCH_SYNTAX_INVALID;
            }
            return TE010_EXPECTED_TOKEN_MISMATCH;
        }
        if (message.startsWith("unexpected characters")) {
            return TE009_EXTRA_TOKEN;
        }
        if (message.startsWith("expected one of")
                || message.startsWith("expected ")) {
            return TE010_EXPECTED_TOKEN_MISMATCH;
        }
        if (message.startsWith("parse failed")) {
            return TE020_OTHER_SYNTAX;
        }
        return TE020_OTHER_SYNTAX;
    }

    private boolean isLikelyMatchContext(String content, int startOffset) {
        int matchIndex = findLastKeywordBefore(content, "match", startOffset);
        if (matchIndex < 0) {
            return false;
        }
        int ifIndex = findLastIfExpressionKeywordBefore(content, startOffset);
        return matchIndex > ifIndex;
    }

    private String formatCatalogMessage(ErrorCatalogEntry catalog, String detail) {
        String base = "[" + catalog.code() + "] " + catalog.message()
                + " 修正: " + catalog.fix();
        if ("TE013".equals(catalog.code())
                && detail != null
                && detail.startsWith("missing ','")) {
            return base + " (detail: match case の区切りに ',' が必要です)";
        }
        if ("TE020".equals(catalog.code())) {
            return base + " (detail: " + detail + ")";
        }
        return base;
    }

    private static record ErrorCatalogEntry(String code, String message, String fix) {}

    private static final ErrorCatalogEntry TE004_MISSING_RIGHT_PAREN =
            new ErrorCatalogEntry("TE004", "丸カッコが閉じられていません。", "対応する ')' を追加");
    private static final ErrorCatalogEntry TE005_MISSING_RIGHT_BRACE =
            new ErrorCatalogEntry("TE005", "波カッコが閉じられていません。", "対応する '}' を追加");
    private static final ErrorCatalogEntry TE006_MISSING_SEMICOLON =
            new ErrorCatalogEntry("TE006", "文末のセミコロンが必要です。", "文の末尾に ';' を追加");
    private static final ErrorCatalogEntry TE009_EXTRA_TOKEN =
            new ErrorCatalogEntry("TE009", "ここに不要なトークンがあります。", "余分な語句を削除");
    private static final ErrorCatalogEntry TE010_EXPECTED_TOKEN_MISMATCH =
            new ErrorCatalogEntry("TE010", "構文の並びが想定と一致しません。", "直前の式と区切り記号を確認");
    private static final ErrorCatalogEntry TE011_IF_CONDITION_INVALID =
            new ErrorCatalogEntry("TE011", "if 条件式が不正です。", "if 条件には booleanExpression を設定");
    private static final ErrorCatalogEntry TE015_FUNCTION_ARITY_INVALID =
            new ErrorCatalogEntry("TE015", "関数引数の数が不正です。", "関数定義に合わせて引数数を修正");
    private static final ErrorCatalogEntry TE023_OPERATOR_NOTATION_INVALID =
            new ErrorCatalogEntry("TE023", "演算子/記法が不正です。",
                    "条件式の演算子や記法（&/|、$method(...)）を修正");
    private static final ErrorCatalogEntry TE013_MATCH_SYNTAX_INVALID =
            new ErrorCatalogEntry("TE013", "match 構文が不正です。",
                    "match { cond -> expr, default -> expr } を確認");
    private static final ErrorCatalogEntry TE014_DEFAULT_CASE_INVALID =
            new ErrorCatalogEntry("TE014", "default ケースの記述が不正です。",
                    "default -> expr を追加/修正");
    private static final ErrorCatalogEntry TE020_OTHER_SYNTAX =
            new ErrorCatalogEntry("TE020", "構文エラーです。",
                    "エラー行の直前トークンと括弧を確認");
    private static final ErrorCatalogEntry TE022_UNDEFINED_VARIABLE_REFERENCE =
            new ErrorCatalogEntry("TE022", "利用可能な変数名ではありません。", "候補変数名へ修正");
    private static final ErrorCatalogEntry TE002_BARE_IDENTIFIER_EXPRESSION =
            new ErrorCatalogEntry("TE002", "識別子を式として解釈できません。", "変数は '$name'、文字列は 'text' を使用");
    private static final ErrorCatalogEntry TE003_STRING_QUOTE_INVALID =
            new ErrorCatalogEntry("TE003", "文字列リテラルのクォートが不正です。", "'text' 形式に修正");
    private static final ErrorCatalogEntry TE007_DESCRIPTION_SYNTAX_INVALID =
            new ErrorCatalogEntry("TE007", "description の書式が不正です。", "description='...' 形式に修正");
    private static final ErrorCatalogEntry TE008_INVALID_CHARACTER =
            new ErrorCatalogEntry("TE008", "不正な文字が含まれています。", "全角記号を半角記号へ修正");
    private static final ErrorCatalogEntry TE016_IMPORT_DECLARATION_INVALID =
            new ErrorCatalogEntry("TE016", "import 宣言の形式が不正です。", "import ... as ...; を確認");
    private static final ErrorCatalogEntry TE017_VARIABLE_DECLARATION_INVALID =
            new ErrorCatalogEntry("TE017", "variable 宣言の形式が不正です。", "var $name ... ; を確認");
    private static final ErrorCatalogEntry TE018_TYPE_HINT_POSITION_INVALID =
            new ErrorCatalogEntry("TE018", "型ヒントの位置が不正です。", "as number/string/boolean の位置を修正");
    private static final ErrorCatalogEntry TE019_GET_ORELSE_INVALID =
            new ErrorCatalogEntry("TE019", "get(...).orElse(...) 構文が不正です。", "get(...).orElse(...) 形式を確認");

    
    private String createParseFailureHint(ParseResult result, String primaryMessage) {
        if (primaryMessage != null
                && (primaryMessage.startsWith("undeclared or untyped variable")
                || primaryMessage.startsWith("missing ',' before default case")
                || primaryMessage.startsWith("missing ';' after var declaration")
                || primaryMessage.startsWith("expected expression after '->'")
                || primaryMessage.startsWith("unexpected trailing ',' before '}'")
                || primaryMessage.startsWith("expected '"))) {
            return "";
        }
        if (result.parsed == null) {
            return "";
        }
        List<String> expected = tryExtractExpectedTokens(result.parsed);
        if (expected.isEmpty()) {
            if (result.failureDiagnostics == null) {
                return "";
            }
            List<String> candidateHints = failureExpectedHintCandidates(result.failureDiagnostics).stream()
                    .map(candidate -> expectedHintDisplay(candidate) + " (" + expectedHintParserClassName(candidate) + ")")
                    .distinct()
                    .limit(8)
                    .toList();
            if (false == candidateHints.isEmpty()) {
                return " Expected hints: " + String.join(", ", candidateHints);
            }
            List<String> expectedParsers = failureExpectedParsers(result.failureDiagnostics);
            if (expectedParsers.isEmpty()) {
                return "";
            }
            String fallback = String.join(", ", expectedParsers);
            return " Expected parser(s): " + fallback;
        }
        String joined = String.join(", ", expected);
        return " Expected: " + joined;
    }

    private Optional<String> extractCatalogCode(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = CATALOG_CODE_PATTERN.matcher(message);
        if (matcher.find() == false) {
            return Optional.empty();
        }
        return Optional.ofNullable(matcher.group(1));
    }

    private List<String> tryExtractExpectedTokens(Parsed parsed) {
        try {
            // Common pattern: parsed.getErrors() -> List<Error>, where Error has getExpected()
            java.lang.reflect.Method getErrors = parsed.getClass().getMethod("getErrors");
            Object errorsObject = getErrors.invoke(parsed);
            if (errorsObject instanceof List) {
                List<?> errors = (List<?>) errorsObject;
                List<String> tokens = new ArrayList<>();
                for (Object error : errors) {
                    if (error == null) {
                        continue;
                    }
                    tokens.addAll(tryExtractExpectedFromError(error));
                }
                return tokens.stream().distinct().toList();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }

        try {
            java.lang.reflect.Method getExpected = parsed.getClass().getMethod("getExpected");
            Object expectedObject = getExpected.invoke(parsed);
            return normalizeExpected(expectedObject);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }

        try {
            java.lang.reflect.Method expectedTokens = parsed.getClass().getMethod("expectedTokens");
            Object expectedObject = expectedTokens.invoke(parsed);
            return normalizeExpected(expectedObject);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }

        return List.of();
    }

    private List<String> tryExtractExpectedFromError(Object error) {
        try {
            java.lang.reflect.Method getExpected = error.getClass().getMethod("getExpected");
            Object expectedObject = getExpected.invoke(error);
            return normalizeExpected(expectedObject);
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
    }

    private List<String> normalizeExpected(Object expectedObject) {
        if (expectedObject == null) {
            return List.of();
        }
        if (expectedObject instanceof List) {
            List<?> values = (List<?>) expectedObject;
            List<String> result = new ArrayList<>();
            for (Object value : values) {
                if (value == null) {
                    continue;
                }
                result.add(String.valueOf(value));
            }
            return result;
        }
        return List.of(String.valueOf(expectedObject));
    }

    private int inferConsumedLength(
            String content,
            ParseContext context,
            Parsed parsed,
            Object failureDiagnostics) {
        if (parsed.isSucceeded()) {
            // FormulaParser only succeeds when all input is consumed.
            return content.length();
        }
        int farthest = failureFarthestOffset(failureDiagnostics);
        if (farthest > 0) {
            return Math.max(0, Math.min(content.length(), farthest));
        }
        int consumed = readConsumedOffset(context, content.length());
        if (consumed > 0) {
            return consumed;
        }
        return detectMissingCommaBeforeDefaultOffset(content).orElse(0);
    }

    private int readConsumedOffset(ParseContext context, int totalLength) {
        try {
            int raw = tryReadConsumedOffsetCompat(context);
            return Math.max(0, Math.min(totalLength, raw));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int tryReadConsumedOffsetCompat(ParseContext context) throws ReflectiveOperationException {
        Object consumed = context.getClass().getMethod("getConsumedPosition").invoke(context);
        if (consumed == null) {
            return 0;
        }
        if (consumed instanceof Number number) {
            return number.intValue();
        }
        try {
            Object value = consumed.getClass().getMethod("value").invoke(consumed);
            if (value instanceof Number numberValue) {
                return numberValue.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return 0;
    }

    private Object readParseFailureDiagnosticsCompat(ParseContext context) {
        try {
            return context.getClass().getMethod("getParseFailureDiagnostics").invoke(context);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private void closeParseContextQuietly(ParseContext context) {
        try {
            context.close();
        } catch (Throwable closeError) {
            System.err.println("[tinyExpressionLsp] ParseContext.close warning: " + closeError);
        }
    }

    private boolean failureHasFailureCandidate(Object failureDiagnostics) {
        return invokeBooleanGetter(failureDiagnostics, "hasFailureCandidate").orElse(false);
    }

    private int failureFarthestOffset(Object failureDiagnostics) {
        return invokeIntGetter(failureDiagnostics, "getFarthestOffset").orElse(0);
    }

    private int failureMaxReachedStackDepth(Object failureDiagnostics) {
        Optional<List<?>> stack = invokeListGetter(failureDiagnostics, "getMaxReachedStackElements");
        return stack.map(List::size).orElse(0);
    }

    private List<String> failureExpectedParsers(Object failureDiagnostics) {
        Optional<List<?>> list = invokeListGetter(failureDiagnostics, "getExpectedParsers");
        if (list.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list.get()) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private List<Object> failureExpectedHintCandidates(Object failureDiagnostics) {
        Optional<List<?>> list = invokeListGetter(failureDiagnostics, "getExpectedHintCandidates");
        if (list.isEmpty()) {
            return List.of();
        }
        return new ArrayList<Object>(list.get());
    }

    private String expectedHintDisplay(Object candidate) {
        return invokeStringGetter(candidate, "getDisplayHint").orElse(null);
    }

    private String expectedHintParserClassName(Object candidate) {
        return invokeStringGetter(candidate, "getParserClassName").orElse("unknown");
    }

    private int expectedHintParserDepth(Object candidate) {
        return invokeIntGetter(candidate, "getParserDepth").orElse(999);
    }

    private Optional<Boolean> invokeBooleanGetter(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value instanceof Boolean bool) {
                return Optional.of(bool);
            }
            return Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Integer> invokeIntGetter(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value instanceof Number number) {
                return Optional.of(number.intValue());
            }
            return Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> invokeStringGetter(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(String.valueOf(value));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private Optional<List<?>> invokeListGetter(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value instanceof List<?>) {
                return Optional.of((List<?>) value);
            }
            return Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private ParseFailureDescription describeParseFailure(
            String content,
            int defaultOffset,
            Object failureDiagnostics) {
        Optional<ParseFailureDescription> catalogSyntaxIssue =
                describeCatalogSpecificSyntaxIssues(content, defaultOffset);
        if (catalogSyntaxIssue.isPresent()) {
            return catalogSyntaxIssue.get();
        }
        Optional<ParseFailureDescription> ifConditionIssue =
                describeIfConditionIssue(content, defaultOffset);
        if (ifConditionIssue.isPresent()) {
            return ifConditionIssue.get();
        }
        Optional<ParseFailureDescription> minMaxArityIssue =
                describeMinMaxArityIssue(content, defaultOffset, List.of());
        if (minMaxArityIssue.isPresent()) {
            return minMaxArityIssue.get();
        }
        Optional<ParseFailureDescription> operatorNotationIssue =
                describeOperatorNotationIssue(content, defaultOffset);
        if (operatorNotationIssue.isPresent()) {
            return operatorNotationIssue.get();
        }
        Optional<ParseFailureDescription> globalMissingIfElseBlockOpenFallback =
                describeMissingIfElseBlockOpeningCurlyBrace(content);
        if (globalMissingIfElseBlockOpenFallback.isPresent()) {
            return globalMissingIfElseBlockOpenFallback.get();
        }
        Optional<ParseFailureDescription> globalMissingArrowRhsFallback =
                describeMissingExpressionAfterArrowAnywhere(content);
        if (globalMissingArrowRhsFallback.isPresent()) {
            return globalMissingArrowRhsFallback.get();
        }
        Optional<ParseFailureDescription> globalMissingBlockOpen =
                describeMissingBlockOpeningCurlyBrace(content, content.length(), List.of("'{'"));
        if (globalMissingBlockOpen.isPresent()) {
            return globalMissingBlockOpen.get();
        }
        Optional<ParseFailureDescription> globalMissingMatchOpen =
                describeMissingMatchOpeningCurlyBrace(content, content.length());
        if (globalMissingMatchOpen.isPresent()) {
            return globalMissingMatchOpen.get();
        }
        Optional<ParseFailureDescription> globalMissingMatchClose =
                describeMissingMatchClosingCurlyBrace(content, content.length());
        if (globalMissingMatchClose.isPresent()) {
            return globalMissingMatchClose.get();
        }
        Optional<ParseFailureDescription> globalMissingBraceBeforeElse =
                describeMissingClosingCurlyBraceBeforeElse(content, content.length());
        if (globalMissingBraceBeforeElse.isPresent()) {
            return globalMissingBraceBeforeElse.get();
        }
        Optional<ParseFailureDescription> globalMissingCloseBrace =
                describeMissingGeneralClosingCurlyBrace(content, content.length());
        if (globalMissingCloseBrace.isPresent()) {
            return globalMissingCloseBrace.get();
        }
        if (failureHasFailureCandidate(failureDiagnostics)) {
            int start = Math.max(0, Math.min(content.length(), failureFarthestOffset(failureDiagnostics)));
            Optional<ParseFailureDescription> byExpected =
                    describeFromExpectedHints(
                            content,
                            start,
                            failureExpectedHintCandidates(failureDiagnostics),
                            failureExpectedParsers(failureDiagnostics));
            if (byExpected.isPresent()) {
                return byExpected.get();
            }
            Optional<Integer> missingSemicolonByLine = detectMissingSemicolonAfterVarDeclarationByLine(content);
            if (missingSemicolonByLine.isPresent()
                    && isLikelyStatementStartAfterOffset(content, missingSemicolonByLine.get())) {
                return new ParseFailureDescription(
                        missingSemicolonByLine.get(),
                        "missing ';' after var declaration");
            }
            int stackDepth = failureMaxReachedStackDepth(failureDiagnostics);
            String stackHint = stackDepth <= 0 ? "" : " near parser stack depth=" + stackDepth;
            return new ParseFailureDescription(start, "parse failed" + stackHint);
        }
        Optional<Integer> missingSemicolonByLine = detectMissingSemicolonAfterVarDeclarationByLine(content);
        if (missingSemicolonByLine.isPresent()
                && isLikelyStatementStartAfterOffset(content, missingSemicolonByLine.get())) {
            return new ParseFailureDescription(
                    missingSemicolonByLine.get(),
                    "missing ';' after var declaration");
        }
        Optional<Integer> missingCommaBetweenCases = detectMissingCommaBetweenMatchCasesOffset(content, content.length());
        if (missingCommaBetweenCases.isPresent()) {
            return new ParseFailureDescription(
                    missingCommaBetweenCases.get(),
                    "missing ',' between match cases");
        }
        Optional<ParseFailureDescription> globalMissingIfElseBlockOpen =
                describeMissingIfElseBlockOpeningCurlyBrace(content);
        if (globalMissingIfElseBlockOpen.isPresent()) {
            return globalMissingIfElseBlockOpen.get();
        }
        Optional<ParseFailureDescription> globalMissingArrowRhs =
                describeMissingExpressionAfterArrowAnywhere(content);
        if (globalMissingArrowRhs.isPresent()) {
            return globalMissingArrowRhs.get();
        }
        return detectMissingCommaBeforeDefaultOffset(content)
                .map(offset -> new ParseFailureDescription(
                        offset,
                        "missing ',' before default case in match expression"))
                .orElseGet(() -> new ParseFailureDescription(
                        defaultOffset,
                        "unexpected characters"));
    }

    private Optional<ParseFailureDescription> describeFromExpectedHints(
            String content,
            int startOffset,
            List<Object> expectedHintCandidates,
            List<String> expectedHints) {
        Optional<ParseFailureDescription> catalogSyntaxIssue =
                describeCatalogSpecificSyntaxIssues(content, startOffset);
        if (catalogSyntaxIssue.isPresent()) {
            return catalogSyntaxIssue;
        }
        List<String> mergedHints = new ArrayList<>();
        if (expectedHintCandidates != null) {
            mergedHints.addAll(expectedHintCandidates.stream()
                    .map(this::expectedHintDisplay)
                    .filter(Objects::nonNull)
                    .toList());
        }
        if (expectedHints != null) {
            mergedHints.addAll(expectedHints);
        }
        mergedHints = mergedHints.stream().filter(Objects::nonNull).distinct().toList();
        if (mergedHints.isEmpty()) {
            return Optional.empty();
        }
        Optional<ParseFailureDescription> ifConditionIssueHint =
                describeIfConditionIssue(content, startOffset);
        if (ifConditionIssueHint.isPresent()) {
            return ifConditionIssueHint;
        }
        Optional<ParseFailureDescription> minMaxArityIssueHint =
                describeMinMaxArityIssue(content, startOffset, mergedHints);
        if (minMaxArityIssueHint.isPresent()) {
            return minMaxArityIssueHint;
        }
        Optional<ParseFailureDescription> operatorNotationIssueHint =
                describeOperatorNotationIssue(content, startOffset);
        if (operatorNotationIssueHint.isPresent()) {
            return operatorNotationIssueHint;
        }
        boolean expectsComma = mergedHints.stream().anyMatch(h -> tokenEquals(h, ","));
        boolean expectsDefaultCase = mergedHints.stream()
                .anyMatch(h -> tokenEquals(h, "default")
                        || (h != null && h.endsWith("DefaultCaseFactorParser")));
        Optional<ParseFailureDescription> missingMatchOpenBraceHint =
                describeMissingMatchOpeningCurlyBrace(content, startOffset);
        if (missingMatchOpenBraceHint.isPresent()) {
            return missingMatchOpenBraceHint;
        }
        Optional<ParseFailureDescription> missingMatchCloseBraceHint =
                describeMissingMatchClosingCurlyBrace(content, startOffset);
        if (missingMatchCloseBraceHint.isPresent()) {
            return missingMatchCloseBraceHint;
        }
        Optional<ParseFailureDescription> missingExpressionAfterArrowHint =
                describeMissingExpressionAfterArrowInMatch(content, startOffset);
        if (missingExpressionAfterArrowHint.isPresent()) {
            return missingExpressionAfterArrowHint;
        }
        Optional<ParseFailureDescription> trailingCommaBeforeMatchCloseBraceHint =
                describeTrailingCommaBeforeMatchCloseBrace(content, startOffset);
        if (trailingCommaBeforeMatchCloseBraceHint.isPresent()) {
            return trailingCommaBeforeMatchCloseBraceHint;
        }
        Optional<Integer> missingCommaBetweenCasesOffset =
                detectMissingCommaBetweenMatchCasesOffset(content, startOffset);
        if (missingCommaBetweenCasesOffset.isPresent()) {
            return Optional.of(new ParseFailureDescription(
                    missingCommaBetweenCasesOffset.get(),
                    "missing ',' between match cases"));
        }
        Optional<ParseFailureDescription> missingBraceBeforeElseHint =
                describeMissingClosingCurlyBraceBeforeElse(content, startOffset);
        if (missingBraceBeforeElseHint.isPresent()) {
            return missingBraceBeforeElseHint;
        }
        Optional<ParseFailureDescription> missingGeneralCloseBraceHint =
                describeMissingGeneralClosingCurlyBrace(content, startOffset);
        if (missingGeneralCloseBraceHint.isPresent()) {
            return missingGeneralCloseBraceHint;
        }
        Optional<Integer> missingCommaBeforeDefaultOffset = detectMissingCommaBeforeDefaultOffset(content);
        if (expectsComma && expectsDefaultCase && missingCommaBeforeDefaultOffset.isPresent()) {
            return Optional.of(new ParseFailureDescription(
                    startOffset,
                    "missing ',' before default case in match expression"));
        }
        if (expectsComma && missingCommaBeforeDefaultOffset.isPresent()) {
            return Optional.of(new ParseFailureDescription(startOffset, "missing ','"));
        }
        Optional<ParseFailureDescription> variableHint =
                describeUndeclaredVariableHint(content, startOffset, mergedHints);
        if (variableHint.isPresent()) {
            return variableHint;
        }
        Optional<ParseFailureDescription> missingSemicolonHint =
                describeMissingSemicolonAfterVarDeclaration(content, startOffset, mergedHints);
        if (missingSemicolonHint.isPresent()) {
            return missingSemicolonHint;
        }
        Optional<ParseFailureDescription> missingBlockBraceHint =
                describeMissingBlockOpeningCurlyBrace(content, startOffset, mergedHints);
        if (missingBlockBraceHint.isPresent()) {
            return missingBlockBraceHint;
        }
        Optional<ParseFailureDescription> structuralTokenHint =
                describeExpectedSingleStructuralToken(startOffset, mergedHints);
        if (structuralTokenHint.isPresent()) {
            return structuralTokenHint;
        }
        List<String> tokenHints = mergedHints.stream()
                .filter(h -> h != null && h.length() >= 2 && h.startsWith("'") && h.endsWith("'"))
                .distinct()
                .toList();
        if (tokenHints.size() >= 2) {
            String joined = tokenHints.size() > 6
                    ? String.join(", ", tokenHints.subList(0, 6)) + ", ..."
                    : String.join(", ", tokenHints);
            return Optional.of(new ParseFailureDescription(
                    startOffset,
                    "expected one of " + joined));
        }
        if (tokenHints.size() == 1) {
            return Optional.of(new ParseFailureDescription(
                    startOffset,
                    "expected " + tokenHints.get(0)));
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeCatalogSpecificSyntaxIssues(
            String content,
            int startOffset) {
        Optional<ParseFailureDescription> invalidCharacter = describeInvalidCharacterIssue(content);
        if (invalidCharacter.isPresent()) {
            return invalidCharacter;
        }
        Optional<ParseFailureDescription> stringQuote = describeStringQuoteIssue(content);
        if (stringQuote.isPresent()) {
            return stringQuote;
        }
        Optional<ParseFailureDescription> description = describeDescriptionSyntaxIssue(content);
        if (description.isPresent()) {
            return description;
        }
        Optional<ParseFailureDescription> importIssue = describeImportDeclarationIssue(content);
        if (importIssue.isPresent()) {
            return importIssue;
        }
        Optional<ParseFailureDescription> variableIssue = describeVariableDeclarationIssue(content);
        if (variableIssue.isPresent()) {
            return variableIssue;
        }
        Optional<ParseFailureDescription> typeHintIssue = describeTypeHintPositionIssue(content);
        if (typeHintIssue.isPresent()) {
            return typeHintIssue;
        }
        Optional<ParseFailureDescription> getOrElseIssue = describeGetOrElseSyntaxIssue(content);
        if (getOrElseIssue.isPresent()) {
            return getOrElseIssue;
        }
        return describeBareIdentifierIssue(content, startOffset);
    }

    private Optional<ParseFailureDescription> describeBareIdentifierIssue(String content, int startOffset) {
        String trimmed = content.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (BARE_IDENTIFIER_PATTERN.matcher(trimmed).matches() == false) {
            return Optional.empty();
        }
        String lower = trimmed.toLowerCase();
        if (lower.equals("if") || lower.equals("match") || lower.equals("default")
                || lower.equals("var") || lower.equals("variable")
                || lower.equals("import") || lower.equals("external")
                || lower.equals("internal") || lower.equals("call")
                || lower.equals("true") || lower.equals("false")) {
            return Optional.empty();
        }
        int position = content.indexOf(trimmed);
        if (position < 0) {
            position = Math.max(0, Math.min(content.length(), startOffset));
        }
        return Optional.of(new ParseFailureDescription(position, "bare identifier expression"));
    }

    private Optional<ParseFailureDescription> describeStringQuoteIssue(String content) {
        int index = content.indexOf('"');
        if (index < 0) {
            return Optional.empty();
        }
        return Optional.of(new ParseFailureDescription(index, "string literal quote invalid"));
    }

    private Optional<ParseFailureDescription> describeInvalidCharacterIssue(String content) {
        String invalids = "；（），｛｝：＄”’　";
        for (int i = 0; i < content.length(); i++) {
            if (invalids.indexOf(content.charAt(i)) >= 0) {
                return Optional.of(new ParseFailureDescription(i, "invalid non-ascii punctuation character"));
            }
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeImportDeclarationIssue(String content) {
        int offset = 0;
        while (offset <= content.length()) {
            int lineEnd = content.indexOf('\n', offset);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = normalizeLineForRegex(content.substring(offset, lineEnd));
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("import ") && VALID_IMPORT_PATTERN.matcher(trimmed).matches() == false) {
                return Optional.of(new ParseFailureDescription(offset + line.indexOf(trimmed), "import declaration invalid"));
            }
            if (lineEnd >= content.length()) {
                break;
            }
            offset = lineEnd + 1;
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeVariableDeclarationIssue(String content) {
        int offset = 0;
        while (offset <= content.length()) {
            int lineEnd = content.indexOf('\n', offset);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = normalizeLineForRegex(content.substring(offset, lineEnd));
            String trimmed = line.stripLeading();
            if ((trimmed.startsWith("var ") || trimmed.startsWith("variable "))
                    && VALID_VAR_HEAD_PATTERN.matcher(trimmed).matches() == false) {
                return Optional.of(new ParseFailureDescription(offset + line.indexOf(trimmed), "variable declaration invalid"));
            }
            if (lineEnd >= content.length()) {
                break;
            }
            offset = lineEnd + 1;
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeDescriptionSyntaxIssue(String content) {
        int offset = 0;
        while (offset <= content.length()) {
            int lineEnd = content.indexOf('\n', offset);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = normalizeLineForRegex(content.substring(offset, lineEnd));
            String trimmed = line.stripLeading();
            if ((trimmed.startsWith("var ") || trimmed.startsWith("variable "))
                    && trimmed.contains("description")
                    && VALID_DESCRIPTION_PATTERN.matcher(trimmed).find() == false) {
                return Optional.of(new ParseFailureDescription(offset + line.indexOf(trimmed), "description syntax invalid"));
            }
            if (lineEnd >= content.length()) {
                break;
            }
            offset = lineEnd + 1;
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeTypeHintPositionIssue(String content) {
        Matcher matcher = MISPLACED_TYPE_HINT_PATTERN.matcher(content);
        if (matcher.find()) {
            return Optional.of(new ParseFailureDescription(matcher.start(), "type hint position invalid"));
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeGetOrElseSyntaxIssue(String content) {
        if (content.contains("get(") == false || content.contains("orElse") == false) {
            return Optional.empty();
        }
        int getHead = content.indexOf("get(");
        int getOpen = content.indexOf('(', getHead);
        if (getOpen < 0) {
            return Optional.of(new ParseFailureDescription(getHead, "get/orElse syntax invalid"));
        }
        int getClose = findMatchingCloseParenthesis(content, getOpen);
        int orElseIndex = content.indexOf(".orElse(", getHead);
        if (orElseIndex < 0 || getClose < 0 || orElseIndex <= getClose) {
            return Optional.of(new ParseFailureDescription(getHead, "get/orElse syntax invalid"));
        }
        int orElseOpen = content.indexOf('(', orElseIndex);
        if (orElseOpen < 0 || findMatchingCloseParenthesis(content, orElseOpen) < 0) {
            return Optional.of(new ParseFailureDescription(orElseIndex, "get/orElse syntax invalid"));
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeIfConditionIssue(
            String content,
            int startOffset) {
        int ifIndex = findLastIfExpressionKeywordBefore(content, startOffset);
        if (ifIndex < 0) {
            return Optional.empty();
        }
        int cursor = skipWhitespace(content, ifIndex + 2);
        if (cursor >= content.length()) {
            return Optional.of(new ParseFailureDescription(cursor, "if condition requires '(...)'"));
        }
        if (content.charAt(cursor) != '(') {
            return Optional.of(new ParseFailureDescription(cursor, "if condition requires '(...)'"));
        }
        int close = findMatchingCloseParenthesis(content, cursor);
        if (close < 0) {
            int position = Math.max(cursor + 1, Math.max(0, Math.min(content.length(), startOffset)));
            return Optional.of(new ParseFailureDescription(position, "if condition missing ')'"));
        }
        int innerStart = cursor + 1;
        int innerEnd = Math.max(innerStart, close);
        if (content.substring(innerStart, innerEnd).isBlank()) {
            return Optional.of(new ParseFailureDescription(innerStart, "if condition is empty"));
        }
        int probe = Math.max(0, Math.min(content.length(), startOffset));
        if (probe > innerStart && probe <= innerEnd) {
            return Optional.of(new ParseFailureDescription(probe, "if condition must be boolean expression"));
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeMinMaxArityIssue(
            String content,
            int startOffset,
            List<String> mergedHints) {
        int minIndex = findLastKeywordBefore(content, "min", startOffset);
        int maxIndex = findLastKeywordBefore(content, "max", startOffset);
        int fnIndex = Math.max(minIndex, maxIndex);
        if (fnIndex < 0) {
            return Optional.empty();
        }
        String functionName = fnIndex == minIndex ? "min" : "max";
        int cursor = skipWhitespace(content, fnIndex + functionName.length());
        if (cursor >= content.length() || content.charAt(cursor) != '(') {
            return Optional.empty();
        }
        int close = findMatchingCloseParenthesis(content, cursor);
        if (close < 0) {
            return Optional.empty();
        }
        int failureOffset = Math.max(0, Math.min(content.length(), startOffset));
        if (failureOffset < fnIndex || failureOffset > close + 1) {
            return Optional.empty();
        }
        ArgumentShape shape = analyzeTopLevelArguments(content, cursor + 1, close);
        if (shape.hasEmptySegment || shape.argumentCount != 2) {
            return Optional.of(new ParseFailureDescription(
                    failureOffset,
                    "function argument count invalid for min/max"));
        }
        if (mergedHints == null || mergedHints.isEmpty()) {
            return Optional.empty();
        }
        boolean expectsComma = mergedHints.stream().anyMatch(h -> tokenEquals(h, ","));
        boolean expectsRightParen = mergedHints.stream().anyMatch(h -> tokenEquals(h, ")"));
        if (expectsComma || expectsRightParen) {
            return Optional.of(new ParseFailureDescription(
                    failureOffset,
                    "function argument count invalid for min/max"));
        }
        return Optional.empty();
    }

    private ArgumentShape analyzeTopLevelArguments(String content, int startInclusive, int endExclusive) {
        int nesting = 0;
        int argumentCount = 0;
        boolean hasEmptySegment = false;
        boolean hasTokenInSegment = false;
        boolean hasNonWhitespace = false;

        for (int i = startInclusive; i < endExclusive; i++) {
            char c = content.charAt(i);
            if (c == '(' || c == '{' || c == '[') {
                nesting++;
                hasTokenInSegment = true;
                hasNonWhitespace = true;
                continue;
            }
            if (c == ')' || c == '}' || c == ']') {
                if (nesting > 0) {
                    nesting--;
                }
                hasTokenInSegment = true;
                hasNonWhitespace = true;
                continue;
            }
            if (nesting == 0 && c == ',') {
                if (hasTokenInSegment == false) {
                    hasEmptySegment = true;
                }
                argumentCount++;
                hasTokenInSegment = false;
                continue;
            }
            if (Character.isWhitespace(c) == false) {
                hasTokenInSegment = true;
                hasNonWhitespace = true;
            }
        }

        if (hasNonWhitespace) {
            argumentCount++;
        } else {
            argumentCount = 0;
        }
        if (argumentCount > 0 && hasTokenInSegment == false) {
            hasEmptySegment = true;
        }
        return new ArgumentShape(argumentCount, hasEmptySegment);
    }

    private Optional<ParseFailureDescription> describeOperatorNotationIssue(
            String content,
            int startOffset) {
        boolean[] ignoredMask = buildIgnoredTextMask(content);
        int offset = Math.max(0, Math.min(content.length(), startOffset));
        int windowStart = Math.max(0, offset - 96);
        int windowEnd = Math.min(content.length(), offset + 96);
        for (int i = windowStart; i + 1 < windowEnd; i++) {
            if (isIgnoredTextOffset(ignoredMask, i)) {
                continue;
            }
            char current = content.charAt(i);
            char next = content.charAt(i + 1);
            if (current == '&' && next == '&') {
                return Optional.of(new ParseFailureDescription(
                        i,
                        "operator or notation invalid: use '&' instead of '&&'"));
            }
            if (current == '|' && next == '|') {
                return Optional.of(new ParseFailureDescription(
                        i,
                        "operator or notation invalid: use '|' instead of '||'"));
            }
        }
        for (int i = windowStart; i < windowEnd; i++) {
            if (isIgnoredTextOffset(ignoredMask, i)) {
                continue;
            }
            char current = content.charAt(i);
            if (current != '&' && current != '|') {
                continue;
            }
            if (i + 1 < content.length() && content.charAt(i + 1) == current) {
                continue;
            }
            int rhs = skipWhitespace(content, i + 1);
            if (rhs >= content.length()) {
                return Optional.of(new ParseFailureDescription(
                        i,
                        "operator or notation invalid: missing rhs after boolean operator"));
            }
            char rhsChar = content.charAt(rhs);
            if (rhsChar == ')' || rhsChar == '}' || rhsChar == ';' || rhsChar == ',') {
                return Optional.of(new ParseFailureDescription(
                        i,
                        "operator or notation invalid: missing rhs after boolean operator"));
            }
        }
        Optional<Integer> dollarMethod = findDollarPrefixedInvocation(content, windowStart, windowEnd, ignoredMask);
        if (dollarMethod.isPresent()) {
            return Optional.of(new ParseFailureDescription(
                    dollarMethod.get(),
                    "operator or notation invalid: remove '$' before method call"));
        }
        return Optional.empty();
    }

    private Optional<Integer> findDollarPrefixedInvocation(
            String content,
            int windowStart,
            int windowEnd,
            boolean[] ignoredMask) {
        for (int i = windowStart; i < windowEnd; i++) {
            if (isIgnoredTextOffset(ignoredMask, i)) {
                continue;
            }
            if (content.charAt(i) != '$') {
                continue;
            }
            int cursor = skipWhitespace(content, i + 1);
            if (cursor >= content.length() || Character.isJavaIdentifierStart(content.charAt(cursor)) == false) {
                continue;
            }
            cursor++;
            while (cursor < content.length()) {
                char c = content.charAt(cursor);
                if (Character.isJavaIdentifierPart(c)) {
                    cursor++;
                    continue;
                }
                break;
            }
            int afterName = skipWhitespace(content, cursor);
            if (afterName < content.length() && content.charAt(afterName) == '(') {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private boolean[] buildIgnoredTextMask(String content) {
        boolean[] ignoredMask = new boolean[content.length()];
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (inLineComment) {
                ignoredMask[i] = true;
                if (current == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                ignoredMask[i] = true;
                if (current == '*' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                    ignoredMask[i + 1] = true;
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (inSingleQuote) {
                ignoredMask[i] = true;
                if (current == '\\' && i + 1 < content.length()) {
                    ignoredMask[i + 1] = true;
                    i++;
                    continue;
                }
                if (current == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (current == '/' && i + 1 < content.length()) {
                char next = content.charAt(i + 1);
                if (next == '/') {
                    ignoredMask[i] = true;
                    ignoredMask[i + 1] = true;
                    i++;
                    inLineComment = true;
                    continue;
                }
                if (next == '*') {
                    ignoredMask[i] = true;
                    ignoredMask[i + 1] = true;
                    i++;
                    inBlockComment = true;
                    continue;
                }
            }
            if (current == '\'') {
                ignoredMask[i] = true;
                inSingleQuote = true;
            }
        }
        return ignoredMask;
    }

    private boolean isIgnoredTextOffset(boolean[] ignoredMask, int offset) {
        return offset >= 0 && offset < ignoredMask.length && ignoredMask[offset];
    }

    private Optional<ParseFailureDescription> describeUndeclaredVariableHint(
            String content,
            int startOffset,
            List<String> mergedHints) {
        int cursor = skipWhitespace(content, Math.max(0, startOffset));
        if (cursor >= content.length() || content.charAt(cursor) != '$') {
            return Optional.empty();
        }
        boolean expectsTypeDeclaration = mergedHints.stream()
                .anyMatch(h -> tokenEquals(h, "as")
                        || tokenEquals(h, "Number")
                        || tokenEquals(h, "number")
                        || tokenEquals(h, "Float")
                        || tokenEquals(h, "float")
                        || tokenEquals(h, "Boolean")
                        || tokenEquals(h, "boolean")
                        || tokenEquals(h, "String")
                        || tokenEquals(h, "string")
                        || tokenEquals(h, "Object")
                        || tokenEquals(h, "object"));
        if (expectsTypeDeclaration == false) {
            return Optional.empty();
        }
        String variable = readVariableToken(content, cursor);
        if (variable.isBlank()) {
            variable = "$var";
        }
        return Optional.of(new ParseFailureDescription(
                cursor,
                "undeclared or untyped variable " + variable
                        + ". declare it first, e.g. var " + variable + " as boolean set if not exists false;"));
    }

    private Optional<ParseFailureDescription> describeMissingSemicolonAfterVarDeclaration(
            String content,
            int startOffset,
            List<String> mergedHints) {
        boolean expectsSemicolon = mergedHints.stream().anyMatch(h -> tokenEquals(h, ";"));
        boolean hasVarDeclarationSignals = mergedHints.stream()
                .anyMatch(h -> tokenEquals(h, "var")
                        || tokenEquals(h, "variable")
                        || tokenEquals(h, "description")
                        || tokenEquals(h, "$"));

        if (expectsSemicolon && hasVarDeclarationSignals) {
            int probe = Math.max(0, Math.min(content.length(), startOffset));
            int statementEnd = skipWhitespaceBackward(content, probe - 1);
            if (statementEnd >= 0) {
                int statementStart = findStatementStart(content, statementEnd);
                if (statementStart < content.length()) {
                    String statement = content.substring(statementStart, statementEnd + 1).stripLeading();
                    if ((statement.startsWith("var ") || statement.startsWith("variable "))
                            && statement.indexOf(';') < 0) {
                        return Optional.of(new ParseFailureDescription(
                                Math.min(content.length(), statementEnd + 1),
                                "missing ';' after var declaration"));
                    }
                }
            }
        }

        Optional<Integer> lineBasedOffset = detectMissingSemicolonAfterVarDeclarationByLine(content);
        if (lineBasedOffset.isPresent()) {
            boolean shouldUseLineFallback = expectsSemicolon
                    || hasVarDeclarationSignals
                    || isLikelyStatementStartAfterOffset(content, lineBasedOffset.get());
            if (shouldUseLineFallback == false) {
                return Optional.empty();
            }
            return Optional.of(new ParseFailureDescription(
                    lineBasedOffset.get(),
                    "missing ';' after var declaration"));
        }
        return Optional.empty();
    }

    private Optional<Integer> detectMissingSemicolonAfterVarDeclarationByLine(String content) {
        int offset = 0;
        while (offset <= content.length()) {
            int lineEnd = content.indexOf('\n', offset);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(offset, lineEnd);
            String trimmed = line.stripLeading();
            if ((trimmed.startsWith("var ") || trimmed.startsWith("variable "))
                    && trimmed.contains(";") == false) {
                int commentIndex = trimmed.indexOf("//");
                String commentStripped = commentIndex >= 0
                        ? trimmed.substring(0, commentIndex)
                        : trimmed;
                if (commentStripped.contains(";") == false) {
                    return Optional.of(Math.min(content.length(), lineEnd));
                }
            }
            if (lineEnd >= content.length()) {
                break;
            }
            offset = lineEnd + 1;
        }
        return Optional.empty();
    }

    private boolean isLikelyStatementStartAfterOffset(String content, int offset) {
        int cursor = skipWhitespace(content, Math.max(0, offset));
        if (cursor >= content.length()) {
            return false;
        }
        if (content.charAt(cursor) == '}') {
            return true;
        }
        String[] starts = new String[] {
                "if", "match", "var", "variable", "import", "external", "internal", "call", "default"
        };
        for (String start : starts) {
            if (content.regionMatches(cursor, start, 0, start.length())
                    && isIdentifierPart(content, cursor + start.length()) == false) {
                return true;
            }
        }
        return false;
    }

    private Optional<ParseFailureDescription> describeMissingIfConditionClosingParenthesis(
            String content,
            int startOffset) {
        int ifIndex = findLastIfExpressionKeywordBefore(content, startOffset);
        if (ifIndex < 0) {
            return Optional.empty();
        }
        int cursor = skipWhitespace(content, ifIndex + 2);
        if (cursor >= content.length() || content.charAt(cursor) != '(') {
            return Optional.empty();
        }
        int close = findMatchingCloseParenthesis(content, cursor);
        if (close >= 0) {
            return Optional.empty();
        }
        int position = Math.max(cursor + 1, Math.max(0, Math.min(content.length(), startOffset)));
        return Optional.of(new ParseFailureDescription(position, "expected ')'"));
    }

    private Optional<ParseFailureDescription> describeMissingMatchOpeningCurlyBrace(
            String content,
            int startOffset) {
        int matchIndex = findLastKeywordBefore(content, "match", startOffset);
        if (matchIndex < 0) {
            return Optional.empty();
        }
        int cursor = skipWhitespace(content, matchIndex + "match".length());
        if (cursor >= content.length()) {
            return Optional.of(new ParseFailureDescription(cursor, "expected '{'"));
        }
        if (content.charAt(cursor) == '{') {
            return Optional.empty();
        }
        return Optional.of(new ParseFailureDescription(cursor, "expected '{'"));
    }

    private Optional<ParseFailureDescription> describeMissingMatchClosingCurlyBrace(
            String content,
            int startOffset) {
        int matchIndex = findLastKeywordBefore(content, "match", startOffset);
        if (matchIndex < 0) {
            return Optional.empty();
        }
        int open = skipWhitespace(content, matchIndex + "match".length());
        if (open >= content.length() || content.charAt(open) != '{') {
            return Optional.empty();
        }
        int close = findMatchingCloseCurlyBrace(content, open);
        if (close >= 0) {
            return Optional.empty();
        }
        return Optional.of(new ParseFailureDescription(content.length(), "expected '}'"));
    }

    private Optional<ParseFailureDescription> describeMissingExpressionAfterArrowInMatch(
            String content,
            int startOffset) {
        int matchIndex = findLastKeywordBefore(content, "match", startOffset);
        if (matchIndex < 0) {
            return Optional.empty();
        }
        int open = skipWhitespace(content, matchIndex + "match".length());
        if (open >= content.length() || content.charAt(open) != '{') {
            return Optional.empty();
        }
        int close = findMatchingCloseCurlyBrace(content, open);
        int blockEndExclusive = close >= 0 ? close : content.length();
        int searchBound = Math.max(open, Math.min(blockEndExclusive, Math.max(0, startOffset)));
        int arrow = content.lastIndexOf("->", searchBound);
        if (arrow < open) {
            return Optional.empty();
        }
        int rhsStart = skipWhitespace(content, arrow + 2);
        if (rhsStart >= blockEndExclusive) {
            return Optional.of(new ParseFailureDescription(rhsStart, "expected expression after '->'"));
        }
        char marker = content.charAt(rhsStart);
        if (marker == ',' || marker == '}') {
            return Optional.of(new ParseFailureDescription(rhsStart, "expected expression after '->'"));
        }
        if (startsWithKeywordAt(content, rhsStart, "default")) {
            return Optional.of(new ParseFailureDescription(rhsStart, "expected expression after '->'"));
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeTrailingCommaBeforeMatchCloseBrace(
            String content,
            int startOffset) {
        int matchIndex = findLastKeywordBefore(content, "match", startOffset);
        if (matchIndex < 0) {
            return Optional.empty();
        }
        int open = skipWhitespace(content, matchIndex + "match".length());
        if (open >= content.length() || content.charAt(open) != '{') {
            return Optional.empty();
        }
        int close = findMatchingCloseCurlyBrace(content, open);
        if (close < 0) {
            return Optional.empty();
        }
        int prev = skipWhitespaceBackward(content, close - 1);
        if (prev < 0 || content.charAt(prev) != ',') {
            return Optional.empty();
        }
        return Optional.of(new ParseFailureDescription(prev, "unexpected trailing ',' before '}'"));
    }

    private Optional<ParseFailureDescription> describeMissingClosingCurlyBraceBeforeElse(
            String content,
            int startOffset) {
        int elseIndex = findLastKeywordBefore(content, "else", startOffset);
        if (elseIndex < 0) {
            return Optional.empty();
        }
        int previous = skipWhitespaceBackward(content, elseIndex - 1);
        if (previous < 0) {
            return Optional.empty();
        }
        if (content.charAt(previous) == '}') {
            return Optional.empty();
        }
        return Optional.of(new ParseFailureDescription(elseIndex, "expected '}'"));
    }

    private Optional<ParseFailureDescription> describeMissingGeneralClosingCurlyBrace(
            String content,
            int startOffset) {
        int unclosed = countUnclosedCurlyBraces(content);
        if (unclosed <= 0) {
            return Optional.empty();
        }
        int position = Math.max(0, Math.min(content.length(), startOffset));
        if (position < content.length()) {
            position = content.length();
        }
        return Optional.of(new ParseFailureDescription(position, "expected '}'"));
    }

    private Optional<ParseFailureDescription> describeMissingIfElseBlockOpeningCurlyBrace(String content) {
        int searchFrom = 0;
        while (searchFrom < content.length()) {
            int ifIndex = content.indexOf("if", searchFrom);
            if (ifIndex < 0) {
                break;
            }
            searchFrom = ifIndex + 2;
            if (isIdentifierPart(content, ifIndex - 1) || isIdentifierPart(content, ifIndex + 2)) {
                continue;
            }
            int open = skipWhitespace(content, ifIndex + 2);
            if (open >= content.length() || content.charAt(open) != '(') {
                continue;
            }
            int close = findMatchingCloseParenthesis(content, open);
            if (close < 0) {
                continue;
            }
            int blockStart = skipWhitespace(content, close + 1);
            if (blockStart < content.length() && content.charAt(blockStart) != '{') {
                return Optional.of(new ParseFailureDescription(blockStart, "expected '{'"));
            }
        }

        searchFrom = 0;
        while (searchFrom < content.length()) {
            int elseIndex = content.indexOf("else", searchFrom);
            if (elseIndex < 0) {
                break;
            }
            searchFrom = elseIndex + 4;
            if (isIdentifierPart(content, elseIndex - 1) || isIdentifierPart(content, elseIndex + 4)) {
                continue;
            }
            int blockStart = skipWhitespace(content, elseIndex + 4);
            if (blockStart < content.length() && content.charAt(blockStart) != '{') {
                return Optional.of(new ParseFailureDescription(blockStart, "expected '{'"));
            }
        }
        return Optional.empty();
    }

    private Optional<ParseFailureDescription> describeMissingExpressionAfterArrowAnywhere(String content) {
        int searchFrom = 0;
        while (searchFrom < content.length()) {
            int arrow = content.indexOf("->", searchFrom);
            if (arrow < 0) {
                return Optional.empty();
            }
            int rhsStart = skipWhitespace(content, arrow + 2);
            if (rhsStart >= content.length()) {
                return Optional.of(new ParseFailureDescription(rhsStart, "expected expression after '->'"));
            }
            char marker = content.charAt(rhsStart);
            if (marker == ',' || marker == '}') {
                return Optional.of(new ParseFailureDescription(rhsStart, "expected expression after '->'"));
            }
            searchFrom = arrow + 2;
        }
        return Optional.empty();
    }

    private int countUnclosedCurlyBraces(String content) {
        int open = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                open++;
            } else if (c == '}' && open > 0) {
                open--;
            }
        }
        return open;
    }

    private int findLastKeywordBefore(String content, String keyword, int endOffsetExclusive) {
        int bound = Math.max(0, Math.min(content.length(), endOffsetExclusive));
        int searchFrom = bound;
        while (searchFrom >= 0) {
            int found = content.lastIndexOf(keyword, searchFrom);
            if (found < 0) {
                return -1;
            }
            int end = found + keyword.length();
            if (isIdentifierPart(content, found - 1) == false
                    && isIdentifierPart(content, end) == false) {
                return found;
            }
            searchFrom = found - 1;
        }
        return -1;
    }

    private int findLastIfExpressionKeywordBefore(String content, int endOffsetExclusive) {
        int bound = Math.max(0, Math.min(content.length(), endOffsetExclusive));
        int searchFrom = bound;
        while (searchFrom >= 0) {
            int found = findLastKeywordBefore(content, "if", searchFrom);
            if (found < 0) {
                return -1;
            }
            int cursor = skipWhitespace(content, found + 2);
            if (cursor < content.length() && content.charAt(cursor) == '(') {
                return found;
            }
            searchFrom = found - 1;
        }
        return -1;
    }

    private boolean startsWithKeywordAt(String content, int start, String keyword) {
        if (start < 0 || start + keyword.length() > content.length()) {
            return false;
        }
        if (content.regionMatches(start, keyword, 0, keyword.length()) == false) {
            return false;
        }
        return isIdentifierPart(content, start - 1) == false
                && isIdentifierPart(content, start + keyword.length()) == false;
    }

    private int findMatchingCloseParenthesis(String content, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findMatchingCloseCurlyBrace(String content, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Optional<ParseFailureDescription> describeMissingBlockOpeningCurlyBrace(
            String content,
            int startOffset,
            List<String> mergedHints) {
        boolean expectsLeftCurlyBrace = mergedHints.stream().anyMatch(h -> tokenEquals(h, "{"));
        if (expectsLeftCurlyBrace == false) {
            return Optional.empty();
        }
        int cursor = skipWhitespace(content, Math.max(0, Math.min(content.length(), startOffset)));
        int tail = skipWhitespaceBackward(content, cursor - 1);
        if (tail < 0) {
            return Optional.empty();
        }
        if (endsWithWord(content, tail, "else") || endsWithWord(content, tail, "match")) {
            return Optional.of(new ParseFailureDescription(cursor, "expected '{'"));
        }
        if (content.charAt(tail) != ')') {
            return Optional.empty();
        }
        int openParenthesis = findMatchingOpenParenthesis(content, tail);
        if (openParenthesis < 0) {
            return Optional.empty();
        }
        int head = skipWhitespaceBackward(content, openParenthesis - 1);
        if (endsWithWord(content, head, "if")) {
            return Optional.of(new ParseFailureDescription(cursor, "expected '{'"));
        }
        return Optional.empty();
    }

    private int findMatchingOpenParenthesis(String content, int closeIndex) {
        int depth = 0;
        for (int i = closeIndex; i >= 0; i--) {
            char current = content.charAt(i);
            if (current == ')') {
                depth++;
            } else if (current == '(') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean endsWithWord(String content, int endInclusive, String word) {
        if (endInclusive < 0) {
            return false;
        }
        int start = endInclusive - word.length() + 1;
        if (start < 0) {
            return false;
        }
        if (content.regionMatches(start, word, 0, word.length()) == false) {
            return false;
        }
        return isIdentifierPart(content, start - 1) == false
                && isIdentifierPart(content, endInclusive + 1) == false;
    }

    private Optional<ParseFailureDescription> describeExpectedSingleStructuralToken(
            int startOffset,
            List<String> mergedHints) {
        List<String> structuralTokens = mergedHints.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(this::isStructuralTokenHint)
                .distinct()
                .toList();
        if (structuralTokens.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(new ParseFailureDescription(
                startOffset,
                "expected " + structuralTokens.get(0)));
    }

    private boolean isStructuralTokenHint(String hint) {
        return "';'".equals(hint)
                || "','".equals(hint)
                || "'{'".equals(hint)
                || "'}'".equals(hint)
                || "'('".equals(hint)
                || "')'".equals(hint)
                || "'->'".equals(hint);
    }

    private String readVariableToken(String content, int start) {
        if (start < 0 || start >= content.length() || content.charAt(start) != '$') {
            return "";
        }
        int i = start + 1;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || Character.isJavaIdentifierPart(c)) {
                i++;
                continue;
            }
            break;
        }
        return content.substring(start, i);
    }

    private boolean tokenEquals(String hint, String token) {
        if (hint == null) {
            return false;
        }
        String normalized = hint.strip();
        if (token.equals(normalized)) {
            return true;
        }
        return ("'" + token + "'").equals(normalized);
    }

    private Optional<Integer> detectMissingCommaBeforeDefaultOffset(String content) {
        int searchFrom = 0;
        while (searchFrom < content.length()) {
            int defaultIndex = content.indexOf("default", searchFrom);
            if (defaultIndex < 0) {
                return Optional.empty();
            }
            searchFrom = defaultIndex + "default".length();
            if (isIdentifierPart(content, defaultIndex - 1)
                    || isIdentifierPart(content, defaultIndex + "default".length())) {
                continue;
            }
            int afterDefault = skipWhitespace(content, searchFrom);
            if (!startsWithArrow(content, afterDefault)) {
                continue;
            }
            int matchIndex = content.lastIndexOf("match", defaultIndex);
            int matchBlockStart = matchIndex < 0 ? -1 : content.indexOf('{', matchIndex);
            if (matchBlockStart < 0 || matchBlockStart >= defaultIndex) {
                continue;
            }
            int prev = skipWhitespaceBackward(content, defaultIndex - 1);
            if (prev < 0) {
                continue;
            }
            char beforeDefault = content.charAt(prev);
            if (beforeDefault == ',' || beforeDefault == '{') {
                continue;
            }
            int insertionPoint = Math.min(content.length(), prev + 1);
            return Optional.of(insertionPoint);
        }
        return Optional.empty();
    }

    private Optional<Integer> detectMissingCommaBetweenMatchCasesOffset(String content, int startOffset) {
        int matchIndex = findLastKeywordBefore(content, "match", startOffset);
        if (matchIndex < 0) {
            return Optional.empty();
        }
        int matchOpen = skipWhitespace(content, matchIndex + "match".length());
        if (matchOpen >= content.length() || content.charAt(matchOpen) != '{') {
            return Optional.empty();
        }
        int matchClose = findMatchingCloseCurlyBrace(content, matchOpen);
        if (matchClose < 0) {
            return Optional.empty();
        }
        int cursor = matchOpen + 1;
        while (cursor < matchClose) {
            int lineStart = cursor;
            if (lineStart > matchOpen + 1) {
                char prev = content.charAt(lineStart - 1);
                if (prev != '\n' && prev != '\r') {
                    cursor++;
                    continue;
                }
            }
            int head = skipWhitespace(content, lineStart);
            if (head >= matchClose) {
                break;
            }
            int lineEnd = content.indexOf('\n', head);
            if (lineEnd < 0 || lineEnd > matchClose) {
                lineEnd = matchClose;
            }
            if (isSimpleMatchCaseHead(content, head, lineEnd)) {
                int previousNonWhitespace = skipWhitespaceBackward(content, head - 1);
                if (previousNonWhitespace >= 0) {
                    char separator = content.charAt(previousNonWhitespace);
                    if (separator != ',' && separator != '{') {
                        if (startsWithKeywordAt(content, head, "default")) {
                            return Optional.empty();
                        }
                        return Optional.of(head);
                    }
                }
            }
            if (lineEnd >= matchClose) {
                break;
            }
            cursor = lineEnd + 1;
        }
        return Optional.empty();
    }

    private boolean isSimpleMatchCaseHead(String content, int head, int lineEndExclusive) {
        if (head < 0 || head >= lineEndExclusive) {
            return false;
        }
        int arrow = content.indexOf("->", head);
        if (arrow < 0 || arrow >= lineEndExclusive) {
            return false;
        }
        if (content.charAt(head) == '$') {
            return true;
        }
        return startsWithKeywordAt(content, head, "true")
                || startsWithKeywordAt(content, head, "false")
                || startsWithKeywordAt(content, head, "default");
    }

    private boolean startsWithArrow(String content, int index) {
        return index + 1 < content.length()
                && content.charAt(index) == '-'
                && content.charAt(index + 1) == '>';
    }

    private String normalizeLineForRegex(String line) {
        if (line != null && line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    private int skipWhitespace(String content, int index) {
        int i = Math.max(0, index);
        while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
            i++;
        }
        return i;
    }

    private int skipWhitespaceBackward(String content, int index) {
        int i = Math.min(index, content.length() - 1);
        while (i >= 0 && Character.isWhitespace(content.charAt(i))) {
            i--;
        }
        return i;
    }

    private int findStatementStart(String content, int index) {
        int i = Math.min(index, content.length() - 1);
        while (i >= 0) {
            char c = content.charAt(i);
            if (c == ';' || c == '\n' || c == '\r') {
                return i + 1;
            }
            i--;
        }
        return 0;
    }

    private boolean isIdentifierPart(String content, int index) {
        if (index < 0 || index >= content.length()) {
            return false;
        }
        return Character.isLetterOrDigit(content.charAt(index)) || content.charAt(index) == '_';
    }

    private static record ParseFailureDescription(int startOffset, String message) {}
    private static record ArgumentShape(int argumentCount, boolean hasEmptySegment) {}

    /**
     * Check if a position is inside the range.
     */
    private static boolean isPositionInRange(Position position, Range range) {
        if (position.getLine() < range.getStart().getLine()) {
            return false;
        }
        if (position.getLine() > range.getEnd().getLine()) {
            return false;
        }
        if (position.getLine() == range.getStart().getLine()
                && position.getCharacter() < range.getStart().getCharacter()) {
            return false;
        }
        if (position.getLine() == range.getEnd().getLine()
                && position.getCharacter() > range.getEnd().getCharacter()) {
            return false;
        }
        return true;
    }

    /**
     * Convert character offset to LSP Position.
     */
    private Position offsetToPosition(String content, int offset) {
        int line = 0;
        int column = 0;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new Position(line, column);
    }

    /**
     * Document state holder.
     */
    public static class DocumentState {
        public final String uri;
        public final String content;
        public final ParseResult parseResult;
        public final CalculatorAstAnalyzer.AnalysisResult analysis;

        public DocumentState(String uri, String content, ParseResult parseResult,
                CalculatorAstAnalyzer.AnalysisResult analysis) {
            this.uri = uri;
            this.content = content;
            this.parseResult = parseResult;
            this.analysis = analysis;
        }
    }

    /**
     * Parse result holder.
     */
    public static class ParseResult {
        public final boolean succeeded;
        public final int consumedLength;
        public final int totalLength;
        public final Parsed parsed;
        public final Object failureDiagnostics;

        public ParseResult(
                boolean succeeded,
                int consumedLength,
                int totalLength,
                Parsed parsed,
                Object failureDiagnostics) {
            this.succeeded = succeeded;
            this.consumedLength = consumedLength;
            this.totalLength = totalLength;
            this.parsed = parsed;
            this.failureDiagnostics = failureDiagnostics;
        }

        public boolean isFullyValid() {
            return succeeded && consumedLength == totalLength;
        }
    }

    /**
     * Text document service implementation.
     */
    public static class CalculatorTextDocumentService implements TextDocumentService {

        private final CalculatorLanguageServer server;

        public CalculatorTextDocumentService(CalculatorLanguageServer server) {
            this.server = server;
        }

        @Override
        public void didOpen(DidOpenTextDocumentParams params) {
            String uri = params.getTextDocument().getUri();
            String content = params.getTextDocument().getText();
            server.parseDocument(uri, content);
        }

        @Override
        public void didChange(DidChangeTextDocumentParams params) {
            String uri = params.getTextDocument().getUri();
            String content = params.getContentChanges().get(0).getText();
            server.parseDocument(uri, content);
        }

        @Override
        public void didClose(DidCloseTextDocumentParams params) {
            server.getDocuments().remove(params.getTextDocument().getUri());
        }

        @Override
        public void didSave(DidSaveTextDocumentParams params) {
            // No special handling needed
        }

        @Override
        public CompletableFuture<Hover> hover(HoverParams params) {
            String uri = params.getTextDocument().getUri();
            Position position = params.getPosition();

            DocumentState state = server.getDocuments().get(uri);
            if (state == null) {
                return CompletableFuture.completedFuture(null);
            }

            String hoverText = null;
            for (CalculatorAstAnalyzer.AstError error : state.analysis.errors()) {
                if (isPositionInRange(position, error.range())) {
                    hoverText = error.message();
                    break;
                }
            }

            if (hoverText == null && state.analysis.hasValue()) {
                hoverText = "= " + state.analysis.value();
            }

            if (hoverText == null) {
                return CompletableFuture.completedFuture(null);
            }

            MarkupContent content = new MarkupContent();
            content.setKind("plaintext");
            content.setValue(hoverText);
            Hover hover = new Hover(content);
            return CompletableFuture.completedFuture(hover);
        }

        @Override
        public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
            String uri = params.getTextDocument().getUri();
            DocumentState state = server.getDocuments().get(uri);
            if (state == null) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            if (state.content.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            String title = null;
            if (false == state.analysis.errors().isEmpty()) {
                title = "Error: " + state.analysis.errors().get(0).message();
            } else if (state.analysis.hasValue()) {
                title = "= " + state.analysis.value();
            }

            if (title == null) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            Range range = new Range(new Position(0, 0), new Position(0, 0));
            Command command = new Command(title, "calculator.showResult");
            CodeLens lens = new CodeLens(range);
            lens.setCommand(command);
            return CompletableFuture.completedFuture(List.of(lens));
        }

        @Override
        public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
                CompletionParams params) {

            String uri = params.getTextDocument().getUri();
            Position position = params.getPosition();

            DocumentState state = server.getDocuments().get(uri);
            if (state == null) {
                return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
            }

            List<CompletionItem> items = getCompletionItems(state.content, position);
            return CompletableFuture.completedFuture(Either.forLeft(items));
        }

        @Override
        public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
            String uri = params.getTextDocument().getUri();
            DocumentState state = server.getDocuments().get(uri);
            if (state == null) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            List<Either<Command, CodeAction>> actions = new ArrayList<>();
            for (Diagnostic diagnostic : params.getContext().getDiagnostics()) {
                String code = diagnosticCode(diagnostic);
                if (code == null) {
                    code = inferCatalogCodeFromMessage(diagnostic.getMessage());
                    if (code == null) {
                        continue;
                    }
                }
                switch (code) {
                    case "TE004":
                        createTe004ClosingParenthesisFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE005":
                        createInsertFix(actions, uri, state.content, diagnostic, "}", "閉じ波括弧 '}' を追加");
                        break;
                    case "TE006":
                        createTe006SemicolonFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE021":
                        createUnknownMethodRenameFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE022":
                        createTe022VariableRenameFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE023":
                        createTe023NotationFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE024":
                        createTe024PartialKeySuffixFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE003":
                        createTe003StringQuoteFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE007":
                        createTe007DescriptionQuoteFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE016":
                        createTe016ImportDeclarationFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE017":
                        createTe017VariableDeclarationFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE018":
                        createTe018TypeHintPositionFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE019":
                        createTe019GetOrElseFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE008":
                        createTe008NormalizePunctuationFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE010":
                        createTe010StructuralFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE013":
                        createTe013MatchFix(actions, uri, state.content, diagnostic);
                        break;
                    case "TE009":
                        createTe009HeuristicFix(actions, uri, state.content, diagnostic);
                        break;
                    default:
                        break;
                }
            }
            return CompletableFuture.completedFuture(actions);
        }

        private void createInsertFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic,
                String insertText,
                String title) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            Position insertPosition = diagnostic.getRange().getStart();
            int offset = positionToOffset(content, insertPosition);
            if (offset < content.length() && content.startsWith(insertText, offset)) {
                return;
            }
            if (";".equals(insertText) && offset > 0 && content.charAt(offset - 1) == ';') {
                return;
            }

            TextEdit edit = new TextEdit(new Range(insertPosition, insertPosition), insertText);
            Map<String, List<TextEdit>> changes = new HashMap<>();
            changes.put(uri, List.of(edit));
            WorkspaceEdit workspaceEdit = new WorkspaceEdit();
            workspaceEdit.setChanges(changes);

            CodeAction codeAction = new CodeAction(title);
            codeAction.setKind(CodeActionKind.QuickFix);
            codeAction.setDiagnostics(List.of(diagnostic));
            codeAction.setEdit(workspaceEdit);
            actions.add(Either.forRight(codeAction));
        }

        private void createTe004ClosingParenthesisFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            int startOffset = positionToOffset(content, diagnostic.getRange().getStart());
            if (startOffset < 0 || startOffset >= content.length()) {
                return;
            }
            int openParen = content.indexOf('(', startOffset);
            if (openParen < 0) {
                createInsertFix(actions, uri, content, diagnostic, ")", "閉じ括弧 ')' を追加");
                return;
            }
            int closeParen = server.findMatchingCloseParenthesis(content, openParen);
            if (closeParen >= 0) {
                createInsertFix(actions, uri, content, diagnostic, ")", "閉じ括弧 ')' を追加");
                return;
            }
            int lineEnd = content.indexOf('\n', openParen);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            TextEdit edit = new TextEdit(
                    new Range(server.offsetToPosition(content, lineEnd), server.offsetToPosition(content, lineEnd)),
                    ")");
            addQuickFix(actions, uri, diagnostic, edit, "閉じ括弧 ')' を追加");
        }

        private void createTe006SemicolonFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            int offset = server.detectMissingSemicolonAfterVarDeclarationByLine(content)
                    .orElseGet(() -> {
                        if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                            return -1;
                        }
                        return positionToOffset(content, diagnostic.getRange().getStart());
                    });
            if (offset < 0 || offset > content.length()) {
                return;
            }
            int insertAt = offset;
            if (insertAt > 0 && insertAt <= content.length()) {
                int prev = insertAt - 1;
                while (prev >= 0 && (content.charAt(prev) == '\r' || content.charAt(prev) == '\n')) {
                    insertAt = prev;
                    prev--;
                }
            }
            if (insertAt > 0 && content.charAt(insertAt - 1) == ';') {
                return;
            }
            TextEdit edit = new TextEdit(
                    new Range(server.offsetToPosition(content, insertAt), server.offsetToPosition(content, insertAt)),
                    ";");
            addQuickFix(actions, uri, diagnostic, edit, "セミコロン ';' を追加");
        }

        private void createTe013MatchFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            String message = diagnostic.getMessage() == null ? "" : diagnostic.getMessage();
            Position start = diagnostic.getRange().getStart();
            int offset = positionToOffset(content, start);
            if (offset < 0 || offset > content.length()) {
                return;
            }
            if (message.contains("区切りに ',' が必要") || message.contains("missing ','")) {
                TextEdit edit = new TextEdit(new Range(start, start), ",");
                addQuickFix(actions, uri, diagnostic, edit, "match case の区切り ',' を追加");
                return;
            }
            Optional<Integer> missingRhs = findArrowWithMissingRhs(content);
            if (missingRhs.isPresent()) {
                int insertAt = missingRhs.get();
                TextEdit edit = new TextEdit(
                        new Range(server.offsetToPosition(content, insertAt), server.offsetToPosition(content, insertAt)),
                        " 0");
                addQuickFix(actions, uri, diagnostic, edit, "-> の右辺式を補完");
            }
        }

        private void createTe009HeuristicFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            createMissingIfBlockBraceFix(actions, uri, content, diagnostic);
            Optional<Integer> missingRhs = findArrowWithMissingRhs(content);
            if (missingRhs.isPresent()) {
                int insertAt = missingRhs.get();
                TextEdit edit = new TextEdit(
                        new Range(server.offsetToPosition(content, insertAt), server.offsetToPosition(content, insertAt)),
                        " 0");
                addQuickFix(actions, uri, diagnostic, edit, "-> の右辺式を補完");
            }
            Optional<Integer> missingComma = server.detectMissingCommaBetweenMatchCasesOffset(content, content.length());
            if (missingComma.isPresent()) {
                Position insertPos = server.offsetToPosition(content, missingComma.get());
                TextEdit edit = new TextEdit(new Range(insertPos, insertPos), ",");
                addQuickFix(actions, uri, diagnostic, edit, "match case の区切り ',' を追加");
            }
        }

        private void createTe010StructuralFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            createTe009HeuristicFix(actions, uri, content, diagnostic);
            createInsertFix(actions, uri, content, diagnostic, "{", "ブロック開始 '{' を追加");
        }

        private void createMissingIfBlockBraceFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            int ifIndex = content.lastIndexOf("if(");
            if (ifIndex < 0) {
                ifIndex = content.lastIndexOf("if (");
            }
            if (ifIndex < 0) {
                return;
            }
            int open = content.indexOf('(', ifIndex);
            if (open < 0) {
                return;
            }
            int close = server.findMatchingCloseParenthesis(content, open);
            if (close < 0) {
                return;
            }
            int next = skipWhitespaceForward(content, close + 1);
            if (next < content.length() && content.charAt(next) == '{') {
                return;
            }
            if (next < content.length() && content.charAt(next) == ')') {
                TextEdit edit = new TextEdit(
                        new Range(server.offsetToPosition(content, next), server.offsetToPosition(content, next + 1)),
                        "{");
                addQuickFix(actions, uri, diagnostic, edit, "if ブロック開始 '{' に修正");
                return;
            }
            TextEdit edit = new TextEdit(
                    new Range(server.offsetToPosition(content, close + 1), server.offsetToPosition(content, close + 1)),
                    "{");
            addQuickFix(actions, uri, diagnostic, edit, "if ブロック開始 '{' を追加");
        }

        private Optional<Integer> findArrowWithMissingRhs(String content) {
            int search = 0;
            while (search < content.length()) {
                int arrow = content.indexOf("->", search);
                if (arrow < 0) {
                    return Optional.empty();
                }
                int rhs = skipWhitespaceForward(content, arrow + 2);
                if (rhs >= content.length()) {
                    return Optional.of(arrow + 2);
                }
                char c = content.charAt(rhs);
                if (c == ',' || c == '}' || c == ')' || c == ';') {
                    return Optional.of(arrow + 2);
                }
                search = arrow + 2;
            }
            return Optional.empty();
        }

        private void createUnknownMethodRenameFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getMessage() == null || diagnostic.getRange() == null) {
                return;
            }
            String suggestion = extractMethodSuggestionFromDiagnostic(diagnostic.getMessage());
            if (suggestion == null || suggestion.isBlank()) {
                return;
            }
            Position start = diagnostic.getRange().getStart();
            Position end = diagnostic.getRange().getEnd();
            if (start == null || end == null) {
                return;
            }
            int startOffset = positionToOffset(content, start);
            int endOffset = positionToOffset(content, end);
            if (startOffset < 0 || endOffset < startOffset || endOffset > content.length()) {
                return;
            }
            if (content.substring(startOffset, endOffset).equals(suggestion)) {
                return;
            }
            TextEdit edit = new TextEdit(new Range(start, end), suggestion);
            addQuickFix(actions, uri, diagnostic, edit, "メソッド名を '" + suggestion + "' に修正");
        }

        private void createTe023NotationFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            Position start = diagnostic.getRange().getStart();
            int offset = positionToOffset(content, start);
            if (offset < 0 || offset >= content.length()) {
                return;
            }
            if (offset + 1 < content.length() && content.startsWith("&&", offset)) {
                TextEdit edit = new TextEdit(
                        new Range(start, server.offsetToPosition(content, offset + 2)),
                        "&");
                addQuickFix(actions, uri, diagnostic, edit, "&& を & に修正");
                return;
            }
            if (offset + 1 < content.length() && content.startsWith("||", offset)) {
                TextEdit edit = new TextEdit(
                        new Range(start, server.offsetToPosition(content, offset + 2)),
                        "|");
                addQuickFix(actions, uri, diagnostic, edit, "|| を | に修正");
                return;
            }
            if (content.charAt(offset) == '$') {
                int methodStart = skipWhitespaceForward(content, offset + 1);
                if (methodStart < content.length() && Character.isJavaIdentifierStart(content.charAt(methodStart))) {
                    int methodEnd = methodStart + 1;
                    while (methodEnd < content.length() && Character.isJavaIdentifierPart(content.charAt(methodEnd))) {
                        methodEnd++;
                    }
                    int next = skipWhitespaceForward(content, methodEnd);
                    if (next < content.length() && content.charAt(next) == '(') {
                        TextEdit edit = new TextEdit(
                                new Range(start, server.offsetToPosition(content, offset + 1)),
                                "");
                        addQuickFix(actions, uri, diagnostic, edit, "$ を除去してメソッド呼び出しに修正");
                    }
                }
                return;
            }

            char current = content.charAt(offset);
            if (current == '&' || current == '|') {
                int rhs = skipWhitespaceForward(content, offset + 1);
                boolean missingRhs = rhs >= content.length();
                if (missingRhs == false) {
                    char rhsChar = content.charAt(rhs);
                    missingRhs = rhsChar == ')' || rhsChar == '}' || rhsChar == ';' || rhsChar == ',';
                }
                if (missingRhs) {
                    Position insertAt = server.offsetToPosition(content, offset + 1);
                    TextEdit edit = new TextEdit(new Range(insertAt, insertAt), " true");
                    addQuickFix(actions, uri, diagnostic, edit, "演算子の右辺に true を補完");
                }
            }
        }

        private int skipWhitespaceForward(String content, int offset) {
            int i = Math.max(0, offset);
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            return i;
        }

        private String extractMethodSuggestionFromDiagnostic(String message) {
            Matcher matcher = Pattern.compile("候補:\\s*([A-Za-z_][A-Za-z0-9_]*)").matcher(message);
            if (matcher.find() == false) {
                return null;
            }
            return matcher.group(1);
        }

        private String extractVariableSuggestionFromDiagnostic(String message) {
            Matcher matcher = Pattern.compile("候補:\\s*(\\$[\\p{L}_][\\p{L}\\p{N}_]*(?:_<suffix>)?)").matcher(message);
            if (matcher.find() == false) {
                return null;
            }
            return matcher.group(1);
        }

        private void addQuickFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                Diagnostic diagnostic,
                TextEdit edit,
                String title) {
            Map<String, List<TextEdit>> changes = new HashMap<>();
            changes.put(uri, List.of(edit));
            WorkspaceEdit workspaceEdit = new WorkspaceEdit();
            workspaceEdit.setChanges(changes);

            CodeAction codeAction = new CodeAction(title);
            codeAction.setKind(CodeActionKind.QuickFix);
            codeAction.setDiagnostics(List.of(diagnostic));
            codeAction.setEdit(workspaceEdit);
            actions.add(Either.forRight(codeAction));
        }

        private void createTe024PartialKeySuffixFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null
                    || diagnostic.getRange().getStart() == null
                    || diagnostic.getRange().getEnd() == null) {
                return;
            }
            int startOffset = positionToOffset(content, diagnostic.getRange().getStart());
            int endOffset = positionToOffset(content, diagnostic.getRange().getEnd());
            if (startOffset < 0 || endOffset <= startOffset || endOffset > content.length()) {
                return;
            }
            String token = content.substring(startOffset, endOffset);
            if (token.matches("\\$[\\p{L}_][\\p{L}\\p{N}_]*") == false) {
                return;
            }
            if (token.contains("_")) {
                return;
            }
            TextEdit edit = new TextEdit(
                    new Range(diagnostic.getRange().getStart(), diagnostic.getRange().getEnd()),
                    token + "_<suffix>");
            Map<String, List<TextEdit>> changes = new HashMap<>();
            changes.put(uri, List.of(edit));
            WorkspaceEdit workspaceEdit = new WorkspaceEdit();
            workspaceEdit.setChanges(changes);

            CodeAction codeAction = new CodeAction("partialKey 変数に suffix を追加");
            codeAction.setKind(CodeActionKind.QuickFix);
            codeAction.setDiagnostics(List.of(diagnostic));
            codeAction.setEdit(workspaceEdit);
            actions.add(Either.forRight(codeAction));
        }

        private void createTe022VariableRenameFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getMessage() == null || diagnostic.getRange() == null) {
                return;
            }
            String suggestion = extractVariableSuggestionFromDiagnostic(diagnostic.getMessage());
            if (suggestion == null || suggestion.isBlank()) {
                return;
            }
            Position start = diagnostic.getRange().getStart();
            Position end = diagnostic.getRange().getEnd();
            if (start == null || end == null) {
                return;
            }
            int startOffset = positionToOffset(content, start);
            int endOffset = positionToOffset(content, end);
            if (startOffset < 0 || endOffset < startOffset || endOffset > content.length()) {
                return;
            }
            if (content.substring(startOffset, endOffset).equals(suggestion)) {
                return;
            }
            TextEdit edit = new TextEdit(new Range(start, end), suggestion);
            addQuickFix(actions, uri, diagnostic, edit, "変数名を '" + suggestion + "' に修正");
        }

        private void createTe003StringQuoteFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            int startOffset = positionToOffset(content, diagnostic.getRange().getStart());
            if (startOffset < 0 || startOffset >= content.length()) {
                return;
            }
            int firstQuote = content.indexOf('"', startOffset);
            if (firstQuote < 0) {
                return;
            }
            int secondQuote = content.indexOf('"', firstQuote + 1);
            List<TextEdit> edits = new ArrayList<>();
            edits.add(new TextEdit(
                    new Range(server.offsetToPosition(content, firstQuote), server.offsetToPosition(content, firstQuote + 1)),
                    "'"));
            if (secondQuote > firstQuote) {
                edits.add(new TextEdit(
                        new Range(server.offsetToPosition(content, secondQuote), server.offsetToPosition(content, secondQuote + 1)),
                        "'"));
            }
            Map<String, List<TextEdit>> changes = new HashMap<>();
            changes.put(uri, edits);
            WorkspaceEdit workspaceEdit = new WorkspaceEdit();
            workspaceEdit.setChanges(changes);

            CodeAction codeAction = new CodeAction("文字列クォートを '...' に修正");
            codeAction.setKind(CodeActionKind.QuickFix);
            codeAction.setDiagnostics(List.of(diagnostic));
            codeAction.setEdit(workspaceEdit);
            actions.add(Either.forRight(codeAction));
        }

        private void createTe007DescriptionQuoteFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            int startOffset = positionToOffset(content, diagnostic.getRange().getStart());
            if (startOffset < 0 || startOffset > content.length()) {
                return;
            }
            int lineStart = content.lastIndexOf('\n', Math.max(0, startOffset - 1));
            lineStart = lineStart < 0 ? 0 : lineStart + 1;
            int lineEnd = content.indexOf('\n', startOffset);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(lineStart, lineEnd);
            Matcher matcher = Pattern.compile("\\bdescription\\s*=\\s*'[^']*$").matcher(line);
            if (matcher.find() == false) {
                return;
            }
            int insertOffset = lineEnd;
            int semicolonAtEnd = line.lastIndexOf(';');
            if (semicolonAtEnd >= 0 && line.substring(semicolonAtEnd).trim().equals(";")) {
                insertOffset = lineStart + semicolonAtEnd;
            }
            TextEdit edit = new TextEdit(
                    new Range(server.offsetToPosition(content, insertOffset), server.offsetToPosition(content, insertOffset)),
                    "'");
            addQuickFix(actions, uri, diagnostic, edit, "description のクォートを閉じる");
        }

        private void createTe016ImportDeclarationFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            int startOffset = positionToOffset(content, diagnostic.getRange().getStart());
            if (startOffset < 0 || startOffset > content.length()) {
                return;
            }
            int lineStart = content.lastIndexOf('\n', Math.max(0, startOffset - 1));
            lineStart = lineStart < 0 ? 0 : lineStart + 1;
            int lineEnd = content.indexOf('\n', startOffset);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(lineStart, lineEnd);
            Matcher matcher = Pattern.compile("^(\\s*)import\\s+([^;]+?)\\s*;?\\s*$").matcher(line);
            if (matcher.find() == false) {
                return;
            }
            String indent = matcher.group(1) == null ? "" : matcher.group(1);
            String importBody = matcher.group(2) == null ? "" : matcher.group(2).trim();
            if (importBody.isBlank() || importBody.contains(" as ")) {
                return;
            }
            String alias = deriveImportAlias(importBody);
            String replacement = indent + "import " + importBody + " as " + alias + ";";
            TextEdit edit = new TextEdit(
                    new Range(server.offsetToPosition(content, lineStart), server.offsetToPosition(content, lineEnd)),
                    replacement);
            addQuickFix(actions, uri, diagnostic, edit, "import に alias を追加");
        }

        private String deriveImportAlias(String importBody) {
            if (importBody == null || importBody.isBlank()) {
                return "alias";
            }
            String candidate = importBody;
            int hash = candidate.lastIndexOf('#');
            if (hash >= 0 && hash + 1 < candidate.length()) {
                candidate = candidate.substring(hash + 1).trim();
            } else {
                int dot = candidate.lastIndexOf('.');
                if (dot >= 0 && dot + 1 < candidate.length()) {
                    candidate = candidate.substring(dot + 1).trim();
                }
            }
            candidate = candidate.replaceAll("[^A-Za-z0-9_]", "");
            if (candidate.isBlank()) {
                return "alias";
            }
            if (Character.isJavaIdentifierStart(candidate.charAt(0)) == false) {
                candidate = "_" + candidate;
            }
            StringBuilder normalized = new StringBuilder();
            for (int i = 0; i < candidate.length(); i++) {
                char c = candidate.charAt(i);
                normalized.append(Character.isJavaIdentifierPart(c) ? c : '_');
            }
            return normalized.isEmpty() ? "alias" : normalized.toString();
        }

        private void createTe018TypeHintPositionFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            int startOffset = positionToOffset(content, diagnostic.getRange().getStart());
            if (startOffset < 0 || startOffset > content.length()) {
                return;
            }
            int lineStart = content.lastIndexOf('\n', Math.max(0, startOffset - 1));
            lineStart = lineStart < 0 ? 0 : lineStart + 1;
            int lineEnd = content.indexOf('\n', startOffset);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(lineStart, lineEnd);
            Matcher matcher = Pattern.compile(
                    "\\bas\\s+(Number|number|Float|float|String|string|Boolean|boolean|Object|object)\\s+(\\$[\\p{L}_][\\p{L}\\p{N}_]*)")
                    .matcher(line);
            if (matcher.find() == false) {
                return;
            }
            String type = matcher.group(1);
            String variable = matcher.group(2);
            String replacement = variable + " as " + type;
            int start = lineStart + matcher.start();
            int end = lineStart + matcher.end();
            TextEdit edit = new TextEdit(
                    new Range(server.offsetToPosition(content, start), server.offsetToPosition(content, end)),
                    replacement);
            addQuickFix(actions, uri, diagnostic, edit, "型ヒント位置を '$name as type' に修正");
        }

        private void createTe019GetOrElseFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            int startOffset = positionToOffset(content, diagnostic.getRange().getStart());
            if (startOffset < 0 || startOffset > content.length()) {
                return;
            }
            int searchStart = Math.max(0, startOffset - 128);
            int searchEnd = Math.min(content.length(), startOffset + 256);
            String window = content.substring(searchStart, searchEnd);

            int relOrElse = window.indexOf(".orElse");
            if (relOrElse >= 0) {
                int orElseOffset = searchStart + relOrElse;
                int afterOrElse = orElseOffset + ".orElse".length();
                int next = skipWhitespaceForward(content, afterOrElse);
                if (next >= content.length() || content.charAt(next) != '(') {
                    TextEdit edit = new TextEdit(
                            new Range(server.offsetToPosition(content, afterOrElse), server.offsetToPosition(content, afterOrElse)),
                            "(0)");
                    addQuickFix(actions, uri, diagnostic, edit, "orElse の引数を補完");
                    return;
                }
                int close = server.findMatchingCloseParenthesis(content, next);
                if (close < 0) {
                    int lineEnd = content.indexOf('\n', next);
                    if (lineEnd < 0) {
                        lineEnd = content.length();
                    }
                    int insertOffset = lineEnd;
                    int semicolon = content.indexOf(';', next);
                    if (semicolon >= 0 && semicolon < lineEnd) {
                        insertOffset = semicolon;
                    }
                    TextEdit edit = new TextEdit(
                            new Range(server.offsetToPosition(content, insertOffset), server.offsetToPosition(content, insertOffset)),
                            ")");
                    addQuickFix(actions, uri, diagnostic, edit, "orElse の閉じ括弧 ')' を追加");
                    return;
                }
            }

            int getHead = content.lastIndexOf("get(", startOffset);
            if (getHead < 0 && window.contains("get(")) {
                getHead = searchStart + window.indexOf("get(");
            }
            if (getHead >= 0) {
                int getOpen = content.indexOf('(', getHead);
                int getClose = getOpen < 0 ? -1 : server.findMatchingCloseParenthesis(content, getOpen);
                if (getClose >= 0) {
                    int afterGet = skipWhitespaceForward(content, getClose + 1);
                    if (afterGet >= content.length() || content.startsWith(".orElse", afterGet) == false) {
                        TextEdit edit = new TextEdit(
                                new Range(server.offsetToPosition(content, getClose + 1), server.offsetToPosition(content, getClose + 1)),
                                ".orElse(0)");
                        addQuickFix(actions, uri, diagnostic, edit, "get(...) に orElse(...) を追加");
                    }
                }
            }
        }

        private void createTe008NormalizePunctuationFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            String normalized = content
                    .replace('；', ';')
                    .replace('（', '(')
                    .replace('）', ')')
                    .replace('，', ',')
                    .replace('｛', '{')
                    .replace('｝', '}')
                    .replace('：', ':')
                    .replace('＄', '$')
                    .replace('”', '\'')
                    .replace('’', '\'')
                    .replace('　', ' ');
            if (normalized.equals(content)) {
                return;
            }
            TextEdit edit = new TextEdit(
                    new Range(new Position(0, 0), server.offsetToPosition(content, content.length())),
                    normalized);
            addQuickFix(actions, uri, diagnostic, edit, "全角記号を半角へ正規化");
        }

        private void createTe017VariableDeclarationFix(
                List<Either<Command, CodeAction>> actions,
                String uri,
                String content,
                Diagnostic diagnostic) {
            if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
                return;
            }
            int startOffset = positionToOffset(content, diagnostic.getRange().getStart());
            if (startOffset < 0 || startOffset > content.length()) {
                return;
            }
            int lineStart = content.lastIndexOf('\n', Math.max(0, startOffset - 1));
            lineStart = lineStart < 0 ? 0 : lineStart + 1;
            int lineEnd = content.indexOf('\n', startOffset);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(lineStart, lineEnd);
            Matcher matcher = Pattern.compile("^(\\s*(?:var|variable)\\s+)([\\p{L}_][\\p{L}\\p{N}_]*)").matcher(line);
            if (matcher.find() == false) {
                return;
            }
            int variableStart = lineStart + matcher.start(2);
            if (variableStart < content.length() && content.charAt(variableStart) == '$') {
                return;
            }
            TextEdit edit = new TextEdit(
                    new Range(server.offsetToPosition(content, variableStart), server.offsetToPosition(content, variableStart)),
                    "$");
            addQuickFix(actions, uri, diagnostic, edit, "変数名に '$' を追加");
        }

        private String diagnosticCode(Diagnostic diagnostic) {
            if (diagnostic == null || diagnostic.getCode() == null) {
                return null;
            }
            Either<String, Integer> code = diagnostic.getCode();
            if (code.isLeft()) {
                return code.getLeft();
            }
            return String.valueOf(code.getRight());
        }

        private String inferCatalogCodeFromMessage(String message) {
            if (message == null || message.isBlank()) {
                return null;
            }
            if (message.contains("開き括弧が閉じられていません")
                    || message.contains("丸カッコが閉じられていません")) {
                return "TE004";
            }
            if (message.contains("波カッコが閉じられていません")) {
                return "TE005";
            }
            return null;
        }

        /**
         * Get completion items based on current position.
         */
        private List<CompletionItem> getCompletionItems(String content, Position position) {
            List<CompletionItem> items = new ArrayList<>();

            for (SuggestableParser.Suggestion suggestion : server.suggestableParser.suggest(content, position)) {
                CompletionItem item = new CompletionItem(suggestion.label());
                item.setKind(suggestion.kind());
                item.setDetail(suggestion.detail());
                item.setInsertText(suggestion.insertText());
                item.setInsertTextFormat(suggestion.insertTextFormat());
                items.add(item);
            }

            if (items.isEmpty()) {
                items.addAll(getParseFailureHintCompletions(content, position));
            }

            return items;
        }

        private List<CompletionItem> getParseFailureHintCompletions(String content, Position position) {
            ParseResult parseResult = server.parseExpression(content);
            if (server.failureHasFailureCandidate(parseResult.failureDiagnostics) == false) {
                return Collections.emptyList();
            }
            int cursorOffset = positionToOffset(content, position);
            int farthestOffset = server.failureFarthestOffset(parseResult.failureDiagnostics);
            if (cursorOffset + 2 < farthestOffset) {
                return Collections.emptyList();
            }

            List<CompletionItem> items = new ArrayList<>();
            List<String> added = new ArrayList<>();
            for (Object candidate : server.failureExpectedHintCandidates(parseResult.failureDiagnostics)) {
                String insertText = toCompletionToken(server.expectedHintDisplay(candidate));
                if (insertText.isBlank()) {
                    continue;
                }
                if (added.contains(insertText)) {
                    continue;
                }
                added.add(insertText);

                CompletionItem item = new CompletionItem(insertText);
                item.setInsertText(insertText);
                item.setKind(toCompletionKind(insertText));
                item.setDetail("expected by " + server.expectedHintParserClassName(candidate));
                item.setSortText(String.format("%03d_%s", server.expectedHintParserDepth(candidate), insertText));
                items.add(item);
            }
            return items;
        }

        private String toCompletionToken(String displayHint) {
            if (displayHint == null) {
                return "";
            }
            String normalized = displayHint.strip();
            if (normalized.length() >= 2 && normalized.startsWith("'") && normalized.endsWith("'")) {
                return normalized.substring(1, normalized.length() - 1);
            }
            return "";
        }

        private CompletionItemKind toCompletionKind(String token) {
            if (token.isEmpty()) {
                return CompletionItemKind.Text;
            }
            char first = token.charAt(0);
            if (Character.isLetter(first) || first == '_') {
                return CompletionItemKind.Keyword;
            }
            return CompletionItemKind.Operator;
        }

        /**
         * Convert LSP Position to character offset.
         */
        private int positionToOffset(String content, Position position) {
            int offset = 0;
            int line = 0;
            int column = 0;

            while (offset < content.length()) {
                if (line == position.getLine() && column == position.getCharacter()) {
                    return offset;
                }
                if (content.charAt(offset) == '\n') {
                    line++;
                    column = 0;
                } else {
                    column++;
                }
                offset++;
            }

            return offset;
        }

        @Override
        public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
            String uri = params.getTextDocument().getUri();
            DocumentState state = server.getDocuments().get(uri);

            if (state == null) {
                return CompletableFuture.completedFuture(new SemanticTokens(Collections.emptyList()));
            }

            List<Integer> data = buildSemanticTokens(state.content, state.parseResult);
            return CompletableFuture.completedFuture(new SemanticTokens(data));
        }

        /**
         * Build semantic tokens data.
         * Format: [deltaLine, deltaStart, length, tokenType, tokenModifiers]
         * tokenType: 0=valid, 1=invalid
         */
        private List<Integer> buildSemanticTokens(String content, ParseResult result) {
            List<Integer> data = new ArrayList<>();

            if (content.isEmpty()) {
                return data;
            }

            int validEnd = result.consumedLength;

            // Valid portion (green)
            if (validEnd > 0) {
                // deltaLine=0, deltaStart=0, length=validEnd, tokenType=0 (valid), modifiers=0
                data.add(0);
                data.add(0);
                data.add(validEnd);
                data.add(0); // valid
                data.add(0);
            }

            // Invalid portion (red)
            if (validEnd < content.length()) {
                int invalidLength = content.length() - validEnd;
                // deltaLine=0, deltaStart=validEnd (relative to previous), length, tokenType=1 (invalid)
                data.add(0);
                data.add(validEnd);
                data.add(invalidLength);
                data.add(1); // invalid
                data.add(0);
            }

            return data;
        }
    }

    /**
     * Workspace service implementation.
     */
    public static class CalculatorWorkspaceService implements WorkspaceService {
        @Override
        public void didChangeConfiguration(org.eclipse.lsp4j.DidChangeConfigurationParams params) {
        }

        @Override
        public void didChangeWatchedFiles(org.eclipse.lsp4j.DidChangeWatchedFilesParams params) {
        }
    }


@Override
public void setTrace(SetTraceParams params) {
    // VS Code sends $/setTrace notifications; ignoring is sufficient.
}
}
