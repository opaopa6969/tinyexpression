package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.util.annotation.TokenExtractor;

public class BooleanVariableParser extends LazyChoice implements VariableParser , BooleanExpression{

  private static final long serialVersionUID = -60484510350410L;

  public BooleanVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(BooleanPrefixedVariableParser.class), 
          Parser.get(BooleanSuffixedVariableParser.class),
          Parser.get(BooleanVariableMatchedWithVariableDeclarationParser.class)
      );
  }
  
  public static String getVariableName(Token thisParserParsed) {
    Token choiced = ChoiceInterface.choiced(thisParserParsed);
    if(choiced.parser instanceof BooleanPrefixedVariableParser) {
      return BooleanPrefixedVariableParser.getVariableName(choiced);
    }else if(choiced.parser instanceof BooleanSuffixedVariableParser) {
      return BooleanSuffixedVariableParser.getVariableName(choiced);
    }
    throw new IllegalArgumentException();
  }

  @Override
  public Optional<VariableType> type() {
    return Optional.of(VariableType.bool);
  }
  
  public static class BooleanVariableMatchedWithVariableDeclarationParser extends LazyChain implements BooleanExpression {

    public BooleanVariableMatchedWithVariableDeclarationParser() {
      super();
    }

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(//
          Parser.get(DollarParser.class), //0
          Parser.get(BooleanVariableDeclarationMatchedTokenParser.class)//1
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