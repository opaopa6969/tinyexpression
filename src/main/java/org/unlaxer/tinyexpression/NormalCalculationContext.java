package org.unlaxer.tinyexpression;

import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class NormalCalculationContext extends AbstractCalculationContext{
	
	
	public NormalCalculationContext() {
		super();
	}

	public NormalCalculationContext(int scale, RoundingMode roundingMode, Angle angle) {
		super(scale, roundingMode, angle);
	}

	@Override
	public <T> Map<String, T> newMap() {
		return new HashMap<>();
	}

}