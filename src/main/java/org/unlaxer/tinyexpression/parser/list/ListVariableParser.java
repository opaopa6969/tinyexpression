package org.unlaxer.tinyexpression.parser.list;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.DollarParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.RootVariableParser;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.string.StringExpression;
import org.unlaxer.tinyexpression.parser.string.StringVariableDeclarationMatchedTokenParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class ListVariableParser extends LazyChoice implements RootVariableParser , ListExpression{

  public ListVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(//
        Parser.get(ListVariableMatchedWithVariableDeclarationParser.class),
        Parser.get(ListPrefixedVariableParser.class),//
        Parser.get(ListSuffixedVariableParser.class)//
    );
  }
 
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.string);
  }
  
  public static class ListVariableMatchedWithVariableDeclarationParser extends LazyChain implements StringExpression {

    public ListVariableMatchedWithVariableDeclarationParser() {
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
    return ListVariableParser.class;
  }

  @Override
  public Class<? extends VariableParser> oneOfTypedVariableParser() {
    return ListPrefixedVariableParser.class;
  }

  @Override
  public Class<? extends TypeHintVariableParser> typeHintVariableParser() {
    return ListTypeHintPrefixParser.class;
  }
}