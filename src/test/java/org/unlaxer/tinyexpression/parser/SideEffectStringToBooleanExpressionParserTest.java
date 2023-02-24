package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class SideEffectStringToBooleanExpressionParserTest extends ParserTestBase {

	@Test
	public void test() {
		setLevel(OutputLevel.detail);
		
		SideEffectStringToBooleanExpressionParser sideEffectStringToBooleanExpressionParser = new SideEffectStringToBooleanExpressionParser();
		
		testAllMatch(sideEffectStringToBooleanExpressionParser, ("with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setWhiteList('niku')"), false);
	}

}
