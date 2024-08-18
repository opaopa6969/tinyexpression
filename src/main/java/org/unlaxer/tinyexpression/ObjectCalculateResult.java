package org.unlaxer.tinyexpression;

import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.evaluator.javacode.ResultType;

public class ObjectCalculateResult{
  
  public final boolean success;
  public final Optional<Token> operatorOperandTreeToken;
  public final Optional<Token> tokenAst;
  public final Optional<Object> answer;
  public final ResultType resultType; 
  public final ParseContext parseContext;
  public final Errors errors;
  public ObjectCalculateResult(ParseContext parseContext , Parsed parsed, Optional<Object> answer , Errors errors ,
      Token operatorOperandTreeToken , ResultType resultType) {
    super();
    this.parseContext = parseContext;
    this.tokenAst = parsed.getTokenOptional();
    this.success = parsed.isSucceeded();
    this.answer = answer;
    this.errors = errors;
    this.operatorOperandTreeToken = Optional.ofNullable(operatorOperandTreeToken);
    this.resultType = resultType;
  }
  
  public ObjectCalculateResult(ParseContext parseContext , Parsed parsed, Optional<Object> answer, 
      Token operatorOperandTreeToken , ResultType resultType) {
    this(parseContext, parsed, answer, new Errors() , operatorOperandTreeToken , resultType);
  }
  
}