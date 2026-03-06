package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyZeroOrMore;
import org.unlaxer.parser.elementary.WildCardCharacterParser;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Consume fenced code body until the closing triple-backtick line.
 */
public class CodeBodyUntilEndFenceParser extends LazyZeroOrMore {

  @Override
  public Supplier<Parser> getLazyParser() {
    return () -> Parser.get(WildCardCharacterParser.class);
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.of(Parser.get(CodeEndParser.class));
  }
}

