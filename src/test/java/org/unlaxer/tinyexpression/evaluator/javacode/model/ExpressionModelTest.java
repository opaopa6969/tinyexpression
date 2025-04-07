package org.unlaxer.tinyexpression.evaluator.javacode.model;

import java.util.List;

import org.junit.Test;
import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.ast.ASTNodeKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.combinator.OneOrMore;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.DigitParser;
import org.unlaxer.tinyexpression.parser.Opecode;
import org.unlaxer.tinyexpression.parser.Opecodes;

public class ExpressionModelTest {
  
  public enum ASTNode{
    
  }
  
  
  // <expression> ::= <term>[('+'|'-')<term>]*
  public static class NExpressionParser extends LazyChain{

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          Parser.get(NTermParser.class)
            .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand),
          new ZeroOrMore(
              new Chain(
                  new Choice(
                      new WordParser("+")
                        .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operator)
                        .putObject(Name.of(Opecode.class),Opecodes.numberPlus),
                      new WordParser("-")
                        .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operator)
                        .putObject(Name.of(Opecode.class),Opecodes.numberMinus)
                  ),
                  Parser.get(NTermParser.class)
                    .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand)
              )
          )
      );
    }
  }
  
  // <term>::= <factor>[('*'|'/')<factor>]*
  public static class NTermParser extends LazyChain{

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          Parser.get(NFactorParser.class)
            .setASTNodeKind(Name.classBaseOf(this), ASTNodeKind.Operand),
          new ZeroOrMore(
              new Chain(
                  new Choice(
                      new WordParser("*")
                        .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operator)
                        .putObject(Name.of(Opecode.class),Opecodes.numberMultiple),
                      new WordParser("/")
                        .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operator)
                        .putObject(Name.of(Opecode.class),Opecodes.numberDivide)
                  ),
                  Parser.get(NFactorParser.class)
                    .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand)
              )
          )
      );
    }

    
  }
  // <factor>::= (<numberLiteral>|'(' <expression>')' )
  public static class NFactorParser extends LazyChoice{

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          new NumberLiteralParser()
            .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand),
          new ParenthesesParser(
              Parser.newInstance(NExpressionParser.class)
                .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand)
          )
      );
    }

    
  }
  
  public static class NumberLiteralParser extends OneOrMore{

    public NumberLiteralParser() {
      super(new DigitParser());
    }
  }
  
  @Test
  public void test() {
    
    NExpressionParser nExpressionParser = new NExpressionParser();
    StringSource stringSource = new StringSource("1*3+4/5-(6+(0+7))*8/9)");
//    StringSource stringSource = new StringSource("(1)");
    ParseContext parseContext = new ParseContext(stringSource);
    Parsed parsed = nExpressionParser.parse(parseContext);
    Token rootToken = parsed.getRootToken();
    System.out.println(parsed.status);
    System.out.println(TokenPrinter.get(rootToken));
    System.out.println(TokenPrinter.get(rootToken.reduceBasicCombinator()));
    
    List<Token> flatten = rootToken.flatten();
    for (Token token : flatten) {
      System.out.println(token.getToken().orElse("")+" -> "+ token.getParser().getPath());
    }
  }
  
//  public static class ExpressionModelCreator{
//    
//    public ExpressionModel create(Token token) {
//      
//      Parser parser = token.parser;
//      boolean hasTag = parser.hasTag(ASTNodeKind.Operator.tag());
//      
//    }
//  }

}
