package org.unlaxer.tinyexpression.evaluator.javacode;

import org.junit.Test;

public class BinaryConditionBuilderTest {

	@Test
	public void test() {
		
		for(float value : new float[] {-10f,0f,10f}) {
			
			int compare = Float.compare(0f, value);
			
			System.out.println("Float.compare(0,"+value+")="+compare);
		}
		
		
		
	}

}
