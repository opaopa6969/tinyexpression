package org.unlaxer.tinyexpression.parser.string;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TypedVariableParser;

public class StringVariableMethodParameterParser extends LazyChoice implements TypedVariableParser , StringExpression{


  public StringVariableMethodParameterParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(//
        Parser.get(StringPrefixedVariableParser.class),//
        Parser.get(StringSuffixedVariableParser.class)
    );
  }
  
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.string);
  }
}