package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class StringContainsParser extends JavaStyleDelimitedLazyChain 
  implements BooleanExpression , StringMultipleParameterPredicator {

  private static final long serialVersionUID = 6896630990248605254L;

  public StringContainsParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return
    // StringContains:=StringExpression'.contains('StringExpression')';
    new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(ContainsMethodParser.class));
  }

  @Override
  public Class<? extends Parser> parameterParserClass() {
    return ContainsMethodParser.class;
  }

  @Override
  public String predicateMethodString() {
    return "org.unlaxer.util.MultipleParamterStringPredicators.contains(";
  }
}