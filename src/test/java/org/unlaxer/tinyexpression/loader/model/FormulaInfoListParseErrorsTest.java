package org.unlaxer.tinyexpression.loader.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeBlockPolicy;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.FormulaInfoParseException;
import org.unlaxer.util.Try;

/**
 * Regression tests for issue #195: three {@code FormulaInfoList.parse} bugs found by the
 * UBNF-generated parity check (#180). Each fixture is the same document the Rust parity test
 * ({@code rust/tinyexpression-rs/tests/formula_info.rs}) exercises against
 * {@code src/test/resources/formulaInfo-ubnf/*.fi}, so a fix here is meant to line up with the
 * Rust loader's judgement (see {@code formula_info::LoadError}).
 */
public class FormulaInfoListParseErrorsTest {

  // Fixtures may contain Java code-block formulas; opt in to the process-wide policy so the
  // test is order-/parallelism-independent (tinyexpression#27).
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

  /**
   * Bug 1: a document with an unparsable line after a complete block used to load only the
   * blocks before it (here: one formula) without any error, silently dropping "garbage".
   */
  @Test
  public void documentNotFullyParsedIsRejectedInsteadOfSilentlyTruncated() throws IOException {
    String text = fixture("reject-syntax-07-garbage-after-block.fi");

    Try<FormulaInfoList> parsed = parse(text);

    assertFalse("should not load a partially-parsed document", parsed.isPresent());
    assertTrue(parsed.throwable.isPresent());
    Throwable failure = parsed.throwable.get();
    assertEquals(FormulaInfoParseException.class, failure.getClass());
    assertTrue(failure.getMessage(), failure.getMessage().contains("partially parsed"));
  }

  /**
   * Bug 2: a trailing {@code key:} with nothing after it (the value token is zero length at the
   * end of input) used to fail with a bare {@code NoSuchElementException}.
   */
  @Test
  public void emptyValueAtEndOfInputIsRejectedExplicitly() throws IOException {
    String text = fixture("reject-load-09-empty-value-at-eof.fi");

    Try<FormulaInfoList> parsed = parse(text);

    assertFalse(parsed.isPresent());
    assertTrue(parsed.throwable.isPresent());
    Throwable failure = parsed.throwable.get();
    assertEquals(FormulaInfoParseException.class, failure.getClass());
    assertEquals("'formula:' has an empty value at the end of input", failure.getMessage());
  }

  /**
   * Bug 3: {@code dependsOn} naming a calculator the document does not define used to fail with
   * a bare {@code NullPointerException} while wiring the dependency back onto the unresolved
   * calculator.
   */
  @Test
  public void unknownDependsOnIsRejectedExplicitly() throws IOException {
    String text = fixture("reject-load-07-unknown-depends-on.fi");

    Try<FormulaInfoList> parsed = parse(text);

    assertFalse(parsed.isPresent());
    assertTrue(parsed.throwable.isPresent());
    Throwable failure = parsed.throwable.get();
    assertEquals(FormulaInfoParseException.class, failure.getClass());
    assertEquals("x dependsOn unknown calculator 'nowhere'", failure.getMessage());
  }
}
