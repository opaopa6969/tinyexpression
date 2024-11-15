package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.SingleCharacterParser;

public class CommaParser extends SingleCharacterParser {
  @Override
  public boolean isMatch(char target) {
    return target == ',';
  }
}