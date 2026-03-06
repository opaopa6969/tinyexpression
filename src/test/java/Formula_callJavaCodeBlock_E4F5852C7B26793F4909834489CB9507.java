import org.unlaxer.Token;

public class Formula_callJavaCodeBlock_E4F5852C7B26793F4909834489CB9507 implements org.unlaxer.tinyexpression.TokenBaseCalculator{

  @Override
public Float   evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , Token token) {
java.util.Optional<sample.v0.CheckDigits_> function0 = calculateContext.getObject(
sample.v0.CheckDigits_.class);
Float answer = (Float   ) 

((function0.map(_function->_function.check(calculateContext , calculateContext.getString("input").orElse("not number")))
    .orElseThrow(()->new org.unlaxer.tinyexpression.CalculationException("class not found in CalculationContext. please set :sample.v1.CheckDigits"))) ? 
1.0f:
0.0f)
    ;
    return answer;
  }


}