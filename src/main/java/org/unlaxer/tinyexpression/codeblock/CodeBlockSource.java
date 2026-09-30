package org.unlaxer.tinyexpression.codeblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.unlaxer.tinyexpression.p4.ubnfc.P4Scanners;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4Parser;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseOptions;

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

  /** Validates the complete formula; only successful parser occurrences become blocks. */
  public static List<Block> parse(String source) {
    var options = new ParseOptions(false, true, true, true, P4Scanners.ALL);
    var result = TinyExpressionP4Parser.parse(source, options);
    if (!result.ok()) {
      // Match the public facade's result-family retries. These roots contain no
      // CodeBlock rule, but can contain fence text inside comments/strings.
      for (String entry : List.of("BooleanExpression", "StringExpression", "ObjectExpression")) {
        var alternate = TinyExpressionP4Parser.parseEntry("TinyExpressionP4", entry, source, options);
        if (alternate.ok()) { result = alternate; break; }
      }
    }
    if (!result.ok()) throw new IllegalArgumentException("code block formula rejected: " + result.diagnostics());
    int[] chars = source.codePoints().toArray();
    List<Block> blocks = new ArrayList<>();
    for (var rule : result.lexical()) {
      if (!rule.ruleId().equals("TinyExpressionP4::CodeBlock") || !rule.exprId().endsWith("body/seq")) continue;
      var tokens = result.lexical().stream()
          .filter(t -> t.parentOccurrenceId() == rule.occurrenceId() && t.token() != null)
          .sorted(Comparator.comparingLong(t -> t.completionOrder())).toList();
      if (tokens.size() != 3) throw new IllegalStateException("code block token contract changed");
      var open = tokens.get(0).span();
      var close = tokens.get(2).span();
      int headerEnd = open.start();
      while (headerEnd < chars.length && chars[headerEnd] != '\r' && chars[headerEnd] != '\n') headerEnd++;
      int bodyStart = afterLine(chars, headerEnd);
      int blockEnd = afterLine(chars, close.start() + 3);
      String header = slice(chars, open.start(), headerEnd);
      int colon = header.indexOf(':');
      if (!header.startsWith("```") || colon < 3) throw new IllegalStateException("invalid code block header projection");
      String scheme = header.substring(3, colon);
      String identifier = header.substring(colon + 1);
      int nameStart = open.start() + header.codePointCount(0, colon + 1);
      blocks.add(new Block(scheme, identifier, slice(chars, bodyStart, close.start()),
          new Span(open.start(), blockEnd), new Span(bodyStart, close.start()),
          new Span(nameStart, nameStart + identifier.codePointCount(0, identifier.length()))));
    }
    blocks.sort(Comparator.comparingInt(b -> b.span().start()));
    return List.copyOf(blocks);
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
}
