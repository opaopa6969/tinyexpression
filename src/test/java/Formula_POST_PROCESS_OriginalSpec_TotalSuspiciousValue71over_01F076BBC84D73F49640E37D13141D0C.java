import org.unlaxer.Token;
public class Formula_POST_PROCESS_OriginalSpec_TotalSuspiciousValue71over_01F076BBC84D73F49640E37D13141D0C implements org.unlaxer.tinyexpression.TokenBaseCalculator{
	@Override
	public Float evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , Token token) {
		float answer = (float) 
((calculateContext.getValue("default_totalSuspiciousValue").orElse(0f)>=71.0f) ? 
1.0f:
0.0f)
		;
		return answer;
	}
}