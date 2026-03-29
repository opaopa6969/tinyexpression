package org.unlaxer.tinyexpression.lsp.p4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.incremental.IncrementalParseCache;
import org.unlaxer.parser.clang.CPPComment;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.elementary.NumberParser;
import org.unlaxer.parser.elementary.SingleQuotedParser;
import org.unlaxer.parser.posix.SpaceParser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4LanguageServer;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;
import org.unlaxer.dsl.runtime.ScopeStore;

/**
 * Extended LSP server for TinyExpression P4.
 * <p>
 * Extends the generated {@link TinyExpressionP4LanguageServer} with:
 * <ul>
 *   <li>Type-safe semantic token classification via the token tree (no regex, no reflection)</li>
 *   <li>Enriched diagnostics with TE001 codes and snippet context</li>
 *   <li>Keyword + variable-reference completion</li>
 *   <li>Hover showing AST root node type</li>
 *   <li>{@link ParseFailureDiagnostics} sealed interface for type-safe error handling</li>
 * </ul>
 */
public class TinyExpressionP4LanguageServerExt extends TinyExpressionP4LanguageServer {

  // ── Semantic token type indices (must match legend declared in initialize()) ──

  private static final int TOKEN_TYPE_KEYWORD  = 0;
  private static final int TOKEN_TYPE_VARIABLE = 1;
  private static final int TOKEN_TYPE_NUMBER   = 2;
  private static final int TOKEN_TYPE_STRING   = 3;
  private static final int TOKEN_TYPE_OPERATOR = 4;
  private static final int TOKEN_TYPE_FUNCTION = 5;
  private static final int TOKEN_TYPE_COMMENT  = 6;
  private static final int TOKEN_TYPE_TYPE     = 7;

  private static final List<String> SEMANTIC_TOKEN_TYPES = List.of(
      "keyword", "variable", "number", "string", "operator", "function", "comment", "type");

  // ── TinyExpression P4 keyword set ──

  private static final Set<String> KEYWORD_SET = Set.of(
      "if", "else", "match", "default",
      "var", "variable", "as",
      "number", "string", "boolean", "object", "float",
      "set", "not", "exists", "description", "call",
      "import", "external", "returning",
      "true", "false");

  // ── Operator set ──

  private static final Set<String> OPERATOR_SET = Set.of(
      "+", "-", "*", "/",
      "==", "!=", "<=", ">=", "<", ">",
      "->", "=");

  // ── Completion keyword list (meaningful P4 keywords only) ──

  private static final List<String> COMPLETION_KEYWORDS = List.of(
      "if", "else", "match", "default",
      "var", "variable", "as",
      "number", "string", "boolean", "object", "float",
      "set", "not", "exists", "call",
      "import", "external", "returning",
      "true", "false");

  /** Pattern for extracting $variable references from document text. */
  private static final Pattern VARIABLE_PATTERN =
      Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");

  // =========================================================================
  // Error Catalog (TE0xx codes)
  // =========================================================================

  private record ErrorCatalogEntry(String code, String message, String fix) {
    String fullMessage() { return "[" + code + "] " + message + " 修正例: " + fix; }
  }

  private static final Map<String, ErrorCatalogEntry> ERROR_CATALOG = new LinkedHashMap<>();

  static {
    // Ported from tiny-expression-validator docs/error-catalog.md
    ERROR_CATALOG.put("TE001", new ErrorCatalogEntry("TE001", "else ブロックには数値式が必要です。", "else { 0 } / else { $someNumber } に修正"));
    ERROR_CATALOG.put("TE002", new ErrorCatalogEntry("TE002", "識別子を式として解釈できません。", "変数は $abc、文字列は 'abc'"));
    ERROR_CATALOG.put("TE003", new ErrorCatalogEntry("TE003", "文字列リテラルのクォートが不正です。", "'text' 形式に修正"));
    ERROR_CATALOG.put("TE004", new ErrorCatalogEntry("TE004", "丸カッコが閉じられていません。", "対応する ) を追加"));
    ERROR_CATALOG.put("TE005", new ErrorCatalogEntry("TE005", "波カッコが閉じられていません。", "対応する } を追加"));
    ERROR_CATALOG.put("TE006", new ErrorCatalogEntry("TE006", "文末のセミコロンが必要です。", "文の末尾に ; を追加"));
    ERROR_CATALOG.put("TE007", new ErrorCatalogEntry("TE007", "description の書式が不正です。", "description = '...' 形式に修正"));
    ERROR_CATALOG.put("TE008", new ErrorCatalogEntry("TE008", "不正な文字が含まれています。", "半角文字に修正"));
    ERROR_CATALOG.put("TE009", new ErrorCatalogEntry("TE009", "ここに不要なトークンがあります。", "余分な語句を削除"));
    ERROR_CATALOG.put("TE010", new ErrorCatalogEntry("TE010", "構文の並びが想定と一致しません。", "直前の式と区切り記号を確認"));
    ERROR_CATALOG.put("TE011", new ErrorCatalogEntry("TE011", "if 条件には booleanExpression が必要です。", "比較式や boolean 値を設定"));
    ERROR_CATALOG.put("TE012", new ErrorCatalogEntry("TE012", "if の then ブロックには数値式が必要です。", "if (...) { 1 } 形式に修正"));
    ERROR_CATALOG.put("TE013", new ErrorCatalogEntry("TE013", "match 構文が不正です。", "match { cond -> expr, default -> expr } を確認"));
    ERROR_CATALOG.put("TE014", new ErrorCatalogEntry("TE014", "default ケースの記述が不正です。", "default -> expr を追加/修正"));
    ERROR_CATALOG.put("TE015", new ErrorCatalogEntry("TE015", "関数引数の数が不正です。", "関数定義に合わせて引数数を修正"));
    ERROR_CATALOG.put("TE016", new ErrorCatalogEntry("TE016", "import 宣言の形式が不正です。", "import ... as ...; を確認"));
    ERROR_CATALOG.put("TE017", new ErrorCatalogEntry("TE017", "variable 宣言の形式が不正です。", "variable $name ... ; を確認"));
    ERROR_CATALOG.put("TE018", new ErrorCatalogEntry("TE018", "型ヒントの位置が不正です。", "as number/string/boolean の位置を修正"));
    ERROR_CATALOG.put("TE019", new ErrorCatalogEntry("TE019", "get/orElse 構文が不正です。", "get(...).orElse(...) 形式を確認"));
    ERROR_CATALOG.put("TE020", new ErrorCatalogEntry("TE020", "構文エラーです。", "エラー行の直前トークンと括弧を確認"));
    ERROR_CATALOG.put("TE021", new ErrorCatalogEntry("TE021", "利用可能なメソッド名ではありません。", "候補メソッド名へ修正"));
    ERROR_CATALOG.put("TE022", new ErrorCatalogEntry("TE022", "利用可能な変数名ではありません。", "候補変数名へ修正"));
    ERROR_CATALOG.put("TE023", new ErrorCatalogEntry("TE023", "演算子/記法不正です。", "&/| の追加、&& -> &、$ の除去など"));
    ERROR_CATALOG.put("TE024", new ErrorCatalogEntry("TE024", "partialKey 変数のsuffix不足です。", "$prefix_<suffix> 形式に修正"));
  }

  private String resolveCatalogMessage(String hint, String snippet, String leading) {
    String code = resolveCode(hint, snippet, leading);
    ErrorCatalogEntry entry = ERROR_CATALOG.getOrDefault(code, ERROR_CATALOG.get("TE020"));
    return entry.fullMessage() + " (詳細: " + hint + ")";
  }

  private String resolveCode(String hint, String snippet, String leading) {
    if (hint.contains("';'")) return "TE006";
    if (hint.contains("')'")) return "TE004";
    if (hint.contains("'}'")) return "TE005";
    if (hint.contains("'description'")) return "TE007";
    if (hint.contains("'if'")) return "TE011";
    if (hint.contains("'match'")) return "TE013";
    if (hint.contains("'default'")) return "TE014";
    if (hint.contains("'import'")) return "TE016";
    if (hint.contains("'variable'") || hint.contains("'var'")) return "TE017";
    if (hint.contains("'as'")) return "TE018";
    if (hint.contains("'orElse'")) return "TE019";
    if (hint.contains("&&") || hint.contains("||")) return "TE023";

    if (snippet.startsWith("if")) return "TE011";
    if (leading.stripTrailing().endsWith("if")) return "TE011";
    if (snippet.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        // Looks like a bare identifier used as expression
        return "TE002";
    }

    return "TE020";
  }

  // ── State ──

  private final DocumentFilter documentFilter;
  private LanguageClient extClient;
  private final Map<String, ExtDocumentState> extDocuments = new HashMap<>();
  /** Per-document incremental parse cache keyed by document URI. */
  private final Map<String, IncrementalParseCache> incrementalCaches = new HashMap<>();

  // =========================================================================
  // Constructors
  // =========================================================================

  /** Uses {@link DocumentFilter#autoDetect()} — handles both FormulaInfo and plain files. */
  public TinyExpressionP4LanguageServerExt() {
    this(DocumentFilter.autoDetect());
  }

  /**
   * Injects a custom {@link DocumentFilter}.
   *
   * <pre>{@code
   * // Markdown with fenced blocks:
   * new TinyExpressionP4LanguageServerExt(DocumentFilter.fenced("```tinyexp", "```"))
   *
   * // Multiple formats:
   * new TinyExpressionP4LanguageServerExt(
   *     DocumentFilter.firstMatch(DocumentFilter.formulaInfo(),
   *                               DocumentFilter.fenced("```tinyexp", "```")))
   * }</pre>
   */
  public TinyExpressionP4LanguageServerExt(DocumentFilter documentFilter) {
    this.documentFilter = documentFilter;
  }

  // =========================================================================
  // LanguageClientAware
  // =========================================================================

  @Override
  public void connect(LanguageClient client) {
    super.connect(client);
    this.extClient = client;
  }

  // =========================================================================
  // initialize — register improved capabilities
  // =========================================================================

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    // Call super to trigger initCatalogResolver(params) in the generated class.
    // We discard the returned capabilities and build our own below.
    super.initialize(params);

    ServerCapabilities cap = new ServerCapabilities();
    cap.setTextDocumentSync(TextDocumentSyncKind.Full);

    CompletionOptions co = new CompletionOptions();
    co.setResolveProvider(false);
    cap.setCompletionProvider(co);
    cap.setHoverProvider(true);

    SemanticTokensWithRegistrationOptions stOpts = new SemanticTokensWithRegistrationOptions();
    stOpts.setFull(true);
    stOpts.setLegend(new SemanticTokensLegend(SEMANTIC_TOKEN_TYPES, List.of()));
    cap.setSemanticTokensProvider(stOpts);

    CodeActionOptions caOpts = new CodeActionOptions(
        List.of(CodeActionKind.QuickFix, CodeActionKind.RefactorRewrite));
    cap.setCodeActionProvider(caOpts);
    cap.setDocumentSymbolProvider(true);
    cap.setRenameProvider(true);
    cap.setDocumentHighlightProvider(true);
    cap.setSignatureHelpProvider(new org.eclipse.lsp4j.SignatureHelpOptions(List.of("(", ",")));
    cap.setCodeLensProvider(new org.eclipse.lsp4j.CodeLensOptions(false));
    cap.setInlayHintProvider(true);
    cap.setFoldingRangeProvider(true);
    cap.setDefinitionProvider(true);
    cap.setLinkedEditingRangeProvider(true);
    cap.setWorkspaceSymbolProvider(true);
    cap.setDocumentFormattingProvider(true);

