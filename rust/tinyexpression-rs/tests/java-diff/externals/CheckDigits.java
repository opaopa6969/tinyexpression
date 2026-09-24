// Copy of the java code block in src/test/resources/formulaInfo-test/*/formulaInfo.txt (callJavaCodeBlock).
import org.unlaxer.tinyexpression.CalculationContext;
public class CheckDigits{
	public boolean check(CalculationContext calculationContext,String target){
		return target.matches("\\d+");
	}
}
