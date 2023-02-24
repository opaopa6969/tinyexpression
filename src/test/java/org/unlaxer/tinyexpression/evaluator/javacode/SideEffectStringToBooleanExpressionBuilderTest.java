package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.SideEffectStringToBooleanExpressionParser;

public class SideEffectStringToBooleanExpressionBuilderTest extends ParserTestBase {

	@Test
	public void test() {
		setLevel(OutputLevel.detail);
	    String formula =
	        "with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setBlackList('niku')";
	    var parser = new SideEffectStringToBooleanExpressionParser();
	    
	    testAllMatch(parser, formula);
	}

}
