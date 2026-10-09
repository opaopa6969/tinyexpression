package org.unlaxer.tinyexpression.codeblock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4SourceText;

/** Source-preserving, non-executing projection of committed P4 code-block tokens.
 * Java code execution remains trusted-author-only, explicitly opt-in (ADR-003); no sandbox.
 * This API never invokes a compiler, loads classes, or writes generated files.
 */
public final class CodeBlockSource {
  private CodeBlockSource() {}
  /** Half-open Unicode code-point positions, not Java UTF-16 offsets. */
  public record Span(int start, int end) {}
  /** identifier is an external binding label, never a filename or unchecked source identifier. */
  public record Block(String scheme, String identifier, String body,
      Span span, Span bodySpan, Span nameSpan) {}
  public enum Target { JAVA, RUST }
  public record Diagnostic(String code, Span span) {
    public String message() {
      String message = switch (code) {
        case "CB001" -> "unknown code block scheme";
        case "CB002" -> "duplicate code block binding";
        case "CB003" -> "code block does not match the build target";
        case "CB004" -> "code block build requires explicit host permission";
        default -> "Rust code blocks require an explicit AOT build";
      };
      return code + " at code points " + span.start() + ".." + span.end()
          + ": " + message;
    }
  }

  /** Parses once; use fromAst to reuse a parse and its owned position snapshot. */
  public static List<Block> parse(String source) {
    return fromAst(P4PreferredAstMapper.parseDetailed(source, null));
  }

  public static List<Block> fromAst(P4PreferredAstMapper.ParsedAst parsed) {
    return fromAst(parsed.ast(), parsed.sourceText());
  }

  /** AST plus its existing, identity-based code-point snapshot; never reparses the source. */
  public static List<Block> fromAst(TinyExpressionP4AST ast, P4SourceText positions) {
    return nodes(ast).stream().map(node -> {
      int[] span = positions.spanOf(node).orElseThrow(() ->
          new IllegalArgumentException("CodeBlock metadata requires its owned source span"));
      return fromSource(retainedSource(node), new Span(span[0], span[1]));
    }).toList();
  }

  private static List<TinyExpressionP4AST.CodeBlockExpr> nodes(TinyExpressionP4AST ast) {
    if (ast instanceof TinyExpressionP4AST.FormulaExpr formula) return formula.codeBlocks();
    if (ast instanceof TinyExpressionP4AST.CodeBlockExpr block) return List.of(block);
    return List.of();
  }

  private static String retainedSource(TinyExpressionP4AST.CodeBlockExpr node) {
    Object value = node.source(); // Classic generators may declare Object or String.
    if (!(value instanceof String source))
      throw new IllegalArgumentException("CodeBlock source must be retained text");
    return source;
  }

  private static Block fromSource(String source, Span span) {
    int[] chars = source.codePoints().toArray();
    int headerEnd = 0;
    while (headerEnd < chars.length && chars[headerEnd] != '\r' && chars[headerEnd] != '\n') headerEnd++;
    int bodyStart = afterLine(chars, headerEnd);
    int width = 0;
    while (width < chars.length && chars[width] == '`') width++;
    int close = chars.length - width;
    var extended = width > 3 ? LongCodeFence.scan(source, 0) : null;
    if (width < 3 || !source.endsWith("`".repeat(width)) || close < bodyStart
        || (width > 3 && (extended == null || extended.end() != source.length()))
        || close <= 0 || (chars[close - 1] != '\r' && chars[close - 1] != '\n')
        || span.start() < 0 || span.end() - span.start() < chars.length)
      throw new IllegalArgumentException("invalid retained code block source/span");
    String header = slice(chars, width, headerEnd);
    int colon = header.indexOf(':');
    if (colon < 1 || colon == header.length() - 1)
      throw new IllegalArgumentException("invalid retained code block header");
    String scheme = header.substring(0, colon);
    String identifier = header.substring(colon + 1);
    int nameStart = span.start() + width + header.codePointCount(0, colon + 1);
    return new Block(scheme, identifier, slice(chars, bodyStart, close), span,
        new Span(span.start() + bodyStart, span.start() + close),
        new Span(nameStart, nameStart + identifier.codePointCount(0, identifier.length())));
  }

  private static String slice(int[] source, int start, int end) {
    return new String(source, start, end - start);
  }

  private static int afterLine(int[] source, int at) {
    if (at < source.length && source[at] == '\r') at++;
    if (at < source.length && source[at] == '\n') at++;
    return at;
  }

  /** Pure build preflight. Permission comes from the host, not the formula.
   * One diagnostic per block, source order: unknown, duplicate, target mismatch, denied.
   * Success is not Rust type checking or permission to run through the normal evaluator.
   */
  public static List<Diagnostic> preflight(List<Block> blocks, Target target, boolean allowCode) {
    var names = new HashSet<String>();
    List<Diagnostic> diagnostics = new ArrayList<>();
    for (var block : blocks) {
      Target language = switch (block.scheme().toLowerCase(Locale.ROOT)) {
        case "java" -> Target.JAVA;
        case "rust" -> Target.RUST;
        default -> null;
      };
      boolean duplicate = !names.add(block.identifier());
      String code = language == null ? "CB001" : duplicate ? "CB002"
          : language != target ? "CB003" : !allowCode ? "CB004" : null;
      if (code != null) diagnostics.add(new Diagnostic(code, block.nameSpan()));
    }
    return List.copyOf(diagnostics);
  }

  public static void rejectUncompiledRust(String source) {
    if (source == null || !source.contains("```")) return;
    for (var block : parse(source)) {
      if (block.scheme().equalsIgnoreCase("rust"))
        throw new UnsupportedOperationException(new Diagnostic("CB005", block.nameSpan()).message());
    }
  }

  /** Guard AST-only evaluation/emission before evaluating any declaration or expression.
   * A detached Java record has no absolute position snapshot, so do not invent coordinates.
   */
  public static void rejectUncompiledRust(TinyExpressionP4AST ast) {
    for (var node : nodes(ast)) {
      String source = retainedSource(node);
      var block = fromSource(source, new Span(0, source.codePointCount(0, source.length())));
      if (block.scheme().equalsIgnoreCase("rust"))
        throw new UnsupportedOperationException("CB005: Rust code blocks require an explicit AOT build (binding "
            + block.identifier() + ")");
    }
  }
}
