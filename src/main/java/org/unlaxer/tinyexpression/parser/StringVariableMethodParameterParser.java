package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class StringVariableMethodParameterParser extends LazyChoice implements TypedVariableParser , StringExpression{


  public StringVariableMethodParameterParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
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