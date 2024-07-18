package org.unlaxer.tinyexpression;

import java.math.BigDecimal;
import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;

public interface NumberCalculator extends Calculator<Float> {

  public default CalculateResult calculate(CalculationContext calculateContext, String formula) {
    ParseContext parseContext = new ParseContext(new StringSource(formula));
    Parsed parsed = getParser().parse(parseContext);
    try {
      Token rootToken = tokenReduer().apply(parsed.getRootToken(true));
      Float answer = getCalculatorOperator().evaluate(calculateContext, rootToken);

      return new CalculateResult(parseContext, parsed, Optional.of(toBigDecimal(answer)), rootToken);

    } catch (Exception e) {
      Errors errors = new Errors(e);
      return new CalculateResult(parseContext, parsed, Optional.empty(), errors, null);
    } finally {
      parseContext.close();
    }
  }

  public BigDecimal toBigDecimal(Float value);

  public float toFloat(Float value);
}