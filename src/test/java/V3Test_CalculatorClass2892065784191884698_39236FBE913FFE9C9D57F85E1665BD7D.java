import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.Token;

public class V3Test_CalculatorClass2892065784191884698_39236FBE913FFE9C9D57F85E1665BD7D implements org.unlaxer.tinyexpression.TokenBaseCalculator{

	@Override
public java.lang.Float	 evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , Token token) {
java.util.Optional<org.unlaxer.tinyexpression.Fee> function0 = calculateContext.getObject(
org.unlaxer.tinyexpression.Fee.class);
java.lang.Float answer = (java.lang.Float		) 

function0.map(_function->_function.calculate(calculateContext , calculateContext.getValue("age").orElse((10.0f+8.0f)).floatValue() , 1000.0f , calculateContext.getValue("taxRate").orElse(0f).floatValue())).orElseThrow(()->new org.unlaxer.tinyexpression.CalculationException("class not found in CalculationContext. please set :org.unlaxer.tinyexpression.Fee"))
		;
		return answer;
	}


}