import org.unlaxer.Token;
import org.unlaxer.tinyexpression.TokenBaseOperator;

public class V1Test_CalculatorClass5128123986458659339 implements TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>{

	@Override
	public Float evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , Token token) {
		float answer = (float) 


((calculateContext.getValue("age").orElse(0f)<18.0f) ? 500.0f:
(calculateContext.getValue("age").orElse(0f)>=60.0f) ? 700.0f:

feeBySex(calculateContext,calculateContext.getString("sex").orElse("")))
		;
		return answer;
	}


float feeBySex(org.unlaxer.tinyexpression.CalculationContext calculateContext ,String sex){
 return 
(((calculateContext.getString("sex").orElse("")==discountSexString(calculateContext))&&(doDiscountBySex(calculateContext))) ? 1000.0f:

1800.0f);
}



String discountSexString(org.unlaxer.tinyexpression.CalculationContext calculateContext ){
 return "woman";
}



boolean doDiscountBySex(org.unlaxer.tinyexpression.CalculationContext calculateContext ){
 return (true);
}


@Override
public Token getRootToken() {
  // TODO Auto-generated method stub
  return null;
}



}