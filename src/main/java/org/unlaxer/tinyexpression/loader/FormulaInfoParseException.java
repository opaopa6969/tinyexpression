package org.unlaxer.tinyexpression.loader;

/**
 * Thrown by the hand-written FormulaInfo loader ({@code FormulaInfoList.parse} and the parsers
 * it drives) when a document cannot be loaded, instead of letting an incidental JDK exception
 * ({@code NoSuchElementException}, {@code NullPointerException}, ...) escape from wherever the
 * problem happens to surface.
 *
 * <p>Three cases (issue #195, found by the UBNF-generated parity check of #180):
 * <ul>
 *   <li>the document is not fully consumed by {@code FormulaInfoBlocksParser} (previously:
 *       silently kept only the blocks parsed before the unparsable line, or none, without an
 *       error);</li>
 *   <li>an entry's value is empty at the end of input, e.g. a trailing {@code key:} with
 *       nothing after it (previously: {@code NoSuchElementException} from the empty value
 *       token);</li>
 *   <li>{@code dependsOn} names a calculator the document does not define (previously:
 *       {@code NullPointerException} while wiring the dependency).</li>
 * </ul>
 *
 * <p>Issue #211 added two more:
 * <ul>
 *   <li>a line that starts with {@code ---END_OF_PART---} and continues with anything but
 *       spaces and tabs (previously: an ordinary value line, so the next block was silently
 *       merged into this one and its formula lost);</li>
 *   <li>a block with {@code calculatorName} twice or more, the sign that two blocks were merged
 *       because the end mark line between them is missing (previously: the later values
 *       silently overwrote the earlier ones).</li>
 * </ul>
 *
 * <p>It is an {@link IllegalArgumentException} so that {@code FormulaInfoSourceDocument.parse},
 * whose documented failure is {@code IllegalArgumentException}, can raise the same exception.
 *
 * <p>This matches the Rust loader ({@code tinyexpression-rs}'s {@code formula_info::LoadError}),
 * which already rejected these documents explicitly; the parity test
 * ({@code rust/tinyexpression-rs/tests/formula_info.rs}) compares this class's simple name
 * against {@code LoadError::java_exception()}.
 */
public class FormulaInfoParseException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  public FormulaInfoParseException(String message) {
    super(message);
  }
}
