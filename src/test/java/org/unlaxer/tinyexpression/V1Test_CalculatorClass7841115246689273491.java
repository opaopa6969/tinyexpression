package org.unlaxer.tinyexpression;
import org.unlaxer.Token;

/*
if($ForcedRelativeSuspiciousValue1){1919}else{
  if($ForcedRelativeSuspiciousValue5){5}else{
    if(($POST_PROCESS_OriginalSpec_RiskyCountry>0.0)|(isPresent($calculated_TorNode)&$calculated_TorNode>0.0)|(isPresent($calculated_BrowserTypeIsTool)&$calculated_BrowserTypeIsTool>0.0)){5}else{
      if(($POST_PROCESS_OriginalSpec_ChineseLanguageOrNotJapanTimezone>0.0)){4}else{
        if($default_RelativeSuspiciousValue==5){4}else{
          $default_RelativeSuspiciousValue
        }
      }
    }
  }
}
*/
public class V1Test_CalculatorClass7841115246689273491 implements TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>{

	@Override
	public Float evaluate(org.unlaxer.tinyexpression.CalculationContext calculateContext , Token token) {
			float answer = (float) 

((calculateContext.getBoolean("ForcedRelativeSuspiciousValue1").orElse(false)) ? 
calculateContext.getValue("ForcedRelativeSuspiciousValue1").orElse(0f):
1919.0f)
			;
			return answer;
		}

  @Override
  public Token getRootToken() {
    // TODO Auto-generated method stub
    return null;
  }

}