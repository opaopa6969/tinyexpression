package org.unlaxer.tinyexpression.loader.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeBlockPolicy;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.FormulaInfoParseException;
import org.unlaxer.util.Try;

/**
 * Issue #211: variations of the {@code ---END_OF_PART---} line used to merge two blocks
 * silently (the earlier formula disappeared). The fixtures are the same
 * {@code src/test/resources/formulaInfo-ubnf/*.fi} files the Rust parity test
 * ({@code rust/tinyexpression-rs/tests/formula_info.rs}) runs, so the grammar
 * ({@code grammar/formula-info.ubnf}), the Rust loader and both Java entry points
 * ({@link FormulaInfoList#parse} and {@link FormulaInfoSourceDocument#parse}) agree.
 */
public class FormulaInfoListEndMarkTest {

  @Before public void enableJavaCodeBlocks() { JavaCodeBlockPolicy.setEnabled(true); }
  @After public void resetJavaCodeBlocks() { JavaCodeBlockPolicy.reset(); }

  private static final FormulaInfoAdditionalFields ADDITIONAL_FIELDS =
      new FormulaInfoAdditionalFields("siteId",
          formulaInfo -> {
            String checkKind = formulaInfo.extraValueByKey.get("checkKind");
            return checkKind != null ? checkKind : formulaInfo.calculatorName;
          });

  private static String fixture(String name) throws IOException {
    Path path = Paths.get(".", "src", "test", "resources", "formulaInfo-ubnf", name);
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  private static Try<FormulaInfoList> parse(String text) {
    return FormulaInfoList.parse(
        text, ADDITIONAL_FIELDS, Thread.currentThread().getContextClassLoader());
  }

  private static List<String> loadedNames(String name) throws IOException {
    Try<FormulaInfoList> parsed = parse(fixture(name));
    parsed.throwable.ifPresent(failure -> {
      throw new AssertionError(name + " should load", failure);
    });
    return parsed.get().get().stream()
        .map(info -> info.calculatorName)
        .collect(Collectors.toList());
  }

  private static List<String> sectionNames(String name) throws IOException {
    return FormulaInfoSourceDocument.parse(fixture(name)).sections().stream()
        .map(FormulaInfoSourceDocument.Section::calculatorName)
        .collect(Collectors.toList());
  }

  private static FormulaInfoParseException loadFailure(String name) throws IOException {
    Try<FormulaInfoList> parsed = parse(fixture(name));
    assertFalse(name + " should not load", parsed.isPresent());
    assertTrue(parsed.throwable.isPresent());
    Throwable failure = parsed.throwable.get();
    assertEquals(name + ": " + failure, FormulaInfoParseException.class, failure.getClass());
    return (FormulaInfoParseException) failure;
  }

  private static FormulaInfoParseException sourceDocumentFailure(String name) {
    return assertThrows(FormulaInfoParseException.class,
        () -> FormulaInfoSourceDocument.parse(fixture(name)));
  }

  /** Spaces/tabs after the mark (LF, CRLF, at the end of input) close the block. */
  @Test
  public void endMarkWithTrailingBlanksClosesTheBlock() throws IOException {
    for (String name : List.of(
        "accept-26-end-mark-trailing-blanks.fi",
        "accept-27-end-mark-trailing-blanks-crlf.fi")) {
      assertEquals(name, List.of("a", "b"), loadedNames(name));
      assertEquals(name, List.of("a", "b"), sectionNames(name));
    }
  }

  /** Other characters after the mark are an error, not a value line. */
  @Test
  public void endMarkWithTrailingCharactersIsRejected() throws IOException {
    assertEquals(
        "line 4: '---END_OF_PART---' must be followed only by spaces or tabs up to the end of"
            + " the line, but found 'xyz'",
        loadFailure("reject-syntax-12-end-mark-trailing-chars.fi").getMessage());
    assertTrue(loadFailure("reject-syntax-13-end-mark-blank-then-chars.fi").getMessage()
        .startsWith("line 4: "));
    // Used to be a line of the description value (the old accept-08 fixture).
    assertTrue(loadFailure("reject-syntax-14-end-mark-trailing-chars-in-value.fi").getMessage()
        .startsWith("line 3: "));
    // Only ' ' and '\t' are allowed, not VT/FF.
    assertTrue(loadFailure("reject-syntax-15-end-mark-trailing-vt.fi").getMessage()
        .startsWith("line 4: "));

    for (String name : List.of(
        "reject-syntax-12-end-mark-trailing-chars.fi",
        "reject-syntax-13-end-mark-blank-then-chars.fi",
        "reject-syntax-14-end-mark-trailing-chars-in-value.fi",
        "reject-syntax-15-end-mark-trailing-vt.fi")) {
      assertTrue(name, sourceDocumentFailure(name).getMessage().startsWith("line "));
    }
  }

  /** Two calculatorName in one block: the end mark between two FormulaInfo is missing. */
  @Test
  public void duplicateCalculatorNameIsRejected() throws IOException {
    assertEquals(
        "calculatorName appears 2 times in one block ('a', 'b'); is the ---END_OF_PART---"
            + " line between two FormulaInfo missing?",
        loadFailure("reject-load-10-missing-end-mark-merges-blocks.fi").getMessage());
    assertEquals(
        "calculatorName appears 2 times in one block ('a', 'a'); is the ---END_OF_PART---"
            + " line between two FormulaInfo missing?",
        loadFailure("reject-load-11-duplicate-calculator-name.fi").getMessage());
    for (String name : List.of(
        "reject-load-10-missing-end-mark-merges-blocks.fi",
        "reject-load-11-duplicate-calculator-name.fi")) {
      assertTrue(name,
          sourceDocumentFailure(name).getMessage().startsWith("calculatorName appears 2 times"));
    }
  }

  /** Lines that only resemble the end mark stay value lines, as before. */
  @Test
  public void endMarkLookalikesStayValueLines() throws IOException {
    Try<FormulaInfoList> parsed = parse(fixture("accept-08-end-mark-lookalikes.fi"));
    assertTrue(parsed.isPresent());
    List<FormulaInfo> infos = parsed.get().get();
    assertEquals(1, infos.size());
    assertEquals("----END_OF_PART---\n --END_OF_PART---\n-- -END_OF_PART---",
        infos.get(0).description);
  }
}
