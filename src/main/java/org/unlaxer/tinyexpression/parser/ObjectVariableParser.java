package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class ObjectVariableParser extends LazyChoice implements RootVariableParser, ExpressionInterface {

  private static final long serialVersionUID = -602438216103654416L;

  public ObjectVariableParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(NakedVariableParser.class),
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

  @Override
  public Class<? extends RootVariableParser> rootOfTypedVariableParser() {
    return ObjectVariableParser.class;
  }

  @Override
  public Class<? extends VariableParser> oneOfTypedVariableParser() {
    return ObjectPrefixedVariableParser.class;
  }

  @Override
  public Class<? extends TypeHintVariableParser> typeHintVariableParser() {
    return ObjectTypeHintPrefixParser.class;
  }
}
