package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class ObjectVariableMethodParameterParser extends LazyChoice
    implements TypedVariableParser, ExpressionInterface {

  private static final long serialVersionUID = -602438216103654417L;

  public ObjectVariableMethodParameterParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(ObjectPrefixedVariableParser.class),
        Parser.get(ObjectSuffixedVariableParser.class)
    );
  }

  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionTypes.object);
  }

  @Override
  public ExpressionTypes expressionType() {
    return ExpressionTypes.object;
  }
}
