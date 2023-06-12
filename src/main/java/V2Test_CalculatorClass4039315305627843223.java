import org.unlaxer.Token;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.TokenBaseOperator;

public class V2Test_CalculatorClass4039315305627843223 implements TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>{

	@Override
	public Float evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , Token token) {

	  java.util.Optional<org.unlaxer.tinyexpression.parser.TestSideEffector> function0 = 
	      calculateContext.getObject(org.unlaxer.tinyexpression.parser.TestSideEffector.class);
	  
	  float answer = (float) 


function0.map(_function->_function.booleanToFloatMethod(calculateContext , (calculateContext.getBoolean("Male").orElse(false)))).orElse(0.0f)
		;
		return answer;
	}

}