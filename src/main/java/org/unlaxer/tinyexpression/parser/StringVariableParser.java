package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.referencer.MatchedTokenParser;
import org.unlaxer.tinyexpression.parser.javalang.AbstractVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringVariableParser extends LazyChoice implements VariableParser , StringExpression{

  private static final long serialVersionUID = -604853821610350410L;

  public StringVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(//
        Parser.get(StringPrefixedVariableParser.class),//
        Parser.get(StringSuffixedVariableParser.class),//
        Parser.get(StringVariableMatchedWithVariableDeclarationParser.class)
    );
  }
  
  public static String getVariableName(Token thisParserParsed) {
    Token choiced = ChoiceInterface.choiced(thisParserParsed);
    if(choiced.parser instanceof StringPrefixedVariableParser) {
      return StringPrefixedVariableParser.getVariableName(choiced);
    }else if(choiced.parser instanceof StringSuffixedVariableParser) {
      return StringSuffixedVariableParser.getVariableName(choiced);
    }
    throw new IllegalArgumentException();
  }

  @Override
  public Optional<VariableType> type() {
    return Optional.of(VariableType.string);
  }
  
  public static class VariableDeclarationMatchedTokenParser extends MatchedTokenParser{

    public VariableDeclarationMatchedTokenParser() {
      super(TokenPredicators.hasTag(AbstractVariableDeclarationParser.typed));
    }
  }
  
  public static class StringVariableMatchedWithVariableDeclarationParser extends LazyChain implements StringExpression {

    public StringVariableMatchedWithVariableDeclarationParser() {
      super();
    }

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(//
          Parser.get(DollarParser.class), //0
          Parser.get(VariableDeclarationMatchedTokenParser.class)//1
      );
    }

    
    @TokenExtractor
    static Token getVariableNameToken(Token thisParserParsed) {
      Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
      return token;
    }

    public static String getVariableName(Token thisParserParsed) {
      return NakedVariableParser.getVariableName(getVariableNameToken(thisParserParsed));
    }
  }
}