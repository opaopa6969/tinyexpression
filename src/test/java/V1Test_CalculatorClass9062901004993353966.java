import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;

public class V1Test_CalculatorClass9062901004993353966 implements TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>{

	@Override
	public Float evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , TinyExpressionTokens token) {
java.util.Optional<org.unlaxer.tinyexpression.CalculatorImplTest> function0 = calculateContext.getObject(
org.unlaxer.tinyexpression.CalculatorImplTest.class);
		float answer = (float) 

function0.map(_function->_function.calculate(calculateContext , calculateContext.getValue("age").orElse(0f) , 1000.0f , calculateContext.getValue("taxRate").orElse(0f))).orElse(0.0f)
		;
		return answer;
	}

}