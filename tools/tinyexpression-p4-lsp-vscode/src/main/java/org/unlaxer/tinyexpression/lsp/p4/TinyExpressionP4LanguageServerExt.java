package org.unlaxer.tinyexpression.lsp.p4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
import org.eclipse.lsp4j.Range;
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
      // FormulaInfo file: parse only the formula section content.
      return parseWithFormulaSection(uri, content, fs);
    }

    // Plain expression file: delegate to parent, then enrich.
    ParseResult result = super.parseDocument(uri, content);

    TinyExpressionP4AST ast = null;
    if (result.succeeded() && result.consumedLength() == result.totalLength()) {
      try { ast = TinyExpressionP4Mapper.parse(content); } catch (Exception ignored) {}
    }

    ParseFailureDiagnostics failures = buildFailureDiagnostics(result, content);
    extDocuments.put(uri, new ExtDocumentState(content, result, ast, failures, 0));

    if (extClient != null) {
      publishEnrichedDiagnostics(uri, content, failures, 0);
    }
    return result;
  }

  private ParseResult parseWithFormulaSection(String uri, String fullContent, FormulaSection fs) {
    String formulaContent = fs.content();
    int lineOffset = fs.lineOffset();

    // Parse only the formula portion.
    StringSource source = createRootSource(formulaContent);
    ParseResult result;
    if (source == null) {
      result = new ParseResult(false, 0, formulaContent.length());
    } else {
      Parser rootParser = TinyExpressionP4Parsers.getRootParser();
      ParseContext ctx = new ParseContext(source);
      Parsed parsed;
      try {
        parsed = rootParser.parse(ctx);
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

    // Suppress parent's whole-file diagnostics by pre-publishing empty list.
    if (extClient != null) {
      extClient.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }

    TinyExpressionP4AST ast = null;
    if (result.succeeded() && result.consumedLength() == result.totalLength()) {
      try { ast = TinyExpressionP4Mapper.parse(formulaContent); } catch (Exception ignored) {}
    }

    ParseFailureDiagnostics failures = buildFailureDiagnostics(result, formulaContent);
    extDocuments.put(uri, new ExtDocumentState(formulaContent, result, ast, failures, lineOffset));

    if (extClient != null) {
      publishEnrichedDiagnostics(uri, formulaContent, failures, lineOffset);
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

  private ParseFailureDiagnostics buildFailureDiagnostics(ParseResult result, String content) {
    if (result.succeeded() && result.consumedLength() == result.totalLength()) {
      return ParseFailureDiagnostics.absent();
    }
    int offset = result.consumedLength();
    // Build a simple expected-hint from the character at the failure position.
    List<String> hints = List.of();
    if (offset < content.length()) {
      String remaining = content.substring(offset, Math.min(offset + 30, content.length())).strip();
      if (!remaining.isEmpty()) {
        hints = List.of("near: '" + remaining + "'");
      }
    }
    return ParseFailureDiagnostics.present(offset, hints);
  }

  private void publishEnrichedDiagnostics(String uri, String content,
      ParseFailureDiagnostics failures, int lineOffset) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    if (failures.hasFailure()) {
      int offset = failures.failureOffset();
      String snippet = content
          .substring(offset, Math.min(offset + 20, content.length()))
          .strip();

      Position rawStart = offsetToPosition(content, offset);
      Position rawEnd   = offsetToPosition(content, content.length());
      // Shift positions by lineOffset so they point into the original document.
      Position start = new Position(rawStart.getLine() + lineOffset, rawStart.getCharacter());
      Position end   = new Position(rawEnd.getLine()   + lineOffset, rawEnd.getCharacter());

      Diagnostic d = new Diagnostic();
      d.setRange(new Range(start, end));
      d.setSeverity(DiagnosticSeverity.Error);
      d.setSource("tinyexpression-p4");
      d.setCode(Either.forLeft("TE001"));
      d.setMessage(snippet.isEmpty()
          ? "Unexpected end of input"
          : "Unexpected token: '" + snippet + "'");
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

    // ── completion ──

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
        CompletionParams params) {

      List<CompletionItem> items = new ArrayList<>();

      // Keywords
      for (String kw : COMPLETION_KEYWORDS) {
        CompletionItem item = new CompletionItem(kw);
        item.setKind(CompletionItemKind.Keyword);
        items.add(item);
      }

      // Variable references extracted from the current document text
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state != null) {
        Matcher m = VARIABLE_PATTERN.matcher(state.content());
        Set<String> seen = new LinkedHashSet<>();
        while (m.find()) {
          seen.add(m.group(1));
        }
        for (String var : seen) {
          CompletionItem item = new CompletionItem("$" + var);
          item.setKind(CompletionItemKind.Variable);
          items.add(item);
        }
      }

      return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    // ── hover ──

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
      String uri = params.getTextDocument().getUri();
      ExtDocumentState state = server.extDocuments.get(uri);
      if (state == null) {
        return CompletableFuture.completedFuture(null);
      }

      String markdownText = switch (state.failures()) {
        case ParseFailureDiagnostics.Absent a -> {
          String astInfo = state.ast() != null
              ? "\n\nAST root: `" + state.ast().getClass().getSimpleName() + "`"
              : "";
          yield "**TinyExpression P4** (valid)" + astInfo;
        }
        case ParseFailureDiagnostics.Present p -> {
          String hints = p.expectedHints().isEmpty()
              ? ""
              : "\n\n" + String.join(", ", p.expectedHints());
          yield "**TinyExpression P4** — parse error at offset "
              + p.failureOffset() + hints;
        }
      };

      MarkupContent content = new MarkupContent();
      content.setKind("markdown");
      content.setValue(markdownText);
      return CompletableFuture.completedFuture(new Hover(content));
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
