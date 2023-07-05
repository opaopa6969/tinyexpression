package org.unlaxer.tinyexpression;

import java.math.RoundingMode;
import java.util.Map;

public class ConcurrentCalculationContext extends AbstractCalculationContext{
	
	
	public ConcurrentCalculationContext() {
		super();
	}

	public ConcurrentCalculationContext(int scale, RoundingMode roundingMode, Angle angle) {
		super(scale, roundingMode, angle);
	}

	@Override
	public <T> Map<String, T> newMap() {
		return new NullSafeConcurrentHashMap<>();
	}
}
