package org.unlaxer.tinyexpression;

import java.math.BigDecimal;
import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;

public class CalculateResult{
  
  public final boolean success;
  public final Optional<Token> operatorOperandTreeToken;
  public final Optional<Token> tokenAst;
  public final Optional<BigDecimal> answer;
  public final ParseContext parseContext;
  public final Errors errors;
  public CalculateResult(ParseContext parseContext , Parsed parsed, Optional<BigDecimal> answer , Errors errors , Token operatorOperandTreeToken) {
    super();
    this.parseContext = parseContext;
    this.tokenAst = parsed.getTokenOptional();
    this.success = parsed.isSucceeded();
    this.answer = answer;
    this.errors = errors;
    this.operatorOperandTreeToken = Optional.ofNullable(operatorOperandTreeToken);
  }
  
  public CalculateResult(ParseContext parseContext , Parsed parsed, Optional<BigDecimal> answer, Token operatorOperandTreeToken) {
    this(parseContext, parsed, answer, new Errors() , operatorOperandTreeToken);
  }
  
}