package org.unlaxer.tinyexpression.evaluator.javacode;
import org.unlaxer.Token;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.TokenBaseOperator;

public class _CalculatorClass201896882657134757 implements TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>{

	@Override
	public Float evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , Token token) {
java.util.Optional<org.unlaxer.tinyexpression.parser.TestSideEffector> function0 = calculateContext.getObject(
org.unlaxer.tinyexpression.parser.TestSideEffector.class);
			float answer = (float) 

						function0.map(_function->_function.setBlackList(calculateContext , 10.0f)).orElse(10.0f)
			;
			return answer;
		}

}

