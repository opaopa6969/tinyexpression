package org.unlaxer.tinyexpression.lsp.p4;


/**
 * Strategy for extracting the parseable TinyExpression formula from a document
 * that may contain non-formula content (metadata headers, Markdown fences, etc.).
 *
 * <p>Return {@code null} to indicate the whole document should be parsed as-is
 * (no stripping needed).
 *
 * <h2>Example — Markdown with fenced TinyExpression blocks</h2>
 * <pre>{@code
 * // Ideally: a full Markdown parser + DSL parser, but as a quick wrapper:
 * DocumentFilter.fenced("```tinyexp", "```")
 * }</pre>
 *
 * <h2>Example — FormulaInfo metadata format</h2>
 * <pre>{@code
 * // tags:NORMAL
 * // description:base score
 * // formula:
 * // $x + $y
 * // ---END_OF_PART---
 * DocumentFilter.formulaInfo()
 * }</pre>
 */
@FunctionalInterface
public interface DocumentFilter {

  /**
   * Extracts the formula section from {@code fullContent}.
   *
   * @return a {@link FormulaSection} with the formula text and its line-number
   *         offset within the original document, or {@code null} to parse the
   *         whole document unchanged.
   */
  FormulaSection extract(String fullContent);

  // ── Built-in implementations ──────────────────────────────────────────────

  /**
   * No-op filter: parse the whole document as-is.
   * Use this for plain {@code .tinyexp} files.
   */
  static DocumentFilter passThrough() {
    return _content -> null;
  }

  /**
   * FormulaInfo format filter.
   *
   * <p>Detects a {@code formula:} line and extracts the content that follows it
   * up to (but not including) a {@code ---END_OF_PART---} line.
   * Returns {@code null} when no {@code formula:} marker is found, so the whole
   * document is passed to the parser (safe fallback for plain expression files).
   */
  static DocumentFilter formulaInfo() {
    return TinyExpressionP4LanguageServerExt::extractFormulaSection;
  }

  /**
   * Fenced-block filter.
   *
   * <p>Finds the first block that starts with a line whose content begins with
   * {@code openFence} and ends at the first line beginning with
   * {@code closeFence}.  Everything outside the block is ignored.
   *
   * <p>Examples:
   * <ul>
   *   <li>Markdown code block: {@code fenced("```tinyexp", "```")}
   *   <li>AsciiDoc listing:    {@code fenced("----", "----")}
   *   <li>Custom delimiter:    {@code fenced("%%formula%%", "%%end%%")}
   * </ul>
   *
   * @param openFence  prefix of the opening fence line (e.g. {@code "```tinyexp"})
   * @param closeFence prefix of the closing fence line (e.g. {@code "```"})
   */
  static DocumentFilter fenced(String openFence, String closeFence) {
    return fullContent -> {
      String[] lines = fullContent.split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i].replace("\r", "").stripTrailing();
        if (line.startsWith(openFence)) {
          int formulaLineOffset = i + 1;
          StringBuilder sb = new StringBuilder();
          for (int j = formulaLineOffset; j < lines.length; j++) {
            String fline = lines[j].replace("\r", "").stripTrailing();
            if (fline.startsWith(closeFence)) break;
            sb.append(lines[j].replace("\r", "")).append('\n');
          }
          return new FormulaSection(sb.toString(), formulaLineOffset);
        }
      }
      return null;
    };
  }

  /**
   * Legacy TinyExpression format filter.
   *
   * <p>Handles documents that embed fenced code blocks (e.g.
   * {@code ```java:ClassName ... ```}) containing non-TinyExpression content
   * such as Java class definitions.  The fenced blocks are replaced line-by-line
   * with spaces so they are invisible to the P4 parser, while line positions
   * are preserved so diagnostic ranges remain accurate.
   *
   * <p>{@code import} declarations and {@code external returning as} invocations
   * are <em>not</em> masked — they are now part of the P4 grammar and are
   * validated directly.
   *
   * <p>The result is returned as {@link FormulaSection}{@code (maskedContent, 0)}
   * — line offset 0 because line positions inside the masked content correspond
   * 1:1 to positions in the original document.
   */
  static DocumentFilter legacy() {
    return fullContent -> {
      String[] lines = fullContent.split("\n", -1);
      StringBuilder sb = new StringBuilder();
      boolean inFenced = false;

      for (int i = 0; i < lines.length; i++) {
        String line     = lines[i];
        String stripped = line.replace("\r", "").stripTrailing();

        String out;
        if (!inFenced && stripped.length() > 3 && stripped.startsWith("```")) {
          // Opening fence line: ```java:Foo, ```python, etc.
          out = " ".repeat(stripped.length());
          inFenced = true;
        } else if (inFenced) {
          if (stripped.equals("```")) {
            // Closing fence line
            out = "   "; // same length as "```"
            inFenced = false;
          } else {
            // Content inside fenced block — blank out (cannot be validated as P4)
            out = " ".repeat(stripped.length());
          }
        } else {
          // Regular line — pass through unchanged; P4 grammar handles it
          out = line;
        }

        sb.append(out);
        if (i < lines.length - 1) sb.append('\n');
      }
      return new FormulaSection(sb.toString(), 0);
    };
  }

  /**
   * Auto-detect filter (default).
   *
   * <p>Tries {@link #formulaInfo()} first (for files with a {@code formula:}
   * marker).  If no marker is found, applies {@link #legacy()} masking which
   * handles fenced code blocks, import declarations, and
   * {@code external returning as} invocations.
   * This single filter covers FormulaInfo-style files, legacy pre-P4 files,
   * and plain P4 expression files.
   */
  static DocumentFilter autoDetect() {
    return firstMatch(formulaInfo(), legacy());
  }

  /**
   * Composite filter: tries each filter in order, returns the first non-null result.
   *
   * <p>Useful when multiple host formats need to be supported in one server
   * instance, e.g.:
   * <pre>{@code
   * DocumentFilter.firstMatch(
   *     DocumentFilter.formulaInfo(),
   *     DocumentFilter.fenced("```tinyexp", "```")
   * )
   * }</pre>
   */
  static DocumentFilter firstMatch(DocumentFilter... filters) {
    return fullContent -> {
      for (DocumentFilter f : filters) {
        FormulaSection result = f.extract(fullContent);
        if (result != null) return result;
      }
      return null;
    };
  }
}
