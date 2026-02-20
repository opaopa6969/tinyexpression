package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.Parser;

public class JavaClassMethodParserTest extends ParserTestBase{

	@Test
	public void test() {
		{
			JavaClassMethodParser javaClassMethodParser = Parser.get(JavaClassMethodParser.class);
			TestResult testAllMatch = testUnMatch(javaClassMethodParser, "test");
			
			String string = TokenPrinter.get(testAllMatch.parsed.getRootToken(),OutputLevel.detail);
			System.out.println(string);
			
		}
		
		{
			JavaClassMethodParser javaClassMethodParser = Parser.get(JavaClassMethodParser.class);
			TestResult testAllMatch = testAllMatch(javaClassMethodParser, "jp.caulis.Foo#test");
			
			Token rootToken = testAllMatch.parsed.getRootToken();
			String string = TokenPrinter.get(rootToken,OutputLevel.simple);
			System.out.println(string);
			
			List<Token> filteredChildren = rootToken.filteredChildren;
			filteredChildren.stream().forEach(System.out::println);
			
			
		}

		
		
	}

}
