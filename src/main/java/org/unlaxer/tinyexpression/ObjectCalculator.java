package org.unlaxer.tinyexpression;

import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.evaluator.javacode.ResultType;

public interface ObjectCalculator extends Calculator<Object> {

  public default ObjectCalculateResult calculate(CalculationContext calculateContext, String formula , ResultType resultType) {
    ParseContext parseContext = new ParseContext(new StringSource(formula));
    Parsed parsed = getParser().parse(parseContext);
    try {
      Token rootToken = tokenReduer().apply(parsed.getRootToken(true));
      Object answer = getCalculatorOperator().evaluate(calculateContext, rootToken);

      return new ObjectCalculateResult(parseContext, parsed, Optional.of(answer), rootToken,resultType);

    } catch (Exception e) {
      Errors errors = new Errors(e);
      return new ObjectCalculateResult(parseContext, parsed, Optional.empty(), errors, null , resultType);
    } finally {
      parseContext.close();
    }
  }

}