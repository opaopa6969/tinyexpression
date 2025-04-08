package org.unlaxer.tinyexpression.evaluator.javacode.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

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
    
    public enum TokenKind{
      LeftOperand,
      OperatorPlus,
      OperatorMinus,
      RightOperand
      ;
      public Name get() {
        return Name.of(this);
      }
    }
    
    static ASTNodeMappings nodeMapping = new ASTNodeMappings(
        new ASTNodeMapping(TokenKind.LeftOperand.get(), ASTNodeKind.Operand),
        new ASTNodeMapping(TokenKind.OperatorPlus.get(), ASTNodeKind.Operator,Opecodes.numberPlus),
        new ASTNodeMapping(TokenKind.OperatorMinus.get(), ASTNodeKind.Operator,Opecodes.numberMinus),
        new ASTNodeMapping(TokenKind.RightOperand.get(), ASTNodeKind.Operand)
    ); 
    
    public NExpressionParser() {
      super();
//      setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand);
    }

    public NExpressionParser(Name name) {
      super(name);
//      setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand);
    }

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          Parser.get(NTermParser.class)
            .setASTNodeMapping(nodeMapping.get(TokenKind.LeftOperand.get())),
          new ZeroOrMore(
              new Chain(
                  new Choice(
                      new WordParser("+")
                        .setASTNodeMapping(nodeMapping.get(TokenKind.OperatorPlus.get())),
                      new WordParser("-")
                        .setASTNodeMapping(nodeMapping.get(TokenKind.OperatorMinus.get()))
                  ),
                  Parser.get(NTermParser.class)
                    .setASTNodeMapping(nodeMapping.get(TokenKind.RightOperand.get()))
              )
          )
      );
    }
  }
  
  
  
  // <term>::= <factor>[('*'|'/')<factor>]*
  public static class NTermParser extends LazyChain{
    
    public enum TokenKind{
      LeftOperand,
      OperatorMultiple,
      OperatorDivide,
      RightOperand
      ;
      public Name get() {
        return Name.of(this);
      }
    }
    
    static ASTNodeMappings nodeMapping = new ASTNodeMappings(
        new ASTNodeMapping(TokenKind.LeftOperand.get(), ASTNodeKind.Operand),
        new ASTNodeMapping(TokenKind.OperatorMultiple.get(), ASTNodeKind.Operator,Opecodes.numberMultiple),
        new ASTNodeMapping(TokenKind.OperatorDivide.get(), ASTNodeKind.Operator,Opecodes.numberDivide),
        new ASTNodeMapping(TokenKind.RightOperand.get(), ASTNodeKind.Operand)
    ); 


    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          Parser.get(NFactorParser.class)
            .setASTNodeMapping(nodeMapping.get(TokenKind.LeftOperand.get())),
          new ZeroOrMore(
              new Chain(
                  new Choice(
                      new WordParser("*")
                        .setASTNodeMapping(nodeMapping.get(TokenKind.OperatorMultiple.get())),
                      new WordParser("/")
                        .setASTNodeMapping(nodeMapping.get(TokenKind.OperatorDivide.get()))
                  ),
                  Parser.get(NFactorParser.class)
                    .setASTNodeMapping(nodeMapping.get(TokenKind.RightOperand.get()))
              )
          )
      );
    }

    
  }
  // <factor>::= (<numberLiteral>|'(' <expression>')' )
  public static class NFactorParser extends LazyChoice{
    
    public enum TokenKind{
      Literal,
      ParenthesesExpression,
      ;
      public Name get() {
        return Name.of(this);
      }
    }
    
    static ASTNodeMappings nodeMapping = new ASTNodeMappings(
        new ASTNodeMapping(TokenKind.Literal.get(), ASTNodeKind.Operand),
        new ASTNodeMapping(TokenKind.ParenthesesExpression.get(), ASTNodeKind.Operand)
    ); 


    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          new NumberLiteralParser()
            /*.setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand)*/,
          new ParenthesesParser(
              Parser.newInstance(NExpressionParser.class)
            /*  .setASTNodeKind(Name.classBaseOf(this),ASTNodeKind.Operand)*/
          )
      );
    }

    
  }
  
  public static class NumberLiteralParser extends OneOrMore{

    public NumberLiteralParser() {
      super(new DigitParser());
    }
  }
  
  public static class ASTNodeMappings{
    
    LinkedHashMap<Name, ASTNodeMapping> mappingByName = new LinkedHashMap<>();

    public ASTNodeMappings(ASTNodeMapping... astNodeMappings) {
      super();
      for (ASTNodeMapping astNodeMapping : astNodeMappings) {
        astNodeMapping.setAstNodeMappings(this);
        mappingByName.put(astNodeMapping.name(), astNodeMapping);
      }
    }
    
    public ASTNodeMapping get(Name name) {
      return mappingByName.get(name);
    }
  }
  
  public static class ASTNodeMapping{
    
    ASTNodeMappings astNodeMappings;
    Name name;
    ASTNodeKind astNodeKind;
    Opecode opecode;
    
    public ASTNodeMapping(org.unlaxer.Name name, ASTNodeKind astNodeKind) {
      this(name,astNodeKind,null);
    }
    public ASTNodeMapping(org.unlaxer.Name name, ASTNodeKind astNodeKind, Opecode opecode) {
      super();
      this.name = name;
      this.astNodeKind = astNodeKind;
      this.opecode = opecode;
    }
    public ASTNodeKind astNodeKind() {
      return astNodeKind;
    }
    public Optional<Opecode> opecode() {
      return Optional.ofNullable(opecode);
    }
    public Name name() {
      return name;
    }
    public ASTNodeMappings astNodeMappings() {
      return astNodeMappings;
    }
    public void setAstNodeMappings(ASTNodeMappings astNodeMappings) {
      this.astNodeMappings = astNodeMappings;
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
//    for (Token token : flatten) {
//      System.out.println(token.getToken().orElse("")+" -> "+ token.getParser().getPath());
//    }
    for (Token token : flatten) {
      Parser parser = token.getParser();
      ASTNodeKind astNodeKind = parser.astNodeKind();
      if(astNodeKind == ASTNodeKind.Operand  || astNodeKind == ASTNodeKind.Operator) {
        System.out.println(token.getToken().orElse("")+" -> "+ parser.getName()+"/"+ token.getPath());
      }
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

  /*
  1*3+4/5-(6+(0+7))*8/9 -> /NExpressionParser
  1*3 -> /NExpressionParser/NTermParser
  1 -> /NExpressionParser/NTermParser/NFactorParser
  1 -> /NExpressionParser/NTermParser/NFactorParser/NumberLiteralParser
  1 -> /NExpressionParser/NTermParser/NFactorParser/NumberLiteralParser/DigitParser
  *3 -> /NExpressionParser/NTermParser/ZeroOrMore
  *3 -> /NExpressionParser/NTermParser/ZeroOrMore/Chain
  * -> /NExpressionParser/NTermParser/ZeroOrMore/Chain/Choice
  * -> /NExpressionParser/NTermParser/ZeroOrMore/Chain/Choice/WordParser
  3 -> /NExpressionParser/NTermParser/ZeroOrMore/Chain/NFactorParser
  3 -> /NExpressionParser/NTermParser/ZeroOrMore/Chain/NFactorParser/NumberLiteralParser
  3 -> /NExpressionParser/NTermParser/ZeroOrMore/Chain/NFactorParser/NumberLiteralParser/DigitParser
  +4/5-(6+(0+7))*8/9 -> /NExpressionParser/ZeroOrMore
  +4/5 -> /NExpressionParser/ZeroOrMore/Chain
  + -> /NExpressionParser/ZeroOrMore/Chain/Choice
  + -> /NExpressionParser/ZeroOrMore/Chain/Choice/WordParser
  4/5 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser
  4 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser
  4 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/NumberLiteralParser
  4 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/NumberLiteralParser/DigitParser
  /5 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore
  /5 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain
  / -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/Choice
  / -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/Choice/WordParser
  5 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser
  5 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser/NumberLiteralParser
  5 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser/NumberLiteralParser/DigitParser
  -(6+(0+7))*8/9 -> /NExpressionParser/ZeroOrMore/Chain
  - -> /NExpressionParser/ZeroOrMore/Chain/Choice
  - -> /NExpressionParser/ZeroOrMore/Chain/Choice/WordParser
  (6+(0+7))*8/9 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser
  (6+(0+7)) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser
  (6+(0+7)) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser
  ( -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/LeftParenthesisParser
  6+(0+7) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser
  6 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser
  6 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser/NFactorParser
  6 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser/NFactorParser/NumberLiteralParser
  6 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser/NFactorParser/NumberLiteralParser/DigitParser
   -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser/ZeroOrMore
  +(0+7) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore
  +(0+7) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain
  + -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/Choice
  + -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/Choice/WordParser
  (0+7) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser
  (0+7) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser
  (0+7) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser
  ( -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/LeftParenthesisParser
  0+7 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser
  0 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser
  0 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser/NFactorParser
  0 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser/NFactorParser/NumberLiteralParser
  0 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser/NFactorParser/NumberLiteralParser/DigitParser
   -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/NTermParser/ZeroOrMore
  +7 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore
  +7 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain
  + -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/Choice
  + -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/Choice/WordParser
  7 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser
  7 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser
  7 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/NumberLiteralParser
  7 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/NumberLiteralParser/DigitParser
   -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore
  ) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/RightParenthesisParser
   -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore
  ) -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/NFactorParser/ParenthesesParser/RightParenthesisParser
  *8/9 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore
  *8 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain
  * -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/Choice
  * -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/Choice/WordParser
  8 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser
  8 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser/NumberLiteralParser
  8 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser/NumberLiteralParser/DigitParser
  /9 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain
  / -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/Choice
  / -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/Choice/WordParser
  9 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser
  9 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser/NumberLiteralParser
  9 -> /NExpressionParser/ZeroOrMore/Chain/NTermParser/ZeroOrMore/Chain/NFactorParser/NumberLiteralParser/DigitParser
*/
}
