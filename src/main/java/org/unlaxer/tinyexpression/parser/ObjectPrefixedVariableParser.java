package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ObjectPrefixedVariableParser extends JavaStyleDelimitedLazyChain
    implements VariableParser, ExpressionInterface {

  private static final long serialVersionUID = -602438216103654414L;

  public ObjectPrefixedVariableParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(ObjectTypeHintPrefixParser.class),
        Parser.get(NakedVariableParser.class)
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
