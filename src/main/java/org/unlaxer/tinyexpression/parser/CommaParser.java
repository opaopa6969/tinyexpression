package org.unlaxer.tinyexpression.parser;

import java.util.Optional;
import org.unlaxer.parser.elementary.SingleCharacterParser;

public class CommaParser extends SingleCharacterParser {
  @Override
  public boolean isMatch(char target) {
    return target == ',';
  }

  public Optional<String> expectedDisplayText() {
    return Optional.of("','");
  }
}
