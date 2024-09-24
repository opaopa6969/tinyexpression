package org.unlaxer.tinyexpression;

import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class CalculateResult{
  
  public final boolean success;
  public final Optional<Token> operatorOperandTreeToken;
  public final Optional<Token> tokenAst;
  public final Optional<Object> answer;
  public final ExpressionType resultType; 
  public final ParseContext parseContext;
  public final Errors errors;
  public CalculateResult(ParseContext parseContext , Parsed parsed, Optional<Object> answer , Errors errors ,
      Token operatorOperandTreeToken , ExpressionType resultType) {
    super();
    this.parseContext = parseContext;
    this.tokenAst = parsed.getTokenOptional();
    this.success = parsed.isSucceeded();
    this.answer = answer;
    this.errors = errors;
    this.operatorOperandTreeToken = Optional.ofNullable(operatorOperandTreeToken);
    this.resultType = resultType;
  }
  
  public CalculateResult(ParseContext parseContext , Parsed parsed, Optional<Object> answer, 
      Token operatorOperandTreeToken , ExpressionType resultType) {
    this(parseContext, parsed, answer, new Errors() , operatorOperandTreeToken , resultType);
  }
  
  public <T> T get(Class<T> returningClass){
    return returningClass.cast(answer.get());
  }
  
  public boolean calculated() {
    return answer.isPresent();
  }
  
}