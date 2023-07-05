package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.TokenBaseOperator;

public class SampleConstructed implements TokenBaseOperator<CalculationContext, Float>{
	
	@Override
	public Float evaluate(CalculationContext calculateContext , TinyExpressionTokens token) {
		
		/*
		if(
			(isPresent($calculated_BlackIPAddressInThisSite)&$calculated_BlackIPAddressInThisSite>0.0) |
			(isPresent($calculated_BlackCaulisCookieInThisSite)&$calculated_BlackCaulisCookieInThisSite>0.0)
		){10
		}else{
			if(
				(isPresent($calculated_BlackIPAddressInOtherSites)&$calculated_BlackIPAddressInOtherSites>0.0)|
				(isPresent($calculated_BlackCaulisCookieInOtherSites)&$calculated_BlackCaulisCookieInOtherSites>0.0)
			){5
			}else{0}
		}
		*/
		
		float answer = 
		(calculateContext.isExists("calculated_BlackIPAddressInThisSite") && 
		calculateContext.getValue("calculated_BlackIPAddressInThisSite").get() >0.0f) ||
		(calculateContext.isExists("calculated_BlackCaulisCookieInThisSite") &&
		calculateContext.getValue("calculated_BlackCaulisCookieInThisSite").get() >0.0f)? 10:
		
		(calculateContext.isExists("calculated_BlackIPAddressInOtherSites") && 
		calculateContext.getValue("calculated_BlackIPAddressInOtherSites").get() >0.0f) ||
		(calculateContext.isExists("calculated_BlackCaulisCookieInOtherSites") &&
		calculateContext.getValue("calculated_BlackCaulisCookieInOtherSites").get() >0.0f)? 5:0
		;
		return answer;
		
	}

}
