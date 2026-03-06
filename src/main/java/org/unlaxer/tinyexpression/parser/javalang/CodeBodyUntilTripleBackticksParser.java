package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.elementary.WildCardStringTerninatorParser;

/**
 * Consume fenced code body until the next triple backticks.
 */
public class CodeBodyUntilTripleBackticksParser extends WildCardStringTerninatorParser {
  public CodeBodyUntilTripleBackticksParser() {
    super("```");
  }
}

