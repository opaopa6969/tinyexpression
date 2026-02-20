package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

@SuppressWarnings("serial")
public class StringVariableMethodParameterParser extends LazyChoice implements TypedVariableParser , StringExpression{


  public StringVariableMethodParameterParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(//
        Parser.get(StringPrefixedVariableParser.class),//
        Parser.get(StringSuffixedVariableParser.class)
    );
  }
  
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionTypes.string);
  }
}