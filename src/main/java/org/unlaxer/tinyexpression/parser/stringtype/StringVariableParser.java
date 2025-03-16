package org.unlaxer.tinyexpression.parser.stringtype;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.RootVariableParser;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.util.cache.SupplierBoundCache;

public class StringVariableParser extends LazyChoice implements RootVariableParser , StringExpression{

  private static final long serialVersionUID = -604853821610350410L;
  
  static final SupplierBoundCache<StringVariableParser> SINGLETON = new SupplierBoundCache<>(StringVariableParser::new);

  public StringVariableParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(//
        Parser.get(StringVariableMatchedWithVariableDeclarationParser.class),
        Parser.get(StringPrefixedVariableParser.class),//
        Parser.get(StringSuffixedVariableParser.class)//
    );
  }
 
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionTypes.string);
  }
    
  public static StringVariableParser get() {
    return SINGLETON.get();
  }

  @Override
  public Class<? extends RootVariableParser> rootOfTypedVariableParser() {
    return StringVariableParser.class;
  }

  @Override
  public Class<? extends VariableParser> oneOfTypedVariableParser() {
    return StringPrefixedVariableParser.class;
  }

  @Override
  public Class<? extends TypeHintVariableParser> typeHintVariableParser() {
    return StringTypeHintPrefixParser.class;
  }
}