package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ObjectSuffixedVariableParser extends JavaStyleDelimitedLazyChain
    implements VariableParser, ExpressionInterface {

  private static final long serialVersionUID = -602438216103654415L;

  public ObjectSuffixedVariableParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(NakedVariableParser.class),
        Parser.get(ObjectTypeHintSuffixParser.class)
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
