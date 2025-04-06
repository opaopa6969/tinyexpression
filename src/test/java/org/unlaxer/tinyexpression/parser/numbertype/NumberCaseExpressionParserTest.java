package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.BasicCombinator;

public class NumberCaseExpressionParserTest {

  @Test
  public void test() {

    NumberCaseExpressionParser numberCaseExpressionParser = new NumberCaseExpressionParser();
    StringSource stringSource = new StringSource("true->1,false->2,true->3");
    ParseContext parseContext = new ParseContext(stringSource);
    Parsed parsed = numberCaseExpressionParser.parse(parseContext);
    System.out.println(parsed.status);
    System.out.println(TokenPrinter.get(parsed.getRootToken()));

    Token reduceBasicCombinator = reduceBasicCombinator(parsed.getRootToken());
    System.out.println(TokenPrinter.get(reduceBasicCombinator));
  }

  @Test
  public void test2() {

    NumberExpressionParser numberCaseExpressionParser = new NumberExpressionParser();
    StringSource stringSource = new StringSource("if( 1==0){1+2*3/4}else{5}");
    ParseContext parseContext = new ParseContext(stringSource);
    Parsed parsed = numberCaseExpressionParser.parse(parseContext);
    System.out.println(parsed.status);
    System.out.println(TokenPrinter.get(parsed.getRootToken()));

    Token reduceBasicCombinator = reduceBasicCombinator(parsed.getRootToken());
    System.out.println(TokenPrinter.get(reduceBasicCombinator));
  }

  public static Token reduceBasicCombinator(Token token) {

    List<Token> newChildren = extractCombinator(token);
    return new Token(token.tokenKind, token.getRangedString(), token.parser, newChildren);
  }

  public static List<Token> extractCombinator(Token token) {
    List<Token> newChildren = new ArrayList<>();
    List<Token> originalChildren2 = token.getOriginalChildren();
    for (Token child : originalChildren2) {
      Parser parser_ = child.parser;
      if (parser_ instanceof BasicCombinator) {
        List<Token> combinatorsChildren = extractCombinator(child);
        if (combinatorsChildren.isEmpty()) {
          continue;
        }
        newChildren.addAll(combinatorsChildren);
      } else {
        Token reduceBasicCombinator = reduceBasicCombinator(child);
        if (reduceBasicCombinator.getToken().isPresent() && false == reduceBasicCombinator.getToken().get().isEmpty()) {
          newChildren.add(reduceBasicCombinator);
        }
      }
    }
    return newChildren;
  }
}
