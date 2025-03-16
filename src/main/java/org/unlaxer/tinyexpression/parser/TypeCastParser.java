package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class TypeCastParser extends JavaStyleDelimitedLazyChain{

  public final ExpressionType expressionType;
  
  public TypeCastParser(ExpressionType expressionType) {
    super();
    this.expressionType = expressionType;
  }

  @Override
  public Parsers getLazyParsers() {
    Parsers parsers = new Parsers();
    parsers.add(LeftParenthesisParser.class);
    parsers.add(RightParenthesisParser.class);
    return parsers;
  }
  
  static Parser createParser(ExpressionType expressionType) {
    if(expressionType.isExternalJavaType()) {
      return new WordParser(expressionType.javaTypeAsString());
    }
    if(expressionType.lowerCaseTypeName().isPresent()) {
      return new Choice(
          new WordParser(expressionType.javaTypeAsString()),
          new WordParser(expressionType.lowerCaseTypeName().get())
      );
    }else {
      return new WordParser(expressionType.javaTypeAsString());
    }
  }
  
}