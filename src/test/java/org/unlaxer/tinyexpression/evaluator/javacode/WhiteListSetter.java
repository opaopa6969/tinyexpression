package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Optional;

import org.unlaxer.tinyexpression.CalculationContext;

public class WhiteListSetter implements ExternalCustomFunction{
	
  @SuppressWarnings("unused")
	public float setWhiteList(
		CalculationContext calculationContext , 
		float input	) {
		
    Optional<String/*UserHash*/> userHash = calculationContext.getObject("userHash", String.class/*UserHash.class*/);
		Optional<String/*SiteId*/> siteId = calculationContext.getObject("siteId", String.class/*SiteId.class*/);
		
		//set 
		// dao.setWhteList(siteId , userHash);
		return input;
	}

}
