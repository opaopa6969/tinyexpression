package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringVariableParser extends LazyChoice implements RootVariableParser , StringExpression{

  private static final long serialVersionUID = -604853821610350410L;

  public StringVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(//
        Parser.get(StringVariableMatchedWithVariableDeclarationParser.class),
        Parser.get(StringPrefixedVariableParser.class),//
        Parser.get(StringSuffixedVariableParser.class)//
    );
  }
 
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.string);
  }
  
  public static class StringVariableMatchedWithVariableDeclarationParser extends LazyChain implements StringExpression {

    public StringVariableMatchedWithVariableDeclarationParser() {
      super();
    }

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(//
          Parser.get(DollarParser.class), //0
          Parser.get(StringVariableDeclarationMatchedTokenParser.class)//1
      );
    }

    
    @TokenExtractor
    static Token getVariableNameToken(Token thisParserParsed) {
      Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
      return token;
    }
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