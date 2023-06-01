package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintStream;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TransactionElement;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;

public class FormulaParserTest {

	@Test
	public void tokenizeAndDisplay()  {

		for(String formula : new String[]{"(1.5+2e+1)/3","1+1-1+1","-1.35e+5"}){
			
			try(ParseContext parseContext = new ParseContext(new StringSource(formula))){
				
				FormulaParser formulaParser = Parser.get(FormulaParser.class);
				Parsed parsed = formulaParser.parse(parseContext);
				
				assertTrue(parseContext.allConsumed());
				assertTrue(parsed.isSucceeded());
				
				TransactionElement peek = parseContext.getCurrent();
				peek.tokens.forEach(token->output(token, System.out, 0));
			}
		}
	}
	
	@Test
	public void emptyFormula()  {
		
		String formula= "";
		try(ParseContext parseContext = new ParseContext(new StringSource(formula))){
			
			FormulaParser formulaParser = Parser.get(FormulaParser.class);
			Parsed parsed = formulaParser.parse(parseContext);
			
			assertTrue(parseContext.allConsumed());
			assertFalse(parsed.isSucceeded());
			
			TransactionElement peek = parseContext.getCurrent();
			peek.tokens.forEach(token->output(token, System.out, 0));
		}
	}
	
	@Test
	public void invalidFormula()  {
		
		String formula= "+-*/";
		try(ParseContext parseContext = new ParseContext(new StringSource(formula))){
			
			FormulaParser formulaParser = Parser.get(FormulaParser.class);
			Parsed parsed = formulaParser.parse(parseContext);
			
			assertFalse(parseContext.allConsumed());
			assertFalse(parsed.isSucceeded());
			
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
	
	@Test
	public void retrieveRootParser()  {
		FormulaParser formulaParser = Parser.get(FormulaParser.class);
		{
			Parser root = formulaParser.getRoot();
			assertEquals(formulaParser, root);
		}
	}

}
