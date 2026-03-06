package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.Token.ScanDirection;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public abstract class IfExpressionParser extends JavaStyleDelimitedLazyChain {
    
  private static final long serialVersionUID = 8228933717392969866L;
  
  
  public IfExpressionParser() {
    super();
  }
  
  public static class IfFuctionNameParser extends SuggestableParser{

    private static final long serialVersionUID = -6045428101193616423L;

    public IfFuctionNameParser() {
      super(true, TinyExpressionKeywords.IF);
    }
  
    @Override
    public String getSuggestString(String matchedString) {
      return "(".concat(matchedString).concat("){ }else{ }");
    }
  }
  
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    
    Parsers parsers = new Parsers(
      Parser.get(IfFuctionNameParser.class),
      Parser.get(LeftParenthesisParser.class),
      Parser.newInstance(BooleanExpressionParser.class).addTag(ExpressionTags.condition.tag()),//2
      Parser.get(RightParenthesisParser.class),
      Parser.get(LeftCurlyBraceParser.class),
   // if(condition){$variable}else{$variable}だった時にどちらかの変数が型指定をする事を求める
      new Choice(
          new JavaStyleDelimitedLazyChain() {

            @Override
            public Parsers getLazyParsers() {
              return Parsers.of(
                  Parser.newInstance(strictTypedReturning())
                    .addTag(ExpressionTags.returning.tag(),ExpressionTags.thenClause.tag()),
                  Parser.get(RightCurlyBraceParser.class),
                  Parser.get(()->new WordParser("else")),
                  Parser.get(LeftCurlyBraceParser.class),
                  Parser.newInstance(nonStrictTypedReturning())
                    .addTag(ExpressionTags.returning.tag(),ExpressionTags.elseClause.tag())
              );
            }
          }.addTag(ExpressionTags.thenAndElse.tag()),
          new JavaStyleDelimitedLazyChain() {

            @Override
            public Parsers getLazyParsers() {
              return Parsers.of(
                  Parser.newInstance(nonStrictTypedReturning())
                    .addTag(ExpressionTags.returning.tag(),ExpressionTags.thenClause.tag()),
                  Parser.get(RightCurlyBraceParser.class),
                  Parser.get(()->new WordParser("else")),
                  Parser.get(LeftCurlyBraceParser.class),
                  Parser.newInstance(strictTypedReturning())
                    .addTag(ExpressionTags.returning.tag(),ExpressionTags.elseClause.tag())

              );
            }
          }.addTag(ExpressionTags.thenAndElse.tag())
      ),
      Parser.get(RightCurlyBraceParser.class)
    );
    
    return parsers;
  }
  
  
  /* 
   */
  public abstract Class<? extends Parser> strictTypedReturning(); 
  public abstract Class<? extends Parser> nonStrictTypedReturning(); 
  
  @TokenExtractor(timings = {Timing.CreateOperatorOperandTree,Timing.UseOperatorOperandTree})
  public static Token getBooleanExpression(Token thisParserParsed) {
    return thisParserParsed.getChild(
        TokenPredicators.parserImplements(BooleanExpression.class , VariableParser.class)
    );
  }
  
  @TokenExtractor(timings = {Timing.CreateOperatorOperandTree,Timing.UseOperatorOperandTree})
  public static Token getThenExpression(Token thisParserParsed , 
      Class<? extends ExpressionInterface> expressionInterfaceClass , Token conditionToken) {
    Predicate<Token> expressionFilter = token ->
        token != null && token.parser != null
            && TokenPredicators.parserImplements(expressionInterfaceClass, VariableParser.class).test(token);
    Predicate<Token> afterConditionFilter = conditionToken == null
        ? token -> true
        : TokenPredicators.afterToken(conditionToken);

    Token structuralThen = resolveExpressionCandidate(structuralBranchExpression(thisParserParsed, 0), expressionFilter);
    if (structuralThen != null) {
      return structuralThen;
    }

    Token taggedThen = findTaggedExpression(thisParserParsed, ExpressionTags.thenClause.tag(), expressionFilter);
    if (taggedThen != null) {
      return taggedThen;
    }

    List<Token> returning = thisParserParsed.flatten(ScanDirection.Breadth).stream()
      .filter(expressionFilter.and(afterConditionFilter))
      .limit(2)
      .collect(Collectors.toList());

    if (returning.isEmpty() == false) {
      return returning.get(0);
    }
    return findTaggedExpression(thisParserParsed, ExpressionTags.returning.tag(), expressionFilter);
  }
  
  @TokenExtractor(timings = {Timing.CreateOperatorOperandTree,Timing.UseOperatorOperandTree})
  public static Token getElseExpression(Token thisParserParsed , 
      Class<? extends ExpressionInterface> expressionInterfaceClass, Token conditionToken) {
    Predicate<Token> expressionFilter = token ->
        token != null && token.parser != null
            && TokenPredicators.parserImplements(expressionInterfaceClass, VariableParser.class).test(token);
    Predicate<Token> afterConditionFilter = conditionToken == null
        ? token -> true
        : TokenPredicators.afterToken(conditionToken);

    Token structuralElse = resolveExpressionCandidate(structuralBranchExpression(thisParserParsed, 4), expressionFilter);
    if (structuralElse != null) {
      return structuralElse;
    }

    Token taggedElse = findTaggedExpression(thisParserParsed, ExpressionTags.elseClause.tag(), expressionFilter);
    if (taggedElse != null) {
      return taggedElse;
    }

    List<Token> returning = thisParserParsed.flatten(ScanDirection.Breadth).stream()
        .filter(expressionFilter.and(afterConditionFilter))
        .limit(2)
        .collect(Collectors.toList());

    if (returning.size() >= 2) {
      return returning.get(1);
    }
    if (returning.isEmpty() == false) {
      return returning.get(0);
    }
    List<Token> taggedReturning = thisParserParsed.flatten(ScanDirection.Breadth).stream()
      .filter(TokenPredicators.hasTag(ExpressionTags.returning.tag()))
      .map(token -> resolveExpressionCandidate(token, expressionFilter))
      .filter(token -> token != null)
      .limit(2)
      .collect(Collectors.toList());
    if (taggedReturning.size() >= 2) {
      return taggedReturning.get(1);
    }
    return taggedReturning.isEmpty() ? null : taggedReturning.get(0);
  }

  private static Token findTaggedExpression(Token root, org.unlaxer.Tag tag, Predicate<Token> expressionFilter) {
    return root.flatten(ScanDirection.Breadth).stream()
        .filter(TokenPredicators.hasTag(tag))
        .map(token -> resolveExpressionCandidate(token, expressionFilter))
        .filter(token -> token != null)
        .findFirst()
        .orElse(null);
  }

  private static Token resolveExpressionCandidate(Token token, Predicate<Token> expressionFilter) {
    if (token == null) {
      return null;
    }
    if (expressionFilter.test(token)) {
      return token;
    }
    return token.flatten(ScanDirection.Breadth).stream()
        .filter(expressionFilter)
        .findFirst()
        .orElse(null);
  }

  private static Token structuralBranchExpression(Token root, int indexInChoice) {
    if (root == null || root.filteredChildren == null) {
      return null;
    }
    // New parser shape: [condition, thenExpr, elseExpr]
    if (root.filteredChildren.size() >= 3) {
      if (indexInChoice == 0) {
        return root.filteredChildren.get(1);
      }
      if (indexInChoice == 4) {
        return root.filteredChildren.get(2);
      }
      return null;
    }
    // Legacy parser shape: [..., choiceNode, ...] with branches inside choice.
    if (root.filteredChildren.size() <= 5) {
      return null;
    }
    Token choiceNode = root.filteredChildren.get(5);
    if (choiceNode == null || choiceNode.filteredChildren == null || choiceNode.filteredChildren.size() <= indexInChoice) {
      return null;
    }
    return choiceNode.filteredChildren.get(indexInChoice);
  }
  
  
  
}
