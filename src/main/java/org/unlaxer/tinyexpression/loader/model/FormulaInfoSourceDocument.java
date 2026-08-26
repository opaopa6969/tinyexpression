package org.unlaxer.tinyexpression.loader.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.loader.FormulaInfoBlockParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoBlocksParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoElementOrCommentParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoElementParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoElementParser.KeyValue;
import org.unlaxer.tinyexpression.loader.FormulaInfoParser.Kind;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

/**
 * Parser-backed source view of a multi-part FormulaInfo document.
 *
 * <p>This model deliberately does not construct calculators or compile embedded Java. It is
 * suitable for editor and debugger source selection, where metadata and exact source locations
 * are needed before execution policy is applied.</p>
 */
public final class FormulaInfoSourceDocument {

  /** One FormulaInfo block and the source slice belonging to its {@code formula:} value. */
  public record Section(
      String calculatorName,
      String executionBackend,
      String formulaText,
      String debugSource,
      int sourceOffset,
      int lineOffset) {

    public String runtimeMode() {
      return ExecutionBackend.parse(executionBackend)
          .orElseThrow(() -> new IllegalArgumentException(
              "Unknown FormulaInfo executionBackend: " + executionBackend))
          .runtimeModeMarker();
    }
  }

  private final List<Section> sections;

  private FormulaInfoSourceDocument(List<Section> sections) {
    this.sections = List.copyOf(sections);
  }

  public List<Section> sections() {
    return sections;
  }

  public Optional<Section> section(String calculatorName) {
    if (calculatorName == null || calculatorName.isBlank()) {
      return sections.stream().findFirst();
    }
    return sections.stream()
        .filter(section -> calculatorName.equals(section.calculatorName()))
        .findFirst();
  }

  /** Parse the entire document exactly, without compiling or instantiating calculators. */
  public static FormulaInfoSourceDocument parse(String text) {
    String sourceText = text == null ? "" : text;
    FormulaInfoBlocksParser parser = new FormulaInfoBlocksParser();
    ParseContext context = new ParseContext(StringSource.createRootSource(sourceText));
    Parsed parsed;
    try {
      parsed = parser.parse(context);
    } finally {
      context.close();
    }
    if (!parsed.isSucceeded() || parsed.getConsumed() == null) {
      throw new IllegalArgumentException("Invalid FormulaInfo document");
    }
    int consumed = parsed.getConsumed().source.sourceAsString().length();
    if (consumed != sourceText.length()) {
      throw new IllegalArgumentException(
          "FormulaInfo document was only partially parsed at offset " + consumed);
    }

    TypedToken<FormulaInfoBlocksParser> root =
        parsed.getRootToken().typed(FormulaInfoBlocksParser.class);
    List<Section> sections = new ArrayList<>();
    for (TypedToken<FormulaInfoBlockParser> block
        : root.getChildrenWithParserAsListTyped(FormulaInfoBlockParser.class)) {
      Map<String, String> values = new LinkedHashMap<>();
      Token formulaValue = null;
      String normalizedFormula = null;

      for (Token token : FormulaInfoElementOrCommentParser.elements(block)) {
        if (!(token.parser instanceof FormulaInfoElementParser)) {
          continue;
        }
        TypedToken<FormulaInfoElementParser> element =
            token.typed(FormulaInfoElementParser.class);
        KeyValue keyValue = element.getParser().extract(element);
        values.put(keyValue.getKey(), keyValue.getValue());
        if ("formula".equals(keyValue.getKey())) {
          formulaValue = element.getChild(TokenPredicators.hasTag(Kind.value.tag()));
          normalizedFormula = keyValue.getValue();
        }
      }

      if (formulaValue == null || normalizedFormula == null) {
        continue;
      }
      String rawFormula = formulaValue.getToken().orElse("");
      int sourceOffset = formulaValue.source.offsetFromRoot().value();
      int leadingLineBreakLength = leadingLineBreakLength(rawFormula);
      rawFormula = rawFormula.substring(leadingLineBreakLength);
      sourceOffset += leadingLineBreakLength;
      String backend = values.getOrDefault(
          "executionBackend", values.getOrDefault("backend", ExecutionBackend.JAVA_CODE.name()));
      sections.add(new Section(
          values.getOrDefault("calculatorName", ""),
          backend,
          normalizedFormula,
          maskFormulaInfoComments(rawFormula),
          sourceOffset,
          lineOffset(sourceText, sourceOffset)));
    }
    if (sections.isEmpty()) {
      throw new IllegalArgumentException("FormulaInfo document contains no formula section");
    }
    return new FormulaInfoSourceDocument(sections);
  }

  private static int lineOffset(String source, int offset) {
    int lines = 0;
    for (int i = 0; i < Math.min(offset, source.length()); i++) {
      if (source.charAt(i) == '\n') {
        lines++;
      }
    }
    return lines;
  }

  private static int leadingLineBreakLength(String source) {
    if (source.startsWith("\r\n")) {
      return 2;
    }
    if (source.startsWith("\n") || source.startsWith("\r")) {
      return 1;
    }
    return 0;
  }

  /** Preserve line/column positions while removing FormulaInfo-only {@code #} comment lines. */
  private static String maskFormulaInfoComments(String source) {
    StringBuilder result = new StringBuilder(source.length());
    int lineStart = 0;
    while (lineStart < source.length()) {
      int newline = source.indexOf('\n', lineStart);
      int lineEnd = newline < 0 ? source.length() : newline;
      String line = source.substring(lineStart, lineEnd);
      if (line.stripLeading().startsWith("#")) {
        for (int i = 0; i < line.length(); i++) {
          result.append(line.charAt(i) == '\r' ? '\r' : ' ');
        }
      } else {
        result.append(line);
      }
      if (newline >= 0) {
        result.append('\n');
        lineStart = newline + 1;
      } else {
        lineStart = source.length();
      }
    }
    return result.toString();
  }
}
