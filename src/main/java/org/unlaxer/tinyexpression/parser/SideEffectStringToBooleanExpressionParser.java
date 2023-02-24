package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser.ClassNameAndIdentifier;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser.SideEffectNameParser;

public class SideEffectStringToBooleanExpressionParser extends WhiteSpaceDelimitedLazyChain implements Expression {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;	
	
	public SideEffectStringToBooleanExpressionParser() {
		super();
	}

	public SideEffectStringToBooleanExpressionParser(Name name) {
		super(name);
	}
	
	List<Parser> parsers;
	
	@Override
	public void initialize() {
		parsers = new Parsers(
			Parser.get(SideEffectNameParser.class),
			Parser.get(()->new WordParser(":")),
			Parser.get(JavaClassMethodParser.class),
			Parser.get(LeftParenthesisParser.class),
			Parser.get(SideEffectStringToBooleanExpressionParameterParser.class),
			Parser.get(RightParenthesisParser.class)
		);		
	}
	
	public static MethodAndParameters extract(Token token) {
		Token classMethod = getMethodClause(token);
		Token parameter = getParametersClause(token);
		
		ClassNameAndIdentifier extract = Parser.get(JavaClassMethodParser.class).extract(classMethod);
		SideEffectStringToBooleanExpressionParameterParser sideEffectStringToBooleanExpressionParameterParser = 
				Parser.get(SideEffectStringToBooleanExpressionParameterParser.class);
		List<Token> parameterTokens = sideEffectStringToBooleanExpressionParameterParser.parameterTokens(parameter);
		return new MethodAndParameters(extract, parameterTokens);
	}
	
	private static Token getParametersClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(4);
	}

	private static Token getMethodClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}

	public static class MethodAndParameters {
		public final ClassNameAndIdentifier classNameAndIdentifier;
		public final List<Token> parameterTokens;
		public final Class<?>[] parameterTypes;
		
		public MethodAndParameters(ClassNameAndIdentifier classNameAndIdentifier, List<Token> parameterTokens) {
			super();
			this.classNameAndIdentifier = classNameAndIdentifier;
			this.parameterTokens = parameterTokens;
//			this.parmeterTypes = parmeterTypes;
			
			parameterTypes = new Class<?>[parameterTokens.size()+2];
			parameterTypes[0] = CalculationContext.class;
			parameterTypes[1] = String.class;
			
			int i = 2;
			for (Token token : parameterTokens) {
				Parser parser = token.parser;
				parameterTypes[i] = 
						parser instanceof Expression ? float.class :
						parser instanceof BooleanExpression ? boolean.class :
						parser instanceof StringExpression ? String.class :
						null;
				i++;
			}
		}
	}
	
	



	@Override
	public List<Parser> getLazyParsers() {
		// TODO Auto-generated method stub
		return parsers;
	}

}
