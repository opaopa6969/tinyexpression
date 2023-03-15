package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.math.RoundingMode;
import java.util.Optional;

import org.junit.Test;
import org.unlaxer.EnclosureDirection;
import org.unlaxer.Token;
import org.unlaxer.TokenEnclosureUtil;
import org.unlaxer.parser.ParsersSpecifier;
import org.unlaxer.tinyexpression.CalculateResult;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.CalculationContext.Angle;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.NormalCalculationContext;
import org.unlaxer.tinyexpression.model.CalculatorEditableLineModel;

public abstract class TokenTest {

	@Test
	public void testEnclosureSelect() {
		
	/*
		(1+1)/3+sin(30) (0 - 15): ExpressionParser 
		 (1+1)/3 (0 - 7): TermParser 
		  (1+1) (0 - 5): FactorParser 
		   (1+1) (0 - 5): ParenthesesParser 
		    ( (0 - 1): LeftParenthesisParser 
		    1+1 (1 - 4): ExpressionParser 
		     1 (1 - 2): TermParser 
		      1 (1 - 2): FactorParser 
		       1 (1 - 2): NumberParser 
		        1 (1 - 2): DigitParser 
		     + (2 - 3): PlusParser 
		     1 (3 - 4): TermParser 
		      1 (3 - 4): FactorParser 
		       1 (3 - 4): NumberParser 
		        1 (3 - 4): DigitParser 
		    ) (4 - 5): RightParenthesisParser 
		  / (5 - 6): DivisionParser 
		  3 (6 - 7): FactorParser 
		   3 (6 - 7): NumberParser 
		    3 (6 - 7): DigitParser 
		 + (7 - 8): PlusParser 
		 sin(30) (8 - 15): TermParser 
		  sin(30) (8 - 15): FactorParser 
		   sin(30) (8 - 15): SinParser 
		    sin (8 - 11): SinFuctionNameParser 
		    ( (11 - 12): LeftParenthesisParser 
		    30 (12 - 14): ExpressionParser 
		     30 (12 - 14): TermParser 
		      30 (12 - 14): FactorParser 
		       30 (12 - 14): NumberParser 
		        3 (12 - 13): DigitParser 
		        0 (13 - 14): DigitParser 
		    ) (14 - 15): RightParenthesisParser 
	*/

		String formula = "(1+1)/3+sin(30)";
		int position = formula.indexOf("1");
		
		ParsersSpecifier enclosurematchers = CalculatorEditableLineModel.enclosureMatchers;
		
		CalculationContext context = new NormalCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		CalculateResult result = calculator().calculate(context, formula);
		Token token = result.token.get();
		EnclosureDirection outer = EnclosureDirection.Outer;
		EnclosureDirection inner = EnclosureDirection.Inner;
		
		//expand
		Optional<Token> enclosureWithMatchInitial = 
				TokenEnclosureUtil.getEnclosureWithToken(token,outer , position , Optional.empty(), enclosurematchers);
		assertEquals("1", enclosureWithMatchInitial.get().tokenString.get());

		Optional<Token> enclosureWithMatch = 
				TokenEnclosureUtil.getEnclosureWithToken(token,outer , position , enclosureWithMatchInitial, enclosurematchers);
		assertEquals("1+1", enclosureWithMatch.get().tokenString.get());
		
		Optional<Token> enclosureWithMatch2 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,outer , position , enclosureWithMatch , enclosurematchers);
		assertEquals("(1+1)", enclosureWithMatch2.get().tokenString.get());
		
		Optional<Token> enclosureWithMatch3 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,outer , position , enclosureWithMatch2 , enclosurematchers);
		assertEquals("(1+1)/3", enclosureWithMatch3.get().tokenString.get());
		
		Optional<Token> enclosureWithMatch4 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,outer , position , enclosureWithMatch3 , enclosurematchers);
		assertEquals(formula, enclosureWithMatch4.get().tokenString.get());
		
		Optional<Token> enclosureWithMatch5 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,outer , position , enclosureWithMatch4 , enclosurematchers);
		assertFalse(enclosureWithMatch5.isPresent());
		
		//collapse
		Optional<Token> enclosureWithMatch6 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,inner , position , enclosureWithMatch4 , enclosurematchers);
		assertEquals("(1+1)/3", enclosureWithMatch6.get().tokenString.get());
		
		Optional<Token> enclosureWithMatch7 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,inner , position , enclosureWithMatch6 , enclosurematchers);
		assertEquals("(1+1)", enclosureWithMatch7.get().tokenString.get());

		Optional<Token> enclosureWithMatch8 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,inner , position , enclosureWithMatch7 , enclosurematchers);
		assertEquals("1+1", enclosureWithMatch8.get().tokenString.get());

		Optional<Token> enclosureWithMatch9 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,inner , position , enclosureWithMatch8 , enclosurematchers);
		assertEquals("1", enclosureWithMatch9.get().tokenString.get());
		
		Optional<Token> enclosureWithMatch10 = 
				TokenEnclosureUtil.getEnclosureWithToken(token,inner , position , enclosureWithMatch9 , enclosurematchers);
		assertFalse(enclosureWithMatch10.isPresent());


	}
	@Test
	public void testEnclosureSelectWithInvalid() {
		String formula = "(1+1)/3ax+sign(30)";
		int position = formula.indexOf("a");
		
		ParsersSpecifier enclosurematchers = CalculatorEditableLineModel.enclosureMatchers;
		
		CalculationContext context = new NormalCalculationContext(2,RoundingMode.HALF_UP,Angle.DEGREE);
		CalculateResult result = calculator().calculate(context, formula);
		Token token = result.token.get();
		EnclosureDirection outer = EnclosureDirection.Outer;
		
		//expand
		Optional<Token> enclosureWithMatchInitial = 
				TokenEnclosureUtil.getEnclosureWithToken(token,outer , position , Optional.empty(), enclosurematchers);
		assertFalse(enclosureWithMatchInitial.isPresent());
	}
	
	public abstract Calculator<?> calculator();

}
