package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.StartAndEndQuotedParser;

public class CodeParser extends StartAndEndQuotedParser{

  public CodeParser() {
    super(
        Parser.get(CodeStartParser.class), //
        new QuotedContentsParser(Parser.get(CodeEndParser.class)) , //
        Parser.get(CodeEndParser.class)
    );
  }
}
