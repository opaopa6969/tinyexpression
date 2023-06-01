package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanPrefixedVariableParser extends JavaStyleDelimitedLazyChain implements BooleanExpression {

  private static final long serialVersionUID = -600588538210309122L;

  public BooleanPrefixedVariableParser() {
    super();
  }
  
  @Override
  public List<Parser> getLazyParsers() {
    return 
    new Parsers(//
        Parser.get(BooleanTypeHintPrefixParser.class), //0
        Parser.get(NakedVariableParser.class)//1
    );
  }
  
  public static String getVariableName(Token thisParserParsed) {
    Token token = thisParserParsed.filteredChildren.get(1);
    return NakedVariableParser.getVariableName(token);
  }

}