    return CompletableFuture.completedFuture(new InitializeResult(cap));
  }

  // =========================================================================
  // File format: FormulaInfo multi-section document
  //   tags:NORMAL
  //   description:...
  //   formula:          ← delimiter; formula content follows
  //   $x + $y
  //   ---END_OF_PART---
  // =========================================================================

  private static final String FORMULA_LINE_MARKER = "formula:";
  private static final String PART_END_MARKER     = "---END_OF_PART---";

  /**
   * Extracts the first formula section from a FormulaInfo-style document.
   * Returns null if no {@code formula:} marker is found (plain .tinyexp).
   *
   * @return FormulaSection with the formula text and the line-number offset
   *         within the original document where the formula content starts.
   */
  static FormulaSection extractFormulaSection(String fullContent) {
    String[] lines = fullContent.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      // Strip \r so Windows (\r\n) and Unix (\n) line endings both work
      String stripped = lines[i].stripTrailing().replace("\r", "");
      if (FORMULA_LINE_MARKER.equals(stripped)) {
        int formulaLineOffset = i + 1; // first line of formula content
        StringBuilder sb = new StringBuilder();
        for (int j = formulaLineOffset; j < lines.length; j++) {
          String fline = lines[j].replace("\r", "");
          if (PART_END_MARKER.equals(fline.stripTrailing())) break;
          sb.append(lines[j].replace("\r", "")); // keep original spacing
          sb.append('\n');
        }
        return new FormulaSection(sb.toString(), formulaLineOffset);
      }
    }
    return null; // no formula: marker — plain expression file
  }

  // =========================================================================
  // parseDocument — extract formula section via DocumentFilter, then enrich
  // =========================================================================

  @Override
  public ParseResult parseDocument(String uri, String content) {
    FormulaSection fs = documentFilter.extract(content);

    if (fs != null) {
      // FormulaInfo file: suppress the parent's whole-file diagnostics, then parse formula portion.
      if (extClient != null) {
        extClient.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
      }
      return parseAndEnrich(uri, fs.content(), fs.lineOffset(), content);
    }

    // Plain expression file: parse directly (bypasses parent so we can capture ctx diagnostics).
    return parseAndEnrich(uri, content, 0, content);
  }

  /**
   * Core parse + enrich pipeline. Parses {@code formulaContent}, captures
   * {@link org.unlaxer.context.ParseFailureDiagnostics} from the {@link ParseContext} before
   * closing it, then publishes enriched LSP diagnostics with parser-derived offset and hints.
   *
   * @param uri            document URI
   * @param formulaContent formula text to parse (may be a sub-section of the full document)
   * @param lineOffset     number of lines to add to parser positions when publishing diagnostics
   * @param fullContent    full document content (includes metadata headers)
   */
  public ParseResult parseAndEnrich(String uri, String formulaContent, int lineOffset, String fullContent) {
    // ── Incremental parse cache: detect unchanged content and skip reparse ──
    IncrementalParseCache cache = incrementalCaches.computeIfAbsent(uri, k -> new IncrementalParseCache());
    List<String> chunks = cache.splitIntoChunks(formulaContent, ",", ";");

    boolean hasChangedChunk = false;
    for (String chunk : chunks) {
      if (cache.getCached(chunk) == null) {
        hasChangedChunk = true;
        break;
      }
    }

    // If no chunks changed, reuse the previous ExtDocumentState entirely
    if (!hasChangedChunk) {
      ExtDocumentState prev = extDocuments.get(uri);
      if (prev != null) {
        IncrementalParseCache.CacheStats stats = cache.stats();
        System.err.printf("[IncrementalParseCache] uri=%s ALL_CACHED chunks=%d entries=%d hitRate=%.1f%%%n",
            uri, chunks.size(), stats.entries(), stats.hitRate() * 100);
        return prev.parseResult();
      }
    }

    // ── Full reparse (at least one chunk changed) ──
    StringSource source = createRootSource(formulaContent);
    ParseResult result;
    org.unlaxer.context.ParseFailureDiagnostics ctxDiag = null;
    List<ScopeStore.SymbolDiagnostic> scopeDiagnostics = new ArrayList<>();
    List<ScopeStore.SymbolInfo>       declarations     = new ArrayList<>();
    List<ScopeStore.ReferenceInfo>    references       = new ArrayList<>();

    if (source == null) {
      result = new ParseResult(false, 0, formulaContent.length());
    } else {
      Parser rootParser = TinyExpressionP4Parsers.getRootParser();
      ParseContext ctx = new ParseContext(source);
      ScopeStore.registerDispatcher(ctx);
      Parsed parsed;
      try {
        parsed = rootParser.parse(ctx);
        ctxDiag = ctx.getParseFailureDiagnostics();

        // Extract scope information BEFORE closing context
        scopeDiagnostics.addAll(ScopeStore.getDiagnostics(ctx));
        declarations.addAll(ScopeStore.getAllDeclarations(ctx));
        references.addAll(ScopeStore.getAllReferences(ctx));
      } finally {
        ctx.close();
      }
      int consumed = 0;
      if (parsed.isSucceeded() && parsed.getConsumed() != null) {
        String s = parsed.getConsumed().source.sourceAsString();
        if (s != null) consumed = s.length();
      }
      result = new ParseResult(parsed.isSucceeded(), consumed, formulaContent.length());
    }

    // ── Update incremental cache with the current chunks ──
    int chunkLineOffset = lineOffset;
    for (String chunk : chunks) {
      if (cache.getCached(chunk) == null) {
        // Parse each changed chunk individually for cache storage
        Token chunkToken = null;
        try {
          StringSource chunkSource = createRootSource(chunk);
          if (chunkSource != null) {
            Parser chunkParser = TinyExpressionP4Parsers.getRootParser();
            ParseContext chunkCtx = new ParseContext(chunkSource);
            try {
              Parsed chunkParsed = chunkParser.parse(chunkCtx);
              if (chunkParsed.isSucceeded() && chunkParsed.getConsumed() != null) {
                chunkToken = chunkParsed.getConsumed();
              }
            } finally {
              chunkCtx.close();
            }
          }
        } catch (Exception ignored) {
          // Chunk-level parse failure is fine; the full parse result is authoritative
        }
        cache.put(chunk, chunkToken, chunkLineOffset);
      }
      // Advance line offset by the number of newlines in this chunk
      for (int i = 0; i < chunk.length(); i++) {
        if (chunk.charAt(i) == '\n') chunkLineOffset++;
      }
    }

    IncrementalParseCache.CacheStats stats = cache.stats();
    System.err.printf("[IncrementalParseCache] uri=%s REPARSED chunks=%d entries=%d hits=%d misses=%d hitRate=%.1f%%%n",
        uri, chunks.size(), stats.entries(), stats.hits(), stats.misses(), stats.hitRate() * 100);

    TinyExpressionP4AST ast = null;
    if (result.succeeded() && result.consumedLength() == result.totalLength()) {
      try { ast = TinyExpressionP4Mapper.parse(formulaContent); } catch (Exception ignored) {}
    }

    ParseFailureDiagnostics failures = buildFailureDiagnostics(result, formulaContent, ctxDiag);
    extDocuments.put(uri, new ExtDocumentState(formulaContent, result, ast, failures, declarations, references, lineOffset, fullContent));

    if (extClient != null) {
      List<Diagnostic> formulaInfoDiags = computeFormulaInfoDiagnostics(uri, fullContent);
      publishEnrichedDiagnostics(uri, formulaContent, failures, scopeDiagnostics, lineOffset, formulaInfoDiags);
    }
    return result;
  }

  // =========================================================================
  // getTextDocumentService — return extended implementation
  // =========================================================================

  @Override
  public TextDocumentService getTextDocumentService() {
    return new ExtTextDocumentService(this);
  }

  // =========================================================================
  // getWorkspaceService — return extended implementation
  // =========================================================================

  @Override
  public WorkspaceService getWorkspaceService() {
    return new ExtWorkspaceService(this);
  }

  // =========================================================================
  // Private helpers
  // =========================================================================

  /**
   * Builds typed failure diagnostics from parse result and parser-native context diagnostics.
   *
   * <p>Priority:
   * <ol>
   *   <li>If parse succeeded fully → {@link ParseFailureDiagnostics#absent()}</li>
   *   <li>If {@code ctxDiag} is available → use farthest offset + expected-hint candidates</li>
   *   <li>Otherwise → snippet at {@code result.consumedLength()}</li>
   * </ol>
   */
  private ParseFailureDiagnostics buildFailureDiagnostics(
      ParseResult result, String content,
      org.unlaxer.context.ParseFailureDiagnostics ctxDiag) {
    if (result.succeeded() && result.consumedLength() == result.totalLength()) {
      return ParseFailureDiagnostics.absent();
    }

    if (ctxDiag != null) {
      int offset = ctxDiag.getFarthestOffset();
      List<String> hints = toDisplayHints(ctxDiag);
      return ParseFailureDiagnostics.present(offset, hints);
    }

    // Fall back: snippet from consumedLength.
    int offset = result.consumedLength();
    List<String> hints = List.of();
    if (offset < content.length()) {
      String remaining = content.substring(offset, Math.min(offset + 20, content.length())).strip();
      if (!remaining.isEmpty()) {
        hints = List.of("near: '" + remaining + "'");
      }
    }
    return ParseFailureDiagnostics.present(offset, hints);
  }

  /**
   * Extracts display hints from {@link org.unlaxer.context.ParseFailureDiagnostics}.
   * Uses {@code expectedHintCandidates} (e.g. "Expected ','") when available,
   * then falls back to {@code expectedParsers} class names.
   */
  private static List<String> toDisplayHints(
      org.unlaxer.context.ParseFailureDiagnostics ctxDiag) {
    List<org.unlaxer.context.ParseFailureDiagnostics.ExpectedHintCandidate> candidates =
        ctxDiag.getExpectedHintCandidates();
    if (!candidates.isEmpty()) {
      return candidates.stream()
          .map(org.unlaxer.context.ParseFailureDiagnostics.ExpectedHintCandidate::getDisplayHint)
          .filter(h -> h != null && !h.isBlank())
          .distinct()
          .toList();
    }
    List<String> parsers = ctxDiag.getExpectedParsers();
    if (!parsers.isEmpty()) {
      return List.of("Expected: " + parsers.get(0));
    }
    return List.of();
  }

  private void publishEnrichedDiagnostics(String uri, String content,
      ParseFailureDiagnostics failures,
      List<ScopeStore.SymbolDiagnostic> scopeDiagnostics,
      int lineOffset,
      List<Diagnostic> formulaInfoDiags) {
    List<Diagnostic> diagnostics = new ArrayList<>(formulaInfoDiags);
    // Semantic diagnostics from ScopeStore (@declares / @backref)
    for (ScopeStore.SymbolDiagnostic sd : scopeDiagnostics) {
      Position start = offsetToPositionWithOffset(content, sd.offset(), lineOffset);
      Position end   = offsetToPositionWithOffset(content, sd.offset() + sd.length(), lineOffset);
      Diagnostic d = new Diagnostic();
      d.setRange(new Range(start, end));
      d.setSeverity(switch (sd.severity()) {
        case ERROR   -> DiagnosticSeverity.Error;
        case WARNING -> DiagnosticSeverity.Warning;
        case INFO    -> DiagnosticSeverity.Information;
        case HINT    -> DiagnosticSeverity.Hint;
      });
      d.setSource("tinyexpression-p4-scope");
      d.setMessage(sd.message());
      diagnostics.add(d);
    }
    if (failures.hasFailure()) {
      int offset = failures.failureOffset();
      String snippet = content
          .substring(offset, Math.min(offset + 20, content.length()))
          .strip();

      Position start = offsetToPositionWithOffset(content, offset, lineOffset);
      // Range end: end of the failing line — more focused than end-of-file.
      int lineEnd = offset;
      while (lineEnd < content.length() && content.charAt(lineEnd) != '\n') lineEnd++;
      Position end = offsetToPositionWithOffset(content, lineEnd, lineOffset);

      Diagnostic d = new Diagnostic();
      d.setRange(new Range(start, end));
      d.setSeverity(DiagnosticSeverity.Error);
      d.setSource("tinyexpression-p4");

      String hint = failures.expectedHints().isEmpty() ? "" : failures.expectedHints().get(0);
      String leading = content.substring(0, offset);
      String code = resolveCode(hint, snippet, leading);
      d.setCode(Either.forLeft(code));

      String message = resolveCatalogMessage(hint, snippet, leading);
      d.setMessage(message);
      diagnostics.add(d);
    }
    extClient.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
  }

  /** Convert character offset to Position (line, character), applying line offset. */
  public static Position offsetToPositionWithOffset(String content, int offset, int lineOffset) {
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
    return new Position(line + lineOffset, column);
  }

  /** Convert a character offset and length to a Range, applying line offset. */
  public static Range offsetToRangeWithOffset(String content, int offset, int length, int lineOffset) {
    Position start = offsetToPositionWithOffset(content, offset, lineOffset);
    Position end = offsetToPositionWithOffset(content, offset + length, lineOffset);
    return new Range(start, end);
  }

  // =========================================================================
  // Semantic token computation — type-safe token-tree walk
  // =========================================================================

  List<Integer> computeSemanticTokens(String content, int lineOffset) {
    StringSource source = createRootSource(content);
    if (source == null) return Collections.emptyList();

    Parser rootParser = TinyExpressionP4Parsers.getRootParser();
    ParseContext context = new ParseContext(source);
    Parsed parsed;
    try {
      parsed = rootParser.parse(context);
    } finally {
      context.close();
    }
    if (!parsed.isSucceeded()) return Collections.emptyList();

    List<Token> leaves = new ArrayList<>();
    collectLeaves(parsed.getConsumed(), leaves);
    leaves.sort(Comparator.comparingInt(t -> t.source.offsetFromRoot().value()));

    List<Integer> data = new ArrayList<>();
    // prevLine/prevChar are in full-document coordinates (lineOffset applied)
    int prevLine = 0, prevChar = 0;

    for (Token leaf : leaves) {
      int tokenType = classifyLeafToken(leaf);
      if (tokenType < 0) continue;

      String text = leaf.source.sourceAsString();
      if (text == null || text.isBlank()) continue;

      int offset = leaf.source.offsetFromRoot().value();
      int[] lc = offsetToLineChar(content, offset);
      int line = lc[0] + lineOffset; // shift into full-document coordinates
      int col  = lc[1];
      int length = text.length();

      int deltaLine = line - prevLine;
      int deltaChar = (deltaLine == 0) ? (col - prevChar) : col;

      data.add(deltaLine);
      data.add(deltaChar);
      data.add(length);
      data.add(tokenType);
      data.add(0); // no modifiers

      prevLine = line;
      prevChar = col;
    }
    return data;
  }

  private static void collectLeaves(Token token, List<Token> out) {
    if (token == null) return;
    if (token.filteredChildren == null || token.filteredChildren.isEmpty()) {
      out.add(token);
      return;
    }
    for (Token child : token.filteredChildren) {
      collectLeaves(child, out);
    }
  }

  /**
   * Classifies a leaf token into a semantic type index.
   * Type-safe: uses {@code instanceof} pattern matching on parser type,
   * then text comparison for keywords and operators.
   *
   * @return token type index (0-6), or -1 to skip
   */
  private static int classifyLeafToken(Token token) {
    Parser parser = token.getParser();
    String text = token.source.sourceAsString();
    if (text == null || text.isBlank()) return -1;
    String stripped = text.strip();

    // Parser-type checks (no reflection)
    if (parser instanceof CPPComment) return TOKEN_TYPE_COMMENT;
    if (parser instanceof SpaceParser) return -1;
    if (parser instanceof NumberParser) return TOKEN_TYPE_NUMBER;
    if (parser instanceof SingleQuotedParser) return TOKEN_TYPE_STRING;
    if (parser instanceof org.unlaxer.parser.elementary.WildCardStringTerninatorParser) return TOKEN_TYPE_STRING;

    // Text-based classification
    if (KEYWORD_SET.contains(stripped)) return TOKEN_TYPE_KEYWORD;
    if (OPERATOR_SET.contains(stripped)) return TOKEN_TYPE_OPERATOR;
    if ("$".equals(stripped)) return TOKEN_TYPE_VARIABLE;
    if (parser instanceof IdentifierParser) return TOKEN_TYPE_VARIABLE;

    return -1;
  }

  private static int[] offsetToLineChar(String content, int offset) {
    int line = 0, col = 0;
    for (int i = 0; i < offset && i < content.length(); i++) {
      if (content.charAt(i) == '\n') { line++; col = 0; } else { col++; }
    }
    return new int[]{line, col};
  }

  // =========================================================================
  // Java code block detection and tokenization (FormulaInfo Phase 2)
  // =========================================================================

  /** Pattern matching the opening fence of a Java code block: ```java or ```java:ClassName */
  private static final Pattern JAVA_FENCE_OPEN  = Pattern.compile("^\\s*```java(:\\w+)?\\s*$");
  /** Pattern matching the closing fence of a code block: ``` */
  private static final Pattern JAVA_FENCE_CLOSE = Pattern.compile("^\\s*```\\s*$");

  /** Java keywords for syntax highlighting inside ```java blocks. */
  private static final Set<String> JAVA_KEYWORD_SET = Set.of(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch",
      "char", "class", "const", "continue", "default", "do", "double",
      "else", "enum", "extends", "final", "finally", "float", "for",
      "goto", "if", "implements", "import", "instanceof", "int",
      "interface", "long", "native", "new", "package", "private",
      "protected", "public", "return", "short", "static", "strictfp",
      "super", "switch", "synchronized", "this", "throw", "throws",
      "transient", "try", "void", "volatile", "while",
      "true", "false", "null");

  /** Java built-in / common type names highlighted as "type" tokens. */
  private static final Set<String> JAVA_TYPE_SET = Set.of(
      "String", "Integer", "Long", "Double", "Float", "Boolean",
      "Object", "List", "Map", "Set", "Optional", "BigDecimal",
      "Override", "Deprecated", "SuppressWarnings");

  /** Regex patterns for Java token extraction (order matters — first match wins). */
  private static final Pattern JAVA_LINE_COMMENT  = Pattern.compile("//.*");
  private static final Pattern JAVA_STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
  private static final Pattern JAVA_CHAR_LITERAL   = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'");
  private static final Pattern JAVA_NUMBER_LITERAL = Pattern.compile("\\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|[0-9][0-9_]*(?:\\.[0-9_]*)?(?:[eE][+-]?[0-9_]+)?[fFdDlL]?)\\b");
  private static final Pattern JAVA_ANNOTATION     = Pattern.compile("@[A-Za-z_]\\w*");
  private static final Pattern JAVA_IDENTIFIER     = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

  /**
   * Checks whether the given line (0-based, in the full document) falls inside
   * a {@code ```java} fenced code block.
   *
   * @param fullContent the full document text
   * @param line        0-based line number in the full document
   * @return true if the line is inside a Java code block (not on the fence lines themselves)
   */
  static boolean isInsideJavaCodeBlock(String fullContent, int line) {
    if (fullContent == null) return false;
    String[] lines = fullContent.split("\n", -1);
    boolean inside = false;
    for (int i = 0; i < lines.length && i <= line; i++) {
      String stripped = lines[i].stripTrailing().replace("\r", "");
      if (!inside && JAVA_FENCE_OPEN.matcher(stripped).matches()) {
        inside = true;
        continue;
      }
      if (inside && JAVA_FENCE_CLOSE.matcher(stripped).matches()) {
        inside = false;
        continue;
      }
    }
    return inside;
  }

  /**
   * Represents a range of lines forming a Java code block inside a FormulaInfo document.
   * {@code startLine} is the first content line (after the opening fence),
   * {@code endLine} is exclusive (the closing fence line or end of document).
   */
  record JavaCodeBlock(int startLine, int endLine) {}

  /**
   * Finds all Java code block ranges (content lines only, excluding fences)
   * in the full document.
   */
  static List<JavaCodeBlock> findJavaCodeBlocks(String fullContent) {
    if (fullContent == null) return Collections.emptyList();
    String[] lines = fullContent.split("\n", -1);
    List<JavaCodeBlock> blocks = new ArrayList<>();
    boolean inside = false;
    int blockStart = -1;
    for (int i = 0; i < lines.length; i++) {
      String stripped = lines[i].stripTrailing().replace("\r", "");
      if (!inside && JAVA_FENCE_OPEN.matcher(stripped).matches()) {
        inside = true;
        blockStart = i + 1;
        continue;
      }
      if (inside && JAVA_FENCE_CLOSE.matcher(stripped).matches()) {
        blocks.add(new JavaCodeBlock(blockStart, i));
        inside = false;
        continue;
      }
    }
    // Unclosed block — treat rest of document as Java
    if (inside && blockStart >= 0) {
      blocks.add(new JavaCodeBlock(blockStart, lines.length));
    }
    return blocks;
  }

  /**
   * Simple record to hold a semantic token before it is delta-encoded.
   */
  record SemanticToken(int line, int startChar, int length, int tokenType) {}

  /**
   * Tokenizes a single line of Java code using regex-based pattern matching.
   * Returns a list of {@link SemanticToken} entries with absolute positions.
   *
   * @param line       the text of the line
   * @param lineNumber 0-based line number in the full document
   * @return list of semantic tokens found on this line
   */
  static List<SemanticToken> tokenizeJavaLine(String line, int lineNumber) {
    List<SemanticToken> tokens = new ArrayList<>();
    // Track which character positions are already claimed
    boolean[] claimed = new boolean[line.length()];

    // Pass 1: line comments (highest priority — claims rest of line)
    Matcher m = JAVA_LINE_COMMENT.matcher(line);
    while (m.find()) {
      markAndAdd(tokens, claimed, lineNumber, m.start(), m.end() - m.start(), TOKEN_TYPE_COMMENT);
    }

    // Pass 2: string literals
    m = JAVA_STRING_LITERAL.matcher(line);
    while (m.find()) {
      markAndAdd(tokens, claimed, lineNumber, m.start(), m.end() - m.start(), TOKEN_TYPE_STRING);
    }

    // Pass 3: char literals
    m = JAVA_CHAR_LITERAL.matcher(line);
    while (m.find()) {
      markAndAdd(tokens, claimed, lineNumber, m.start(), m.end() - m.start(), TOKEN_TYPE_STRING);
    }

    // Pass 4: number literals
    m = JAVA_NUMBER_LITERAL.matcher(line);
    while (m.find()) {
      if (!anyClaimed(claimed, m.start(), m.end())) {
        markAndAdd(tokens, claimed, lineNumber, m.start(), m.end() - m.start(), TOKEN_TYPE_NUMBER);
      }
    }

    // Pass 5: annotations
    m = JAVA_ANNOTATION.matcher(line);
    while (m.find()) {
      if (!anyClaimed(claimed, m.start(), m.end())) {
        markAndAdd(tokens, claimed, lineNumber, m.start(), m.end() - m.start(), TOKEN_TYPE_FUNCTION);
      }
    }

    // Pass 6: identifiers (keywords, types, or variables)
    m = JAVA_IDENTIFIER.matcher(line);
    while (m.find()) {
      if (!anyClaimed(claimed, m.start(), m.end())) {
        String word = m.group();
        int type;
        if (JAVA_KEYWORD_SET.contains(word)) {
          type = TOKEN_TYPE_KEYWORD;
        } else if (JAVA_TYPE_SET.contains(word)) {
          type = TOKEN_TYPE_TYPE;
        } else {
          continue; // plain identifier — skip
        }
        markAndAdd(tokens, claimed, lineNumber, m.start(), m.end() - m.start(), type);
      }
    }

    // Sort by start position
    tokens.sort(Comparator.comparingInt(t -> t.startChar()));
    return tokens;
  }

  /** Marks positions as claimed and adds the token, only if no overlap. */
  private static void markAndAdd(List<SemanticToken> tokens, boolean[] claimed,
      int lineNumber, int start, int length, int tokenType) {
    if (anyClaimed(claimed, start, start + length)) return;
    for (int i = start; i < start + length && i < claimed.length; i++) {
      claimed[i] = true;
    }
    tokens.add(new SemanticToken(lineNumber, start, length, tokenType));
  }

  private static boolean anyClaimed(boolean[] claimed, int start, int end) {
    for (int i = start; i < end && i < claimed.length; i++) {
      if (claimed[i]) return true;
    }
    return false;
  }

  /**
   * Computes semantic tokens for all Java code blocks in the full document.
   * Returns delta-encoded token data ready to merge with formula tokens.
   */
  List<Integer> computeJavaBlockSemanticTokens(String fullContent) {
    if (fullContent == null) return Collections.emptyList();
    List<JavaCodeBlock> blocks = findJavaCodeBlocks(fullContent);
    if (blocks.isEmpty()) return Collections.emptyList();

    String[] lines = fullContent.split("\n", -1);

    // Collect all tokens from all Java blocks
    List<SemanticToken> allTokens = new ArrayList<>();
    for (JavaCodeBlock block : blocks) {
      for (int i = block.startLine(); i < block.endLine() && i < lines.length; i++) {
        allTokens.addAll(tokenizeJavaLine(lines[i].replace("\r", ""), i));
      }
    }

    if (allTokens.isEmpty()) return Collections.emptyList();

    // Sort by (line, startChar) and delta-encode
    allTokens.sort(Comparator.comparingInt(SemanticToken::line)
        .thenComparingInt(SemanticToken::startChar));

    List<Integer> data = new ArrayList<>();
    int prevLine = 0, prevChar = 0;
    for (SemanticToken t : allTokens) {
      int deltaLine = t.line() - prevLine;
      int deltaChar = (deltaLine == 0) ? (t.startChar() - prevChar) : t.startChar();
      data.add(deltaLine);
      data.add(deltaChar);
      data.add(t.length());
      data.add(t.tokenType());
      data.add(0); // no modifiers
      prevLine = t.line();
      prevChar = t.startChar();
    }
    return data;
  }

  /** Exposes {@code setCatalogResolver} publicly for configuration and testing. */
  @Override
  public void setCatalogResolver(CatalogResolver r) {
    super.setCatalogResolver(r);
  }

  /** Reflection-compatible StringSource factory (same as generated code). */
  static StringSource createRootSource(String source) {
    try {
      java.lang.reflect.Method m =
          StringSource.class.getMethod("createRootSource", String.class);
      Object v = m.invoke(null, source);
      if (v instanceof StringSource s) return s;
    } catch (Throwable ignored) {}
    try {
      for (java.lang.reflect.Constructor<?> c : StringSource.class.getDeclaredConstructors()) {
        Class<?>[] types = c.getParameterTypes();
        if (types.length == 0 || types[0] != String.class) continue;
        Object[] args = new Object[types.length];
        args[0] = source;
        c.setAccessible(true);
        Object v = c.newInstance(args);
        if (v instanceof StringSource s) return s;
      }
    } catch (Throwable ignored) {}
    return null;
  }

  // =========================================================================
  // FormulaInfo metadata — field names, value suggestions, dependsOn diagnostics
  // =========================================================================

  /** Metadata field names that may appear before the formula: line. */
  private static final List<String> METADATA_FIELD_NAMES = List.of(
      "calculatorName", "resultType", "numberType", "tags", "description",
      "dependsOn", "executionBackend",
      "periodStartInclusive", "periodEndExclusive");

  /** Suggested values for resultType: field. */
  private static final List<String> RESULT_TYPE_VALUES = List.of(
      "float", "string", "boolean", "object", "double", "int", "long", "bigDecimal");

  /** Suggested values for executionBackend: field. */
  private static final List<String> EXECUTION_BACKEND_VALUES = List.of(
      "JAVA_CODE", "AST_EVALUATOR", "DSL_JAVA_CODE");

  /**
   * Returns true if the given line (0-based, in the full document) is in the
   * metadata section — i.e. before the first {@code formula:} line.
   */
  static boolean isMetadataLine(String fullContent, int line) {
    String[] lines = fullContent.split("\n", -1);
    // Find the first formula: line
    for (int i = 0; i < lines.length; i++) {
      String stripped = lines[i].stripTrailing().replace("\r", "");
      if (FORMULA_LINE_MARKER.equals(stripped)) {
        return line < i;
      }
    }
    return false; // no formula: marker — not a FormulaInfo document
  }

  /**
   * Returns the text of the given line (0-based) from the full document content.
   */
  static String lineText(String fullContent, int line) {
    String[] lines = fullContent.split("\n", -1);
    if (line < 0 || line >= lines.length) return "";
    return lines[line].replace("\r", "");
  }

  /**
   * Parses a FormulaInfo multi-section document and collects all calculatorName values
   * mapped to their line numbers (0-based, in the full document).
   */
  static Map<String, Integer> collectCalculatorNames(String fullContent) {
    Map<String, Integer> result = new LinkedHashMap<>();
    String[] lines = fullContent.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String stripped = lines[i].stripTrailing().replace("\r", "");
      if (stripped.startsWith("calculatorName:")) {
        String name = stripped.substring("calculatorName:".length()).strip();
        if (!name.isEmpty()) {
          result.put(name, i);
        }
      }
    }
    return result;
  }

  /**
   * Collects all dependsOn values with their line numbers (0-based).
   * Each entry is [dependsOnValue, lineNumber].
   */
  static List<Map.Entry<String, Integer>> collectDependsOn(String fullContent) {
    List<Map.Entry<String, Integer>> result = new ArrayList<>();
    String[] lines = fullContent.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String stripped = lines[i].stripTrailing().replace("\r", "");
      if (stripped.startsWith("dependsOn:")) {
        String value = stripped.substring("dependsOn:".length()).strip();
        if (!value.isEmpty()) {
          // Support comma-separated dependsOn values
          for (String dep : value.split(",")) {
            String trimmed = dep.strip();
            if (!trimmed.isEmpty()) {
              result.add(Map.entry(trimmed, i));
            }
          }
        }
      }
    }
    return result;
  }

  /**
   * Computes diagnostics for FormulaInfo metadata: warns when dependsOn references
   * a calculatorName that does not exist within the same document.
   */
  private List<Diagnostic> computeFormulaInfoDiagnostics(String uri, String fullContent) {
    List<Diagnostic> diags = new ArrayList<>();
    if (fullContent == null) return diags;

    Map<String, Integer> calcNames = collectCalculatorNames(fullContent);
    List<Map.Entry<String, Integer>> deps = collectDependsOn(fullContent);

    for (Map.Entry<String, Integer> dep : deps) {
      String depName = dep.getKey();
      int lineNum = dep.getValue();
      if (!calcNames.containsKey(depName)) {
        String lineContent = lineText(fullContent, lineNum);
        // Find the column of the depName within the line
        int col = lineContent.indexOf(depName);
        if (col < 0) col = 0;

        Position start = new Position(lineNum, col);
        Position end = new Position(lineNum, col + depName.length());
        Diagnostic d = new Diagnostic();
        d.setRange(new Range(start, end));
        d.setSeverity(DiagnosticSeverity.Warning);
        d.setSource("formulainfo");
        d.setCode(Either.forLeft("FI001"));
        d.setMessage("dependsOn: '" + depName + "' does not match any calculatorName in this document.");
        diags.add(d);
      }
    }
    return diags;
  }

  // =========================================================================
  // Inner types
  // =========================================================================

  /**
   * Extended per-document state stored by this server.
   *
   * @param content   raw document text
   * @param parseResult basic parse outcome from the generated parser
   * @param ast       typed P4 AST root (null when parse failed or was partial)
   * @param failures  typed failure diagnostics (sealed: Absent or Present)
   */
  record ExtDocumentState(
      String content,
      ParseResult parseResult,
      TinyExpressionP4AST ast,
      ParseFailureDiagnostics failures,
      List<ScopeStore.SymbolInfo> declarations,
      List<ScopeStore.ReferenceInfo> references,
      int lineOffset,
      String fullContent) {}

  /**
   * Extended TextDocumentService that replaces the generated no-op implementations
   * of semantic tokens, completion, and hover.
   */
  class ExtTextDocumentService implements TextDocumentService {

    private final TinyExpressionP4LanguageServerExt server;

    ExtTextDocumentService(TinyExpressionP4LanguageServerExt server) {
      this.server = server;
    }

    // ── lifecycle ──

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
      server.parseDocument(
          params.getTextDocument().getUri(),
          params.getTextDocument().getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
      server.parseDocument(
          params.getTextDocument().getUri(),
          params.getContentChanges().get(0).getText());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
      String closedUri = params.getTextDocument().getUri();
      server.extDocuments.remove(closedUri);
      server.incrementalCaches.remove(closedUri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {}

    // ── completion (enhanced with method/variable autocomplete + metadata) ──

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
        CompletionParams params) {

      List<CompletionItem> items = new ArrayList<>();
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);

      // ── FormulaInfo metadata completion ──
      if (state != null && state.fullContent() != null) {
        int cursorLine = params.getPosition().getLine();
        if (isMetadataLine(state.fullContent(), cursorLine)) {
          String line = lineText(state.fullContent(), cursorLine);
          return CompletableFuture.completedFuture(
              Either.forLeft(metadataCompletion(line, state.fullContent())));
        }
      }

      // Extract word prefix at cursor position for filtering
      String prefix = "";
      if (state != null) {
        prefix = wordAt(state.content(), params.getPosition(), state.lineOffset());
      }

      // 1. Keywords with prefix filtering
      for (String kw : COMPLETION_KEYWORDS) {
        if (kw.startsWith(prefix)) {
          CompletionItem item = new CompletionItem(kw);
          item.setKind(CompletionItemKind.Keyword);
          items.add(item);
        }
      }

      // 2. Methods and variables from ScopeStore
      if (state != null) {
        Set<String> seen = new LinkedHashSet<>();

        // Add declared symbols (methods and variables)
        for (ScopeStore.SymbolInfo decl : state.declarations()) {
          String symbolName = decl.name();
          String completionLabel = symbolName.startsWith("$") ? symbolName : symbolName;

          if (completionLabel.startsWith(prefix) && seen.add(completionLabel)) {
            CompletionItem item = new CompletionItem(completionLabel);

            // Infer kind and type hint from symbol name
            if (symbolName.startsWith("$")) {
              item.setKind(CompletionItemKind.Variable);
              item.setDetail(inferVariableType(symbolName));
            } else {
              item.setKind(CompletionItemKind.Method);
              item.setDetail(inferMethodReturnType(symbolName));
            }

            items.add(item);
          }
        }

        // 3. Catalog variable completion (from .tecatalog files via CatalogResolver)
        String dollarPrefix = prefix.startsWith("$") ? prefix.substring(1) : "";
        if (prefix.isEmpty() || prefix.startsWith("$")) {
          for (CompletionItem ci : server.catalogCompletion(dollarPrefix)) {
            String label = "$" + ci.getLabel();
            if (seen.add(label)) {
              CompletionItem item = new CompletionItem(label);
              item.setKind(CompletionItemKind.Variable);
              if (ci.getDetail() != null) {
                item.setDetail(ci.getDetail());
              }
              items.add(item);
            }
          }
        }

        // 4. Variable references from document text (fallback for undeclared variables)
        Matcher m = VARIABLE_PATTERN.matcher(state.content());
        while (m.find()) {
          String varName = "$" + m.group(1);
          if (varName.startsWith(prefix) && seen.add(varName)) {
            CompletionItem item = new CompletionItem(varName);
            item.setKind(CompletionItemKind.Variable);
            item.setDetail("inferred");
            items.add(item);
          }
        }
      }

      return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    /** Get inferred type hint for variable. */
    private String inferVariableType(String varName) {
      if (varName.contains("count") || varName.contains("number") || varName.contains("age")) {
        return "number";
      }
      if (varName.contains("name") || varName.contains("text") || varName.contains("str")) {
        return "string";
      }
      if (varName.contains("enabled") || varName.contains("flag") || varName.contains("is")) {
        return "boolean";
      }
      return "unknown";
    }

    /**
     * Provides metadata field name and value completion for FormulaInfo documents.
     * When the line contains a colon, suggests values for the field;
     * otherwise suggests field names.
     */
    private List<CompletionItem> metadataCompletion(String line, String fullContent) {
      List<CompletionItem> items = new ArrayList<>();
      String trimmed = line.stripLeading();

      // If line already has "fieldName:" — offer value completion
      if (trimmed.contains(":")) {
        String fieldName = trimmed.substring(0, trimmed.indexOf(':')).strip();
        String valuePrefix = trimmed.substring(trimmed.indexOf(':') + 1).strip();

        List<String> values = switch (fieldName) {
          case "resultType", "numberType" -> RESULT_TYPE_VALUES;
          case "executionBackend" -> EXECUTION_BACKEND_VALUES;
          case "dependsOn" -> {
            // Suggest existing calculatorName values
            Map<String, Integer> calcNames = collectCalculatorNames(fullContent);
            yield new ArrayList<>(calcNames.keySet());
          }
          default -> List.of();
        };

        for (String v : values) {
          if (v.startsWith(valuePrefix) || valuePrefix.isEmpty()) {
            CompletionItem item = new CompletionItem(v);
            item.setKind(CompletionItemKind.Value);
            item.setDetail(fieldName + " value");
            items.add(item);
          }
        }
        return items;
      }

      // No colon yet — offer field name completion
      for (String field : METADATA_FIELD_NAMES) {
        if (field.startsWith(trimmed) || trimmed.isEmpty()) {
          CompletionItem item = new CompletionItem(field + ":");
          item.setKind(CompletionItemKind.Property);
          item.setDetail("FormulaInfo field");
          item.setInsertText(field + ":");
          items.add(item);
        }
      }
      return items;
    }

    // ── hover (enhanced with symbol information) ──

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(null);
      }

      // Extract word at cursor position
      String word = wordAt(state.content(), params.getPosition(), state.lineOffset());

      String markdownText;

      // Try to find symbol information: catalog first, then declarations, then parse status
      if (!word.isEmpty()) {
        // 1. Catalog hover for $variable names
        if (word.startsWith("$")) {
          String varName = word.substring(1);
          String catalogDescription = server.catalogHover(varName);
          if (catalogDescription != null) {
            markdownText = "**" + word + "**\n\n" + catalogDescription + "\n\n*catalog variable*";
          } else {
            // 2. ScopeStore declared symbol hover
            String lookupName = varName;
            var declOpt = state.declarations().stream()
                .filter(d -> d.name().equals(lookupName))
                .findFirst();
            if (declOpt.isPresent()) {
              markdownText = buildSymbolHover(word);
            } else {
              markdownText = buildParseStatusHover(state.failures());
            }
          }
        } else {
          // Non-variable word: check declarations
          var declOpt = state.declarations().stream()
              .filter(d -> d.name().equals(word))
              .findFirst();
          if (declOpt.isPresent()) {
            markdownText = buildSymbolHover(word);
          } else {
            markdownText = buildParseStatusHover(state.failures());
          }
        }
      } else {
        // No word at cursor, show parse status
        markdownText = buildParseStatusHover(state.failures());
      }

      MarkupContent content = new MarkupContent();
      content.setKind("markdown");
      content.setValue(markdownText);
      return CompletableFuture.completedFuture(new Hover(content));
    }

    /** Build hover content for symbol information. */
    private String buildSymbolHover(String symbolName) {
      String typeInfo;
      String prefix = "**" + symbolName + "**";

      if (symbolName.startsWith("$")) {
        // Variable: show inferred type
        String varType = inferVariableType(symbolName);
        typeInfo = prefix + ": `" + varType + "`";
      } else {
        // Method: show signature and return type
        String returnType = inferMethodReturnType(symbolName);
        typeInfo = prefix + "() → `" + returnType + "`";
      }

      return typeInfo + "\n\n*TinyExpression P4 symbol*";
    }

    /** Build hover content for parse status. */
    private String buildParseStatusHover(ParseFailureDiagnostics failures) {
      return switch (failures) {
        case ParseFailureDiagnostics.Absent a -> {
          yield "**TinyExpression P4**\n\nDocument is valid P4.";
        }
        case ParseFailureDiagnostics.Present p -> {
          String hints = p.expectedHints().isEmpty()
              ? ""
              : "\n\n**Expected**: " + String.join(", ", p.expectedHints());
          yield "**TinyExpression P4** — Parse error at offset " + p.failureOffset() + hints;
        }
      };
    }

    // ── code actions (quick fixes) ──

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(
        CodeActionParams params) {

      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      List<Either<Command, CodeAction>> actions = new ArrayList<>();

      if (state == null) {
        return CompletableFuture.completedFuture(actions);
      }

      // ── Diagnostic-based quick fixes ──
      CodeActionContext ctx = params.getContext();
      List<Diagnostic> diagnostics = ctx.getDiagnostics();
      if (diagnostics != null && !diagnostics.isEmpty()) {
        for (Diagnostic diag : diagnostics) {
          String code = diag.getCode() instanceof Either<?,?> e
              ? (e.isLeft() ? String.valueOf(e.getLeft()) : String.valueOf(e.getRight()))
              : "";

          if ("TE001".equals(code)) {
            // TE001: parse error — suggest rewriting with P4 syntax
            actions.addAll(buildTE001QuickFixes(uri, state.content(), diag, state.lineOffset()));
          }
        }
      }

      // ── Refactor: if ↔ ternary conversion ──
      actions.addAll(buildIfTernaryRefactoring(uri, state, params.getRange()));

      return CompletableFuture.completedFuture(actions);
    }

    private List<Either<Command, CodeAction>> buildTE001QuickFixes(
        String uri, String content, Diagnostic diag, int lineOffset) {

      List<Either<Command, CodeAction>> result = new ArrayList<>();
      int offset = positionToOffsetWithOffset(content, diag.getRange().getStart(), lineOffset);
      if (offset < 0 || offset >= content.length()) {
        return result;
      }
      String remaining = content.substring(offset).stripLeading();

      // Quick fix: "if ... else ..." → "if (...) { ... } else { ... }"
      if (remaining.startsWith("if ")) {
        CodeAction ca = new CodeAction("Rewrite 'if' to P4 syntax: if (cond) { then } else { else }");
        ca.setKind(CodeActionKind.QuickFix);
        ca.setDiagnostics(List.of(diag));

        // Build a template replacement for the whole line
        Range lineRange = wholeLinesRangeWithOffset(content, diag.getRange(), lineOffset);
        String oldLine = content.substring(
            positionToOffsetWithOffset(content, lineRange.getStart(), lineOffset),
            positionToOffsetWithOffset(content, lineRange.getEnd(), lineOffset));
        String newLine = convertIfToP4Syntax(oldLine);
        if (newLine != null) {
          ca.setEdit(buildEdit(uri, lineRange, newLine));
          result.add(Either.forRight(ca));
        }
      }

      // Quick fix: "funcName(...)" without 'call' → "call funcName(...)"
      java.util.regex.Matcher m = java.util.regex.Pattern
          .compile("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
          .matcher(remaining);
      if (m.find()) {
        String funcName = m.group(1);
        if (!KEYWORD_SET.contains(funcName)) {
          CodeAction ca = new CodeAction("Add 'call' keyword: call " + funcName + "(...)");
          ca.setKind(CodeActionKind.QuickFix);
          ca.setDiagnostics(List.of(diag));
          Position insertPos = diag.getRange().getStart();
          ca.setEdit(buildEdit(uri, new Range(insertPos, insertPos), "call "));
          result.add(Either.forRight(ca));
        }
      }

      // Generic: show P4 syntax reference as hint via a no-op command
      CodeAction hint = new CodeAction(
          "P4 syntax: use 'call func()', 'if(cond){then}else{else}', 'match{cond->val,...,default->val}'");
      hint.setKind(CodeActionKind.Empty);
      hint.setDiagnostics(List.of(diag));
      result.add(Either.forRight(hint));

      return result;
    }

    // ── if ↔ ternary bidirectional refactoring ──

    private static final Pattern IF_EXPR_PATTERN = Pattern.compile(
        "if\\s*\\((.+?)\\)\\s*\\{(.+?)\\}\\s*else\\s*\\{(.+?)\\}");

    private static final Pattern TERNARY_PATTERN = Pattern.compile(
        "\\(([^?]+?)\\?([^:]+?):([^)]+?)\\)");

    private List<Either<Command, CodeAction>> buildIfTernaryRefactoring(
        String uri, ExtDocumentState state, Range cursorRange) {

      List<Either<Command, CodeAction>> result = new ArrayList<>();
      String content = state.content();
      int lineOffset = state.lineOffset();
      if (content == null) return result;

      // Determine the line the cursor is on
      int cursorLine = cursorRange.getStart().getLine() - lineOffset;
      String[] lines = content.split("\n", -1);
      if (cursorLine < 0 || cursorLine >= lines.length) return result;
      String line = lines[cursorLine];

      // Try if-expression → ternary
      Matcher ifMatcher = IF_EXPR_PATTERN.matcher(line);
      if (ifMatcher.find()) {
        String condition = ifMatcher.group(1).strip();
        String thenExpr  = ifMatcher.group(2).strip();
        String elseExpr  = ifMatcher.group(3).strip();
        String ternary   = "(" + condition + " ? " + thenExpr + " : " + elseExpr + ")";

        int matchStart = ifMatcher.start();
        int matchEnd   = ifMatcher.end();
        Range replaceRange = new Range(
            new Position(cursorLine + lineOffset, matchStart),
            new Position(cursorLine + lineOffset, matchEnd));

        CodeAction ca = new CodeAction("Convert to ternary (condition ? then : else)");
        ca.setKind(CodeActionKind.RefactorRewrite);
        ca.setEdit(buildEdit(uri, replaceRange, ternary));
        result.add(Either.forRight(ca));
      }

      // Try ternary → if-expression
      Matcher ternaryMatcher = TERNARY_PATTERN.matcher(line);
      if (ternaryMatcher.find()) {
        String condition = ternaryMatcher.group(1).strip();
        String thenExpr  = ternaryMatcher.group(2).strip();
        String elseExpr  = ternaryMatcher.group(3).strip();
        String ifExpr    = "if(" + condition + "){ " + thenExpr + " }else{ " + elseExpr + " }";

        int matchStart = ternaryMatcher.start();
        int matchEnd   = ternaryMatcher.end();
        Range replaceRange = new Range(
            new Position(cursorLine + lineOffset, matchStart),
            new Position(cursorLine + lineOffset, matchEnd));

        CodeAction ca = new CodeAction("Convert to if-else");
        ca.setKind(CodeActionKind.RefactorRewrite);
        ca.setEdit(buildEdit(uri, replaceRange, ifExpr));
        result.add(Either.forRight(ca));
      }

      return result;
    }

    private static int positionToOffsetWithOffset(String content, Position pos, int lineOffset) {
      int targetLine = pos.getLine() - lineOffset;
      if (targetLine < 0) return 0;
      int line = 0, offset = 0;
      while (offset < content.length() && line < targetLine) {
        if (content.charAt(offset++) == '\n') line++;
      }
      return Math.min(offset + pos.getCharacter(), content.length());
    }

    private Range wholeLinesRangeWithOffset(String content, Range diag, int lineOffset) {
      Position start = new Position(diag.getStart().getLine(), 0);
      int endLine = diag.getEnd().getLine();
      int endOffset = positionToOffsetWithOffset(content, new Position(endLine, 0), lineOffset);
      // extend to end of the last line
      while (endOffset < content.length() && content.charAt(endOffset) != '\n') endOffset++;
      if (endOffset < content.length()) endOffset++; // include newline
      Position end = offsetToPositionWithOffset(content, endOffset, lineOffset);
      return new Range(start, end);
    }

    /** Simple heuristic: "if a else b" → "if (a) { b_then } else { b_else }". */
    private static String convertIfToP4Syntax(String line) {
      // match "if <cond> else <value>" (basic single-line pattern)
      java.util.regex.Matcher m = java.util.regex.Pattern
          .compile("(?i)^(\\s*)if\\s+(.+?)\\s+else\\s+(.+?)\\s*$")
          .matcher(line);
      if (!m.matches()) return null;
      String indent = m.group(1);
      String cond   = m.group(2).trim();
      String els    = m.group(3).trim();
      return indent + "if (" + cond + ") { /* then */ } else { " + els + " }";
    }

    private static WorkspaceEdit buildEdit(String uri, Range range, String newText) {
      TextEdit edit = new TextEdit(range, newText);
      VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(uri, null);
      TextDocumentEdit docEdit = new TextDocumentEdit(docId, List.of(edit));
      WorkspaceEdit we = new WorkspaceEdit();
      we.setDocumentChanges(List.of(Either.forLeft(docEdit)));
      return we;
    }

    // ── go-to-definition (formula symbols + dependsOn) ──

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
        definition(DefinitionParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
      }

      // ── dependsOn go-to-definition ──
      // If cursor is on a dependsOn: line in the metadata section, jump to the
      // calculatorName: line that matches the referenced name.
      if (state.fullContent() != null) {
        int cursorLine = params.getPosition().getLine();
        String line = lineText(state.fullContent(), cursorLine);
        if (line.stripLeading().startsWith("dependsOn:")) {
          String depValue = line.substring(line.indexOf(':') + 1).strip();
          // Handle comma-separated: find which token the cursor is on
          int col = params.getPosition().getCharacter();
          String targetName = findDependsOnTokenAtColumn(line, col);
          if (targetName == null || targetName.isEmpty()) {
            targetName = depValue; // fallback: use whole value
          }

          Map<String, Integer> calcNames = collectCalculatorNames(state.fullContent());
          Integer targetLine = calcNames.get(targetName);
          if (targetLine != null) {
            String targetLineText = lineText(state.fullContent(), targetLine);
            int nameCol = targetLineText.indexOf(targetName);
            if (nameCol < 0) nameCol = 0;
            Range targetRange = new Range(
                new Position(targetLine, nameCol),
                new Position(targetLine, nameCol + targetName.length()));
            LocationLink link = new LocationLink();
            link.setTargetUri(uri);
            link.setTargetRange(targetRange);
            link.setTargetSelectionRange(targetRange);
            return CompletableFuture.completedFuture(Either.forRight(List.of(link)));
          }
          return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }
      }

      // ── formula symbol go-to-definition ──
      if (state.declarations().isEmpty()) {
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
      }
      String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
      if (word.isEmpty()) {
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
      }

      String lookupName = word.startsWith("$") ? word.substring(1) : word;
      List<LocationLink> links = new ArrayList<>();
      for (var decl : state.declarations()) {
        if (lookupName.equals(decl.name()) || word.equals(decl.name())) {
          Range range = offsetToRangeWithOffset(state.content(), decl.sourceOffset(), decl.name().length(), state.lineOffset());
          LocationLink link = new LocationLink();
          link.setTargetUri(uri);
          link.setTargetRange(range);
          link.setTargetSelectionRange(range);
          links.add(link);
        }
      }
      return CompletableFuture.completedFuture(Either.forRight(links));
    }

    /**
     * Given a dependsOn: line and cursor column, returns the comma-separated
     * token that the cursor is positioned within.
     */
    private String findDependsOnTokenAtColumn(String line, int col) {
      int colonIdx = line.indexOf(':');
      if (colonIdx < 0 || col <= colonIdx) return null;
      String valuePart = line.substring(colonIdx + 1);
      int relCol = col - (colonIdx + 1);
      // Split by comma and find which segment contains relCol
      int pos = 0;
      for (String segment : valuePart.split(",", -1)) {
        int segEnd = pos + segment.length();
        if (relCol >= pos && relCol <= segEnd) {
          return segment.strip();
        }
        pos = segEnd + 1; // +1 for comma
      }
      return valuePart.strip();
    }

    // ── find-references ──

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null || state.references().isEmpty()) {
        return CompletableFuture.completedFuture(List.of());
      }
      String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
      if (word.isEmpty()) {
        return CompletableFuture.completedFuture(List.of());
      }
      List<Location> locations = state.references().stream()
          .filter(r -> r.name().equals(word))
          .map(r -> {
            Range range = offsetToRangeWithOffset(state.content(), r.offset(), r.length(), state.lineOffset());
            return new Location(uri, range);
          })
          .collect(java.util.stream.Collectors.toList());
      return CompletableFuture.completedFuture(locations);
    }

    // ── linked editing range ──

    @Override
    public CompletableFuture<LinkedEditingRanges> linkedEditingRange(
        LinkedEditingRangeParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(new LinkedEditingRanges(Collections.emptyList()));
      }

      // Extract word at cursor position
      String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
      if (word.isEmpty()) {
        return CompletableFuture.completedFuture(new LinkedEditingRanges(Collections.emptyList()));
      }

      // Collect ranges for both declarations and references
      String lookupName = word.startsWith("$") ? word.substring(1) : word;
      Set<Range> ranges = new LinkedHashSet<>();

      // Add declaration ranges
      for (var decl : state.declarations()) {
        if (lookupName.equals(decl.name()) || word.equals(decl.name())) {
          ranges.add(offsetToRangeWithOffset(state.content(), decl.sourceOffset(), decl.name().length(), state.lineOffset()));
        }
      }

      // Add reference ranges
      for (var ref : state.references()) {
        if (lookupName.equals(ref.name()) || word.equals(ref.name())) {
          ranges.add(offsetToRangeWithOffset(state.content(), ref.offset(), ref.length(), state.lineOffset()));
        }
      }

      return CompletableFuture.completedFuture(new LinkedEditingRanges(new ArrayList<>(ranges)));
    }

    // ── document symbol (outline) ──

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
        DocumentSymbolParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null || state.declarations().isEmpty()) {
        return CompletableFuture.completedFuture(List.of());
      }

      // Group symbols by category for hierarchical display
      Map<String, List<DocumentSymbol>> symbolsByCategory = new LinkedHashMap<>();
      symbolsByCategory.put("variables", new ArrayList<>());
      symbolsByCategory.put("methods", new ArrayList<>());

      String content = state.content();
      for (ScopeStore.SymbolInfo decl : state.declarations()) {
        // Get symbol kind based on declaration name patterns
        SymbolKind kind = inferSymbolKind(decl.name());

        // Calculate range from offset and estimated length
        Range range = offsetToRangeWithOffset(content, decl.sourceOffset(), decl.name().length(), state.lineOffset());

        // Extract child symbols (e.g., method parameters)
        List<DocumentSymbol> children = extractChildSymbols(decl.name(), decl.sourceOffset(), content, state.lineOffset());

        DocumentSymbol symbol = new DocumentSymbol(
            decl.name(),
            kind,
            range,
            range,
            null
        );
        if (!children.isEmpty()) {
          symbol.setChildren(children);
        }

        // Categorize by symbol type
        if (decl.name().matches("[a-z][a-zA-Z0-9]*")) {
          symbolsByCategory.get("methods").add(symbol);
        } else if (decl.name().startsWith("$") || Character.isLowerCase(decl.name().charAt(0))) {
          symbolsByCategory.get("variables").add(symbol);
        }
      }

      // Build hierarchical symbols with category groups
      List<Either<SymbolInformation, DocumentSymbol>> symbols = new ArrayList<>();
      for (Map.Entry<String, List<DocumentSymbol>> entry : symbolsByCategory.entrySet()) {
        List<DocumentSymbol> categoryItems = entry.getValue();
        if (!categoryItems.isEmpty()) {
          DocumentSymbol categorySymbol = new DocumentSymbol(
              entry.getKey(),
              SymbolKind.Namespace,
              new Range(new Position(0, 0), new Position(0, 0)),
              new Range(new Position(0, 0), new Position(0, 0))
          );
          categorySymbol.setChildren(categoryItems);
          symbols.add(Either.forRight(categorySymbol));
        }
      }

      return CompletableFuture.completedFuture(symbols);
    }

    /** Infer SymbolKind from declaration name pattern */
    private SymbolKind inferSymbolKind(String declName) {
      // Variables typically appear with certain patterns
      if (declName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
        // Could be a method; in future, check AST
        return SymbolKind.Method;
      }
      return SymbolKind.Variable;
    }

    /** Extract child symbols (e.g., method parameters) from method definition */
    private List<DocumentSymbol> extractChildSymbols(String methodName, int offset, String content, int lineOffset) {
      // Simplified: attempt to find method parameters in parentheses
      // Future: walk AST to find MethodParameter nodes
      List<DocumentSymbol> children = new ArrayList<>();

      // Find opening parenthesis after method name
      int parenPos = offset + methodName.length();
      while (parenPos < content.length() && content.charAt(parenPos) != '(') {
        parenPos++;
      }

      if (parenPos >= content.length() || content.charAt(parenPos) != '(') {
        return children;
      }

      // Find closing parenthesis
      int closeParenPos = parenPos + 1;
      int depth = 1;
      while (closeParenPos < content.length() && depth > 0) {
        char c = content.charAt(closeParenPos);
        if (c == '(') depth++;
        else if (c == ')') depth--;
        closeParenPos++;
      }

      if (depth != 0) {
        return children;
      }

      // Extract parameters from inside parentheses
      String paramString = content.substring(parenPos + 1, closeParenPos - 1).trim();
      if (paramString.isEmpty()) {
        return children;
      }

      // Split by comma and create child symbols for each parameter
      String[] params = paramString.split(",");
      int paramOffset = parenPos + 1;
      for (String param : params) {
        param = param.trim();
        if (!param.isEmpty()) {
          // Simple extraction: take first word as parameter name
          String[] parts = param.split("\\s+");
          if (parts.length > 0) {
            String paramName = parts[0];
            Range range = offsetToRangeWithOffset(content, paramOffset, paramName.length(), lineOffset);

            DocumentSymbol child = new DocumentSymbol(
                paramName,
                SymbolKind.Variable,
                range,
                range,
                null
            );
            children.add(child);
          }
        }
        paramOffset += param.length() + 1; // +1 for comma
      }

      return children;
    }

    // ── rename (refactoring) ──

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(new WorkspaceEdit(Map.of()));
      }

      // Extract word at cursor position
      String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
      if (word.isEmpty()) {
        return CompletableFuture.completedFuture(new WorkspaceEdit(Map.of()));
      }

      String newName = params.getNewName();
      if (newName.isEmpty() || !newName.matches("[a-zA-Z_][a-zA-Z0-9_$]*")) {
        return CompletableFuture.completedFuture(new WorkspaceEdit(Map.of()));
      }

      // Collect all occurrences (definition + references)
      List<Integer> offsets = new ArrayList<>();

      // Add definition offset if found
      state.declarations().stream()
          .filter(d -> d.name().equals(word))
          .map(ScopeStore.SymbolInfo::sourceOffset)
          .forEach(offsets::add);

      // Add all reference offsets
      state.references().stream()
          .filter(r -> r.name().equals(word))
          .map(ScopeStore.ReferenceInfo::offset)
          .forEach(offsets::add);

      if (offsets.isEmpty()) {
        return CompletableFuture.completedFuture(new WorkspaceEdit(Map.of()));
      }

      // Create TextEdit[] for all occurrences
      List<TextEdit> edits = offsets.stream()
          .map(offset -> {
            Range range = offsetToRangeWithOffset(state.content(), offset, word.length(), state.lineOffset());
            return new TextEdit(range, newName);
          })
          .collect(java.util.stream.Collectors.toList());

      // Return WorkspaceEdit with single document URI
      return CompletableFuture.completedFuture(
          new WorkspaceEdit(Map.of(uri, edits))
      );
    }

    // ── document highlight (same identifier highlighting) ──

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null || state.references().isEmpty()) {
        return CompletableFuture.completedFuture(List.of());
      }

      String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
      if (word.isEmpty()) {
        return CompletableFuture.completedFuture(List.of());
      }

      List<DocumentHighlight> highlights = state.references().stream()
          .filter(r -> r.name().equals(word))
          .map(r -> {
            Range range = offsetToRangeWithOffset(state.content(), r.offset(), r.length(), state.lineOffset());
            return new DocumentHighlight(
                range,
                DocumentHighlightKind.Text
            );
          })
          .collect(java.util.stream.Collectors.toList());

      return CompletableFuture.completedFuture(highlights);
    }

    // ── signature help (parameter hints) ──

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null || state.declarations().isEmpty()) {
        return CompletableFuture.completedFuture(new SignatureHelp(List.of(), null, null));
      }

      // Adjust position for line offset
      String content = state.content();
      int offset = positionToOffsetWithOffset(content, params.getPosition(), state.lineOffset());
      if (offset < 0 || offset >= content.length()) {
        return CompletableFuture.completedFuture(new SignatureHelp(List.of(), null, null));
      }

      // Scan backward to find opening parenthesis
      int parenPos = offset;
      while (parenPos > 0 && content.charAt(parenPos) != '(') {
        parenPos--;
      }
      if (parenPos <= 0 || content.charAt(parenPos) != '(') {
        return CompletableFuture.completedFuture(new SignatureHelp(List.of(), null, null));
      }

      // Extract method name before parenthesis
      int nameEnd = parenPos;
      int nameStart = parenPos - 1;
      while (nameStart >= 0 && (Character.isLetterOrDigit(content.charAt(nameStart)) || content.charAt(nameStart) == '_')) {
        nameStart--;
      }
      nameStart++;

      if (nameStart >= nameEnd) {
        return CompletableFuture.completedFuture(new SignatureHelp(List.of(), null, null));
      }

      String methodName = content.substring(nameStart, nameEnd);

      // Find method in declarations
      var methodSig = state.declarations().stream()
          .filter(d -> d.name().equals(methodName))
          .findFirst();

      if (methodSig.isEmpty()) {
        return CompletableFuture.completedFuture(new SignatureHelp(List.of(), null, null));
      }

      // Extract parameter count and return type from method name pattern
      // In future: would traverse AST to extract actual MethodParameter nodes
      String returnType = inferReturnType(methodName);
      int paramCount = estimateParameterCount(content, parenPos);

      // Build simplified parameter info (counts parameters from call site)
      List<ParameterInformation> params_info = new ArrayList<>();
      for (int i = 0; i < paramCount; i++) {
        params_info.add(new ParameterInformation(
            "$param" + (i + 1),
            "Parameter " + (i + 1)
        ));
      }

      // Create signature with estimated parameters
      String signature = buildSignature(methodName, paramCount, returnType);
      SignatureInformation sig = new SignatureInformation(
          signature,
          "Method: " + methodName,
          params_info
      );

      // Calculate active parameter from cursor position
      int activeParam = countCommasBeforeCursor(content, offset, parenPos);

      return CompletableFuture.completedFuture(
          new SignatureHelp(List.of(sig), 0, activeParam)
      );
    }

    /** Infer return type from method name pattern (simplified heuristic) */
    private String inferReturnType(String methodName) {
      if (methodName.startsWith("is") || methodName.startsWith("check")) {
        return "boolean";
      }
      if (methodName.startsWith("count") || methodName.startsWith("get_number")) {
        return "number";
      }
      if (methodName.startsWith("get_string") || methodName.startsWith("format")) {
        return "string";
      }
      return "any";
    }

    /** Estimate parameter count by analyzing method call arguments */
    private int estimateParameterCount(String content, int openParenPos) {
      int closeParenPos = openParenPos + 1;
      int depth = 1;
      while (closeParenPos < content.length() && depth > 0) {
        char c = content.charAt(closeParenPos);
        if (c == '(') depth++;
        else if (c == ')') depth--;
        closeParenPos++;
      }

      if (depth == 0) {
        String args = content.substring(openParenPos + 1, closeParenPos - 1).trim();
        if (args.isEmpty()) return 0;
        // Simple heuristic: count commas + 1
        return 1 + (int) args.chars().filter(c -> c == ',').count();
      }
      return 0;
    }

    /** Count commas before cursor to determine active parameter */
    private int countCommasBeforeCursor(String content, int cursorOffset, int openParenPos) {
      int commaCount = 0;
      for (int i = openParenPos + 1; i < cursorOffset && i < content.length(); i++) {
        if (content.charAt(i) == ',') commaCount++;
      }
      return commaCount;
    }

    /** Build signature string from method name, param count, and return type */
    private String buildSignature(String methodName, int paramCount, String returnType) {
      StringBuilder sb = new StringBuilder(methodName).append("(");
      for (int i = 0; i < paramCount; i++) {
        if (i > 0) sb.append(", ");
        sb.append("$param").append(i + 1);
      }
      sb.append(") → ").append(returnType);
      return sb.toString();
    }

    // ── code lens (DAP-integrated evaluation display) ──

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(List.of());
      }

      // Create code lenses for assignment statements with evaluated results
      List<CodeLens> lenses = new ArrayList<>();
      String content = state.content();
      int count = 0;
      int maxLenses = 5; // Limit to avoid overwhelming UI

      for (int i = 0; i < content.length() - 1 && count < maxLenses; i++) {
        if (content.charAt(i) == '=' && i + 1 < content.length() && content.charAt(i + 1) != '=') {
          // Extract the RHS expression (from '=' to next statement boundary)
          String rhsExpression = extractRhsExpression(content, i + 1);
          if (!rhsExpression.isEmpty()) {
            // Evaluate the expression (try to parse and get type/hint)
            String evaluationResult = evaluateExpressionHint(rhsExpression);

            Range range = offsetToRangeWithOffset(content, i, 1, state.lineOffset());

            // Create code lens with evaluation result in title
            String lensTitle = "= " + evaluationResult;
            Command cmd = new Command(
                lensTitle,
                "tinyExpressionP4Lsp.evaluateExpression",
                List.of(uri, i)
            );

            CodeLens lens = new CodeLens(range);
            lens.setCommand(cmd);
            lenses.add(lens);
            count++;
          }
        }
      }

      return CompletableFuture.completedFuture(lenses);
    }

    /** Extract RHS expression from '=' operator position. */
    private String extractRhsExpression(String content, int afterEqualPos) {
      // Find the end of the expression (next semicolon or end of line)
      int start = afterEqualPos;
      while (start < content.length() && Character.isWhitespace(content.charAt(start))) {
        start++;
      }
      if (start >= content.length()) return "";

      int end = start;
      int depth = 0;
      while (end < content.length()) {
        char c = content.charAt(end);
        if (c == '(' || c == '[') depth++;
        else if (c == ')' || c == ']') depth--;
        else if ((c == ';' || c == '\n') && depth == 0) break;
        end++;
      }

      return content.substring(start, end).trim();
    }

    // ── inline hints (variable type hints) ──

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(List.of());
      }

      // Find variable assignments and create type hints
      List<InlayHint> hints = new ArrayList<>();
      String content = state.content();
      String lowerContent = content.toLowerCase();

      // Pattern: "var" or "variable" keyword followed by identifier and assignment
      int searchPos = 0;
      while ((searchPos = lowerContent.indexOf("var", searchPos)) != -1) {
        // Check if this is a variable declaration (preceded by non-alphanumeric)
        if (searchPos == 0 || !Character.isLetterOrDigit(lowerContent.charAt(searchPos - 1))) {
          int varEnd = searchPos + 3;

          // Skip to identifier
          while (varEnd < content.length() && Character.isWhitespace(content.charAt(varEnd))) {
            varEnd++;
          }

          // Skip identifier (until '=' or whitespace)
          int idEnd = varEnd;
          while (idEnd < content.length() && Character.isLetterOrDigit(content.charAt(idEnd))) {
            idEnd++;
          }

          if (idEnd > varEnd) {
            String varName = content.substring(varEnd, idEnd).trim();

            // Look for '=' after identifier
            int eqPos = idEnd;
            while (eqPos < content.length() && content.charAt(eqPos) != '=') {
              if (content.charAt(eqPos) == ';' || content.charAt(eqPos) == '\n') break;
              eqPos++;
            }

            if (eqPos < content.length() && content.charAt(eqPos) == '=') {
              // Extract RHS expression
              String rhsExpr = extractRhsExpression(content, eqPos + 1);
              if (!rhsExpr.isEmpty()) {
                // Infer type
                String typeHint = evaluateExpressionHint(rhsExpr);

                // Create InlayHint after variable name
                Position hintPos = offsetToPositionWithOffset(content, idEnd, state.lineOffset());

                InlayHint hint = new InlayHint(
                    hintPos,
                    Either.forLeft(" : " + typeHint)
                );
                hint.setKind(InlayHintKind.Type);
                hints.add(hint);

                // Limit to 20 hints per document
                if (hints.size() >= 20) break;
              }
            }
          }
        }
        searchPos++;
      }

      return CompletableFuture.completedFuture(hints);
    }

    // ── folding range (code block folding) ──

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null || state.ast() == null) {
        return CompletableFuture.completedFuture(List.of());
      }

      // Scan content for if/match keywords to create folding ranges
      List<FoldingRange> ranges = new ArrayList<>();
      String content = state.content();
      String lowerContent = content.toLowerCase();

      // Find if/then/else/endif blocks
      int searchPos = 0;
      while ((searchPos = lowerContent.indexOf("if ", searchPos)) != -1) {
        if (searchPos == 0 || !Character.isLetterOrDigit(lowerContent.charAt(searchPos - 1))) {
          // Find corresponding endif
          int endPos = lowerContent.indexOf("endif", searchPos);
          if (endPos > searchPos) {
            Position start = offsetToPositionWithOffset(content, searchPos, state.lineOffset());
            Position end = offsetToPositionWithOffset(content, endPos + 5, state.lineOffset());

            FoldingRange range = new FoldingRange(start.getLine(), end.getLine());
            range.setKind(FoldingRangeKind.Region);
            ranges.add(range);
          }
        }
        searchPos++;
      }

      // Find match blocks
      searchPos = 0;
      while ((searchPos = lowerContent.indexOf("match ", searchPos)) != -1) {
        if (searchPos == 0 || !Character.isLetterOrDigit(lowerContent.charAt(searchPos - 1))) {
          // Find corresponding 'end' or similar keyword
          int endPos = lowerContent.indexOf(" end", searchPos);
          if (endPos > searchPos) {
            Position start = offsetToPositionWithOffset(content, searchPos, state.lineOffset());
            Position end = offsetToPositionWithOffset(content, endPos + 4, state.lineOffset());

            FoldingRange range = new FoldingRange(start.getLine(), end.getLine());
            range.setKind(FoldingRangeKind.Region);
            ranges.add(range);
          }
        }
        searchPos++;
      }

      return CompletableFuture.completedFuture(ranges);
    }

    /** Provide expression type hint by analyzing expression structure (heuristic, no evaluation). */
    private String evaluateExpressionHint(String expr) {
      if (expr == null || expr.isEmpty()) return "?";

      // Try to parse and get AST for better type detection
      try {
        TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse(expr);
        // Infer type from AST node
        return inferTypeFromAst(ast);
      } catch (Exception parseError) {
        // Fallback to pattern-based heuristics
        return inferTypeFromPattern(expr);
      }
    }

    /** Infer type from parsed AST node. */
    private String inferTypeFromAst(TinyExpressionP4AST ast) {
      if (ast == null) return "?";

      return switch (ast) {
        case TinyExpressionP4AST.StringConcatExpr ignored -> "str";
        case TinyExpressionP4AST.BooleanOrExpr ignored -> "bool";
        case TinyExpressionP4AST.BinaryExpr b -> {
          // Check operator type from the ops list
          if (!b.op().isEmpty()) {
            String op = b.op().get(0);
            if ("==".equals(op) || "!=".equals(op) || "<".equals(op) || ">".equals(op) || "<=".equals(op) || ">=".equals(op)) {
              yield "bool";
            }
          }
          yield "num";  // arithmetic ops return number
        }
        case TinyExpressionP4AST.VariableRefExpr ignored -> "var";
        case TinyExpressionP4AST.MethodInvocationExpr m -> inferMethodReturnType(m.name());
        case TinyExpressionP4AST.ComparisonExpr ignored -> "bool";
        case TinyExpressionP4AST.StringComparisonExpr ignored -> "bool";
        default -> "expr";
      };
    }

    /** Infer type from expression text pattern. */
    private String inferTypeFromPattern(String expr) {
      // Numeric patterns
      if (expr.matches("^\\d+(\\.\\d+)?$")) return "number";
      if (expr.matches("^'[^']*'$")) return "string";
      if ("true".equals(expr) || "false".equals(expr)) return "boolean";

      // Variable reference
      if (expr.startsWith("$")) return "var";

      // Method call
      if (expr.contains("(") && expr.contains(")")) {
        int parenPos = expr.indexOf("(");
        String methodName = expr.substring(0, parenPos).trim();
        return inferMethodReturnType(methodName);
      }

      // Arithmetic operators suggest number
      if (expr.matches(".*[+\\-*/].*") && !expr.contains("==") && !expr.contains("!=")) return "number";

      // Comparison operators suggest boolean
      if (expr.matches(".*(==|!=|<|>|<=|>=).*")) return "boolean";

      return "expr";
    }

    /** Infer return type from method name pattern. */
    private String inferMethodReturnType(String methodName) {
      if (methodName == null) return "any";
      if (methodName.startsWith("is") || methodName.startsWith("check")) return "bool";
      if (methodName.startsWith("count") || methodName.startsWith("get_number")) return "num";
      if (methodName.startsWith("get_string") || methodName.startsWith("format")) return "str";
      return "any";
    }

    /** カーソル位置の単語を返す（$identifier の場合は $ も含める）。 */
    private String wordAt(String content, Position position, int lineOffset) {
      int line = position.getLine() - lineOffset;
      int col  = position.getCharacter();
      if (line < 0) return "";
      int offset = 0;
      String[] lines = content.split("\n", -1);
      for (int i = 0; i < line && i < lines.length; i++) offset += lines[i].length() + 1;
      offset += col;
      if (offset >= content.length()) return "";

      int s = offset, e = offset;
      // move back (including '$')
      while (s > 0 && (Character.isLetterOrDigit(content.charAt(s - 1)) || content.charAt(s - 1) == '$' || content.charAt(s - 1) == '_')) {
        s--;
      }
      // move forward
      while (e < content.length() && (Character.isLetterOrDigit(content.charAt(e)) || content.charAt(e) == '$' || content.charAt(e) == '_')) {
        e++;
      }
      return content.substring(s, e);
    }

    // ── semantic tokens ──

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(new SemanticTokens(Collections.emptyList()));
      }
      // Formula section tokens (TinyExpression parse tree)
      List<Integer> formulaData = server.computeSemanticTokens(state.content(), state.lineOffset());

      // Java code block tokens (regex-based highlighting)
      List<Integer> javaData = server.computeJavaBlockSemanticTokens(state.fullContent());

      // Merge: decode both streams into absolute tokens, sort, re-encode
      List<Integer> merged = mergeSemanticTokenData(formulaData, javaData);
      return CompletableFuture.completedFuture(new SemanticTokens(merged));
    }

    /**
     * Decodes delta-encoded semantic token data into absolute SemanticToken records.
     */
    private static List<SemanticToken> decodeTokenData(List<Integer> data) {
      List<SemanticToken> tokens = new ArrayList<>();
      int line = 0, chr = 0;
      for (int i = 0; i + 4 < data.size(); i += 5) {
        int deltaLine = data.get(i);
        int deltaChar = data.get(i + 1);
        int length    = data.get(i + 2);
        int tokenType = data.get(i + 3);
        // data.get(i + 4) is modifiers — ignored for merge
        if (deltaLine > 0) { line += deltaLine; chr = deltaChar; }
        else { chr += deltaChar; }
        tokens.add(new SemanticToken(line, chr, length, tokenType));
      }
      return tokens;
    }

    /**
     * Merges two delta-encoded semantic token streams into a single stream.
     * Tokens are sorted by (line, startChar) and re-encoded as deltas.
     */
    private static List<Integer> mergeSemanticTokenData(List<Integer> a, List<Integer> b) {
      if (a.isEmpty()) return b;
      if (b.isEmpty()) return a;

      List<SemanticToken> all = new ArrayList<>();
      all.addAll(decodeTokenData(a));
      all.addAll(decodeTokenData(b));
      all.sort(Comparator.comparingInt(SemanticToken::line)
          .thenComparingInt(SemanticToken::startChar));

      List<Integer> result = new ArrayList<>();
      int prevLine = 0, prevChar = 0;
      for (SemanticToken t : all) {
        int deltaLine = t.line() - prevLine;
        int deltaChar = (deltaLine == 0) ? (t.startChar() - prevChar) : t.startChar();
        result.add(deltaLine);
        result.add(deltaChar);
        result.add(t.length());
        result.add(t.tokenType());
        result.add(0);
        prevLine = t.line();
        prevChar = t.startChar();
      }
      return result;
    }

    // ── callHierarchy ──

    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(
        CallHierarchyPrepareParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(Collections.emptyList());
      }

      // Extract word at cursor position
      String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
      if (word.isEmpty()) {
        return CompletableFuture.completedFuture(Collections.emptyList());
      }

      // Check if it's a declared method name
      List<CallHierarchyItem> items = new ArrayList<>();
      for (var symbolInfo : state.declarations()) {
        if (word.equals(symbolInfo.name())) {
          // Find the range of the method name
          Range range = offsetToRangeWithOffset(state.content(), symbolInfo.sourceOffset(), word.length(), state.lineOffset());
          CallHierarchyItem item = new CallHierarchyItem();
          item.setName(word);
          item.setKind(SymbolKind.Method);
          item.setRange(range);
          item.setSelectionRange(range);
          item.setUri(uri);
          items.add(item);
          break;
        }
      }

      return CompletableFuture.completedFuture(items);
    }

    public CompletableFuture<List<CallHierarchyIncomingCall>> incomingCalls(
        CallHierarchyIncomingCallsParams params) {
      CallHierarchyItem item = params.getItem();
      String methodName = item.getName();
      String uri = item.getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(Collections.emptyList());
      }

      List<CallHierarchyIncomingCall> incoming = new ArrayList<>();

      // Find all references to methodName
      for (var ref : state.references()) {
        if (methodName.equals(ref.name())) {
          // Get the range of the reference
          Range range = offsetToRangeWithOffset(state.content(), ref.offset(), methodName.length(), state.lineOffset());

          CallHierarchyIncomingCall call = new CallHierarchyIncomingCall();
          CallHierarchyItem fromItem = new CallHierarchyItem();
          fromItem.setName(methodName);
          fromItem.setKind(SymbolKind.Method);
          fromItem.setRange(range);
          fromItem.setSelectionRange(range);
          fromItem.setUri(uri);
          call.setFrom(fromItem);
          call.setFromRanges(List.of(range));

          incoming.add(call);
        }
      }

      return CompletableFuture.completedFuture(incoming);
    }

    public CompletableFuture<List<CallHierarchyOutgoingCall>> outgoingCalls(
        CallHierarchyOutgoingCallsParams params) {
      // MVP: Return empty list (outgoing calls not yet implemented)
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    // ── formatting (Alt+Shift+F) ──

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(
        org.eclipse.lsp4j.DocumentFormattingParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(Collections.emptyList());
      }

      String content = state.content();
      org.eclipse.lsp4j.FormattingOptions options = params.getOptions();
      int tabSize = options.getTabSize();
      String indentUnit = options.isInsertSpaces() ? " ".repeat(tabSize) : "\t";

      String formatted = formatDocument(content, indentUnit);
      if (formatted.equals(content)) {
        return CompletableFuture.completedFuture(Collections.emptyList());
      }

      // Replace whole document
      Range range = new Range(new Position(0, 0), offsetToPositionWithOffset(content, content.length(), 0));
      return CompletableFuture.completedFuture(List.of(new TextEdit(range, formatted)));
    }

    private String formatDocument(String content, String indentUnit) {
      StringBuilder sb = new StringBuilder();
      String[] lines = content.split("\n", -1);
      int indentLevel = 0;

      for (String line : lines) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          sb.append("\n");
          continue;
        }

        // Simplistic P4 indent logic
        boolean dedent = trimmed.startsWith("}") || trimmed.startsWith("else") || trimmed.startsWith("default");
        int currentIndent = dedent ? Math.max(0, indentLevel - 1) : indentLevel;

        sb.append(indentUnit.repeat(currentIndent)).append(trimmed).append("\n");

        // Adjust indent level AFTER appending
        if (trimmed.endsWith("{") || (trimmed.startsWith("if") && !trimmed.contains("endif"))) {
          indentLevel++;
        }
        if (trimmed.startsWith("}") || trimmed.contains("endif")) {
          indentLevel = Math.max(0, indentLevel - 1);
        }
      }

      // Remove last trailing newline added in loop if original didn't have it
      String result = sb.toString();
      if (!content.endsWith("\n") && result.endsWith("\n")) {
        result = result.substring(0, result.length() - 1);
      }
      return result;
    }
  }

  /**
   * Workspace symbols provider.
   */
  static class ExtWorkspaceService implements WorkspaceService {
    private final TinyExpressionP4LanguageServerExt server;

    ExtWorkspaceService(TinyExpressionP4LanguageServerExt server) {
      this.server = server;
    }

    @Override
    public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends org.eclipse.lsp4j.WorkspaceSymbol>>> symbol(
        org.eclipse.lsp4j.WorkspaceSymbolParams params) {
      String query = params.getQuery().toLowerCase();
      List<SymbolInformation> results = new ArrayList<>();

      for (Map.Entry<String, ExtDocumentState> entry : server.extDocuments.entrySet()) {
        String uri = entry.getKey();
        ExtDocumentState state = entry.getValue();

        for (var decl : state.declarations()) {
          if (decl.name().toLowerCase().contains(query)) {
            Range range = TinyExpressionP4LanguageServerExt.offsetToRangeWithOffset(state.content(), decl.sourceOffset(), decl.name().length(), state.lineOffset());

            SymbolInformation info = new SymbolInformation();
            info.setName(decl.name());
            info.setKind(inferSymbolKind(decl.name()));
            info.setLocation(new Location(uri, range));
            results.add(info);
          }
        }
      }
      return CompletableFuture.completedFuture(Either.forLeft(results));
    }

    private SymbolKind inferSymbolKind(String name) {
      if (name.startsWith("$")) return SymbolKind.Variable;
      if (Character.isUpperCase(name.charAt(0))) return SymbolKind.Class;
      return SymbolKind.Method;
    }

    @Override
    public void didChangeConfiguration(org.eclipse.lsp4j.DidChangeConfigurationParams params) {}

    @Override
    public void didChangeWatchedFiles(org.eclipse.lsp4j.DidChangeWatchedFilesParams params) {}
  }

}
