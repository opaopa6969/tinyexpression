package org.unlaxer.tinyexpression.parser.stringtype;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.ExpressionOrLiteral;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.StringClauseBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.evaluator.javacode.TokenCodeBuilder;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleNamedParenthesesParser;
import org.unlaxer.util.annotation.TokenExtractor;

public interface StringMultipleParameterPredicator extends TokenCodeBuilder{
  
  @TokenExtractor
  public default Token getLeftExpression(Token thisParserParsed) {
    return thisParserParsed.getChildWithParser(StringExpressionParser.class);
  }

  @TokenExtractor
  public default List<Token> getParameters(Token thisParserParsed){
    
    Token parameterParser = thisParserParsed.getChildWithParser(parameterParserClass());
    return getStringExpressions(parameterParser);
  }
  
  public Class<? extends Parser> parameterParserClass();
  
  static List<Token> getStringExpressions(Token parameterParser){
    
    Token stringExpressions =
          JavaStyleNamedParenthesesParser.getInnerParserParsed(parameterParser);
    List<Token> expressions = stringExpressions.filteredChildren.stream()
      .filter(token->token.parser instanceof StringExpressionParser)
      .collect(Collectors.toList());
    return expressions;
  }
  
  
  public String predicateMethodString();
  
  @Override
  public default void build(SimpleJavaCodeBuilder builder, Token token , 
      TinyExpressionTokens tinyExpressionTokens) {
    
    builder.append(predicateMethodString());
    
    List<Token> filteredChildren = token.filteredChildren;
    Iterator<ExpressionOrLiteral> iterator = filteredChildren.stream()
      .map(_token-> StringClauseBuilder.SINGLETON.build(_token, tinyExpressionTokens))
      .iterator();
    
    while (iterator.hasNext()) {
      ExpressionOrLiteral expressionOrLiteral = iterator.next();
      builder.append(expressionOrLiteral.toString());
      if(iterator.hasNext()) {
        builder.append(",");
      }
    }
    
    builder.append(")");
  }
}