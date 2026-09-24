// Copy of the java code block in src/test/resources/formulaInfo-test/*/formulaInfo.txt (callJavaCodeBlockWithPackage).
package sample.v1;
import org.unlaxer.tinyexpression.CalculationContext;
public class CheckAlphabets{
	public boolean check(CalculationContext calculationContext,String target){
		return target.matches("[a-zA-Z]+");
	}
}
