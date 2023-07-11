import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;

public class V1Test_CalculatorClass5419900124322906293 implements TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>{

	@Override
	public Float evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , TinyExpressionTokens token) {
		float answer = (float) 


((calculateContext.getValue("age").orElse(0f)<18.0f) ? 500.0f:
(calculateContext.getValue("age").orElse(0f)>=60.0f) ? 700.0f:

'feeBySex' (65 - 73): org.unlaxer.parser.clang.IdentifierParser(calculateContext.getValue("sex").orElse(0f)))
		;
		return answer;
	}


float feeBySex(){
 return 
(((calculateContext.getValue("sex").orElse(0f)=='discountSexString' (137 - 154): org.unlaxer.parser.clang.IdentifierParser())&&('doDiscountBySex' (164 - 179): org.unlaxer.parser.clang.IdentifierParser())) ? 1000.0f:

1800.0f);
}



String discountSexString(){
 return "woman";
}



boolean doDiscountBySex(){
 return (true);
}



}