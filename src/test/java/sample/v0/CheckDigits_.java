package sample.v0;//version1. if logic updates then update package.
import org.unlaxer.tinyexpression.CalculationContext;

public class CheckDigits_{
	public boolean check(CalculationContext calculationContext,String target){
		return target.matches("\\d+");
	}
}
