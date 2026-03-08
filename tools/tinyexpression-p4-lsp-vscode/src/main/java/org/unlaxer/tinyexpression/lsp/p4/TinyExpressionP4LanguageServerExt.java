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

  private static final List<String> SEMANTIC_TOKEN_TYPES = List.of(
      "keyword", "variable", "number", "string", "operator", "function", "comment");

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

  // ── State ──

  private final DocumentFilter documentFilter;
  private LanguageClient extClient;
  private final Map<String, ExtDocumentState> extDocuments = new HashMap<>();

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

    cap.setCodeActionProvider(true);
    cap.setDocumentSymbolProvider(true);
    cap.setRenameProvider(true);
    cap.setDocumentHighlightProvider(true);
    cap.setSignatureHelpProvider(new org.eclipse.lsp4j.SignatureHelpOptions(List.of("(", ",")));
    cap.setCodeLensProvider(new org.eclipse.lsp4j.CodeLensOptions(false));
    cap.setInlayHintProvider(true);
    cap.setFoldingRangeProvider(true);

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
      return parseAndEnrich(uri, fs.content(), fs.lineOffset());
    }

    // Plain expression file: parse directly (bypasses parent so we can capture ctx diagnostics).
    return parseAndEnrich(uri, content, 0);
  }

  /**
   * Core parse + enrich pipeline. Parses {@code formulaContent}, captures
   * {@link org.unlaxer.context.ParseFailureDiagnostics} from the {@link ParseContext} before
   * closing it, then publishes enriched LSP diagnostics with parser-derived offset and hints.
   *
   * @param uri            document URI
   * @param formulaContent formula text to parse (may be a sub-section of the full document)
   * @param lineOffset     number of lines to add to parser positions when publishing diagnostics
   */
  private ParseResult parseAndEnrich(String uri, String formulaContent, int lineOffset) {
    StringSource source = createRootSource(formulaContent);
    ParseResult result;
    org.unlaxer.context.ParseFailureDiagnostics ctxDiag = null;
    List<ScopeStore.SymbolDiagnostic> scopeDiagnostics = List.of();
    List<ScopeStore.SymbolInfo>       declarations     = List.of();
    List<ScopeStore.ReferenceInfo>    references       = List.of();

    if (source == null) {
      result = new ParseResult(false, 0, formulaContent.length());
    } else {
      Parser rootParser = TinyExpressionP4Parsers.getRootParser();
      ParseContext ctx = new ParseContext(source);
      Parsed parsed;
      try {
        parsed = rootParser.parse(ctx);
        // Capture before close: gives us farthest offset + expected-hint candidates.
        ctxDiag       = ctx.getParseFailureDiagnostics();
        scopeDiagnostics = ScopeStore.getDiagnostics(ctx);
        declarations  = ScopeStore.getAllDeclarations(ctx);
        references    = ScopeStore.getAllReferences(ctx);
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

    TinyExpressionP4AST ast = null;
    if (result.succeeded() && result.consumedLength() == result.totalLength()) {
      try { ast = TinyExpressionP4Mapper.parse(formulaContent); } catch (Exception ignored) {}
    }

    ParseFailureDiagnostics failures = buildFailureDiagnostics(result, formulaContent, ctxDiag);
    extDocuments.put(uri, new ExtDocumentState(formulaContent, result, ast, failures, declarations, references, lineOffset));

    if (extClient != null) {
      publishEnrichedDiagnostics(uri, formulaContent, failures, scopeDiagnostics, lineOffset);
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
  // getWorkspaceService — delegate to generated no-op
  // =========================================================================

  @Override
  public WorkspaceService getWorkspaceService() {
    return super.getWorkspaceService();
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
      int lineOffset) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    // Semantic diagnostics from ScopeStore (@declares / @backref)
    for (ScopeStore.SymbolDiagnostic sd : scopeDiagnostics) {
      Position rawStart = offsetToPosition(content, sd.offset());
      Position rawEnd   = offsetToPosition(content, sd.offset() + sd.length());
      Position start = new Position(rawStart.getLine() + lineOffset, rawStart.getCharacter());
      Position end   = new Position(rawEnd.getLine()   + lineOffset, rawEnd.getCharacter());
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

      Position rawStart = offsetToPosition(content, offset);
      // Range end: end of the failing line — more focused than end-of-file.
      int lineEnd = offset;
      while (lineEnd < content.length() && content.charAt(lineEnd) != '\n') lineEnd++;
      Position rawEnd = offsetToPosition(content, lineEnd);

      // Shift positions by lineOffset so they point into the original document.
      Position start = new Position(rawStart.getLine() + lineOffset, rawStart.getCharacter());
      Position end   = new Position(rawEnd.getLine()   + lineOffset, rawEnd.getCharacter());

      Diagnostic d = new Diagnostic();
      d.setRange(new Range(start, end));
      d.setSeverity(DiagnosticSeverity.Error);
      d.setSource("tinyexpression-p4");
      d.setCode(Either.forLeft("TE001"));
      // Prefer explicit expected-hint (e.g. "Expected ','") over generic snippet message.
      String message;
      if (!failures.expectedHints().isEmpty()) {
        message = failures.expectedHints().get(0);
      } else if (snippet.isEmpty()) {
        message = "Unexpected end of input";
      } else {
        message = "Unexpected token: '" + snippet + "'";
      }
      d.setMessage(message);
      diagnostics.add(d);
    }
    extClient.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
  }

  private static Position offsetToPosition(String content, int offset) {
    int line = 0, col = 0;
    for (int i = 0; i < offset && i < content.length(); i++) {
      if (content.charAt(i) == '\n') { line++; col = 0; } else { col++; }
    }
    return new Position(line, col);
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
      int lineOffset) {}

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
      server.extDocuments.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {}

    // ── completion (enhanced with method/variable autocomplete) ──

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
        CompletionParams params) {

      List<CompletionItem> items = new ArrayList<>();
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);

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

        // 3. Variable references from document text (fallback for undeclared variables)
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

      // Try to find symbol information in declarations
      if (!word.isEmpty()) {
        var declOpt = state.declarations().stream()
            .filter(d -> d.name().equals(word))
            .findFirst();

        if (declOpt.isPresent()) {
          // Show symbol type information
          var decl = declOpt.get();
          markdownText = buildSymbolHover(word);
        } else {
          // Fallback to parse status
          markdownText = buildParseStatusHover(state.failures());
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

      CodeActionContext ctx = params.getContext();
      List<Diagnostic> diagnostics = ctx.getDiagnostics();
      if (diagnostics == null || diagnostics.isEmpty()) {
        return CompletableFuture.completedFuture(actions);
      }

      for (Diagnostic diag : diagnostics) {
        String code = diag.getCode() instanceof Either<?,?> e
            ? (e.isLeft() ? String.valueOf(e.getLeft()) : String.valueOf(e.getRight()))
            : "";

        if ("TE001".equals(code)) {
          // TE001: parse error — suggest rewriting with P4 syntax
          actions.addAll(buildTE001QuickFixes(uri, state.content(), diag));
        }
      }

      return CompletableFuture.completedFuture(actions);
    }

    private List<Either<Command, CodeAction>> buildTE001QuickFixes(
        String uri, String content, Diagnostic diag) {

      List<Either<Command, CodeAction>> result = new ArrayList<>();
      int offset = positionToOffset(content, diag.getRange().getStart());
      String remaining = content.substring(offset).stripLeading();

      // Quick fix: "if ... else ..." → "if (...) { ... } else { ... }"
      if (remaining.startsWith("if ")) {
        CodeAction ca = new CodeAction("Rewrite 'if' to P4 syntax: if (cond) { then } else { else }");
        ca.setKind(CodeActionKind.QuickFix);
        ca.setDiagnostics(List.of(diag));

        // Build a template replacement for the whole line
        Range lineRange = wholeLinesRange(content, diag.getRange());
        String oldLine = content.substring(
            positionToOffset(content, lineRange.getStart()),
            positionToOffset(content, lineRange.getEnd()));
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

    private static int positionToOffset(String content, Position pos) {
      int line = 0, offset = 0;
      while (offset < content.length() && line < pos.getLine()) {
        if (content.charAt(offset++) == '\n') line++;
      }
      return Math.min(offset + pos.getCharacter(), content.length());
    }

    private static Range wholeLinesRange(String content, Range diag) {
      Position start = new Position(diag.getStart().getLine(), 0);
      int endLine = diag.getEnd().getLine();
      int endOffset = positionToOffset(content, new Position(endLine, 0));
      // extend to end of the last line
      while (endOffset < content.length() && content.charAt(endOffset) != '\n') endOffset++;
      if (endOffset < content.length()) endOffset++; // include newline
      int[] lc = offsetToLineChar(content, endOffset);
      return new Range(start, new Position(lc[0], lc[1]));
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

    // ── go-to-definition ──

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
        definition(DefinitionParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null || state.declarations().isEmpty()) {
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
      }
      String word = wordAt(state.content(), params.getPosition(), state.lineOffset());
      if (word.isEmpty()) {
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
      }
      List<Location> locations = state.declarations().stream()
          .filter(d -> d.name().equals(word))
          .map(d -> {
            int abs = d.sourceOffset();
            Position pos = offsetToPosition(state.content(), abs);
            Position shifted = new Position(pos.getLine() + state.lineOffset(), pos.getCharacter());
            return new Location(uri, new Range(shifted, shifted));
          })
          .collect(java.util.stream.Collectors.toList());
      return CompletableFuture.completedFuture(Either.forLeft(locations));
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
            Position start = offsetToPosition(state.content(), r.offset());
            Position end   = offsetToPosition(state.content(), r.offset() + r.length());
            Position sShifted = new Position(start.getLine() + state.lineOffset(), start.getCharacter());
            Position eShifted = new Position(end.getLine()   + state.lineOffset(), end.getCharacter());
            return new Location(uri, new Range(sShifted, eShifted));
          })
          .collect(java.util.stream.Collectors.toList());
      return CompletableFuture.completedFuture(locations);
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
        Position start = offsetToPosition(content, decl.sourceOffset());
        Position end = new Position(start.getLine(), start.getCharacter() + decl.name().length());

        // Apply line offset
        Position sShifted = new Position(start.getLine() + state.lineOffset(), start.getCharacter());
        Position eShifted = new Position(end.getLine() + state.lineOffset(), end.getCharacter());

        // Extract child symbols (e.g., method parameters)
        List<DocumentSymbol> children = extractChildSymbols(decl.name(), decl.sourceOffset(), content, state.lineOffset());

        DocumentSymbol symbol = new DocumentSymbol(
            decl.name(),
            kind,
            new Range(sShifted, eShifted),
            new Range(sShifted, eShifted),
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
            Position start = offsetToPosition(content, paramOffset);
            Position end = new Position(start.getLine(), start.getCharacter() + paramName.length());
            Position sShifted = new Position(start.getLine() + lineOffset, start.getCharacter());
            Position eShifted = new Position(end.getLine() + lineOffset, end.getCharacter());

            DocumentSymbol child = new DocumentSymbol(
                paramName,
                SymbolKind.Variable,
                new Range(sShifted, eShifted),
                new Range(sShifted, eShifted),
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
      if (newName.isEmpty() || !newName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
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
            Position start = offsetToPosition(state.content(), offset);
            Position end = new Position(start.getLine(), start.getCharacter() + word.length());

            Position sShifted = new Position(start.getLine() + state.lineOffset(), start.getCharacter());
            Position eShifted = new Position(end.getLine() + state.lineOffset(), end.getCharacter());

            return new TextEdit(new Range(sShifted, eShifted), newName);
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
            Position start = offsetToPosition(state.content(), r.offset());
            Position end = new Position(start.getLine(), start.getCharacter() + r.length());

            Position sShifted = new Position(start.getLine() + state.lineOffset(), start.getCharacter());
            Position eShifted = new Position(end.getLine() + state.lineOffset(), end.getCharacter());

            return new DocumentHighlight(
                new Range(sShifted, eShifted),
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
      Position adjustedPos = new Position(
          params.getPosition().getLine() - state.lineOffset(),
          params.getPosition().getCharacter()
      );
      String content = state.content();
      int offset = positionToOffset(content, adjustedPos);
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

            int[] lc = offsetToLineChar(content, i);
            Position pos = new Position(lc[0] + state.lineOffset(), lc[1]);
            Range range = new Range(pos, new Position(lc[0] + state.lineOffset(), lc[1] + 1));

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
                int[] lc = offsetToLineChar(content, idEnd);
                Position hintPos = new Position(
                    lc[0] + state.lineOffset(),
                    lc[1]
                );

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
            int[] startLC = offsetToLineChar(content, searchPos);
            int[] endLC = offsetToLineChar(content, endPos + 5);

            FoldingRange range = new FoldingRange(startLC[0], endLC[0]);
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
            int[] startLC = offsetToLineChar(content, searchPos);
            int[] endLC = offsetToLineChar(content, endPos + 4);

            FoldingRange range = new FoldingRange(startLC[0], endLC[0]);
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
        case TinyExpressionP4AST.StringExpr ignored -> "str";
        case TinyExpressionP4AST.BooleanExpr ignored -> "bool";
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

    /** カーソル位置の単語を返す（変数名 $x の場合は x のみ）。 */
    private String wordAt(String content, Position position, int lineOffset) {
      int line = position.getLine() - lineOffset;
      int col  = position.getCharacter();
      if (line < 0) return "";
      int offset = 0;
      String[] lines = content.split("\n", -1);
      for (int i = 0; i < line && i < lines.length; i++) offset += lines[i].length() + 1;
      offset += col;
      if (offset >= content.length()) return "";
      // skip '$' prefix if present
      int start = offset;
      if (start < content.length() && content.charAt(start) == '$') start++;
      int s = start, e = start;
      while (s > 0 && Character.isLetterOrDigit(content.charAt(s - 1))) s--;
      while (e < content.length() && Character.isLetterOrDigit(content.charAt(e))) e++;
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
      List<Integer> data = server.computeSemanticTokens(state.content(), state.lineOffset());
      return CompletableFuture.completedFuture(new SemanticTokens(data));
    }
  }

}
