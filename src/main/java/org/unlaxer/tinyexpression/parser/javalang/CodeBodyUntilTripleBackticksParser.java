package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.elementary.WildCardStringTerminatorParser;

/**
 * Consume fenced code body until the next triple backticks.
 */
public class CodeBodyUntilTripleBackticksParser extends WildCardStringTerminatorParser {
  public CodeBodyUntilTripleBackticksParser() {
    super("```");
  }
}
