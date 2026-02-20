package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class StringEndsWithParser extends JavaStyleDelimitedLazyChain
    implements BooleanExpression, StringMultipleParameterPredicator {

  private static final long serialVersionUID = 6896630990248605254L;

  List<Parser> parsers;

  public StringEndsWithParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return
    // StringEndsWith:=StringExpression'.endsWith('StringExpression')';
    new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(EndsWithMethodParser.class));

  }

  @Override
  public Class<? extends Parser> parameterParserClass() {
    return EndsWithMethodParser.class;
  }
  
  @Override
  public String predicateMethodString() {
    return "org.unlaxer.util.MultipleParamterStringPredicators.endsWith(";
  }
}