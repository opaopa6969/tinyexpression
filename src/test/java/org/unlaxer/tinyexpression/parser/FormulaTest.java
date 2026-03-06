package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertTrue;

import java.io.PrintStream;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.ParserTestBase;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TransactionElement;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;

public class FormulaTest extends ParserTestBase{

	@Test
	public void test() {
		
		Parser formulaParser = Parser.get(FormulaParser.class);
		testPartialMatch(formulaParser, "1+2", "1+2");
		testPartialMatch(formulaParser, "-1", "-1");
		testPartialMatch(formulaParser, "+1", "+1");
		testPartialMatch(formulaParser, "1+(3/1)", "1+(3/1)");
		testPartialMatch(formulaParser, "1+(3+2/1+1)", "1+(3+2/1+1)");
		testPartialMatch(formulaParser, "(1e-3)+(3+2/1+1)", "(1e-3)+(3+2/1+1)");
		
		testUnMatch(formulaParser, "(1e-3)+(3+2/1+1)=");
		testUnMatch(formulaParser, "/1");
		testUnMatch(formulaParser, "*1");
	}
	
	@Test
	public void parseAndDisplay()  {
		
//		String formula= "(1.5+2e+1)/3";
		String formula= "3/1+1/3";
		
		Parser formulaParser = Parser.get(FormulaParser.class);

		try(ParseContext parseContext = new ParseContext(new StringSource(formula))){
			
			Parsed parsed = formulaParser.parse(parseContext);
			
			assertTrue(parseContext.allConsumed());
			assertTrue(parsed.isSucceeded());
			
			TransactionElement peek = parseContext.getCurrent();
			peek.tokens.forEach(token->output(token, System.out, 0));
		}
	}
	
	void output(Token token,PrintStream out, int level){
		
		if(false == token.tokenString.isPresent()){
			return;
		}
		for(int i = 0 ; i < level ; i++){
			out.print(" ");
		}
		
		out.format("%s : %s \n" , token.tokenString.get() , token.parser.getClass().getSimpleName());
		if(false == token.filteredChildren.isEmpty()){
			level++;
			for(Token original : token.filteredChildren){
				output(original , out , level);
			}
		}
	}

}
