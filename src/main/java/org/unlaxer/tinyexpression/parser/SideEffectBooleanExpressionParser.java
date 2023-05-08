package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser.ClassNameAndIdentifier;

@Deprecated
public class SideEffectBooleanExpressionParser extends WhiteSpaceDelimitedLazyChain implements Expression{
	
	private static final long serialVersionUID = 8228933717392969866L;
	
	
	public SideEffectBooleanExpressionParser() {
		super();
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return
      new Parsers(
        Parser.get(SideEffectNameParser.class),
        Parser.get(()->new WordParser(":")),
        Parser.get(JavaClassMethodParser.class),//2
        Parser.get(LeftParenthesisParser.class),
        Parser.get(SideEffectBooleanExpressionParameterParser.class),//4
        Parser.get(RightParenthesisParser.class)
      );
	}
	
	public static Token getMethodClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
	
	public static Token getParametersClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(4);
	}
	
	public static MethodAndParameters extract(Token token) {
		
		Token classMethod = getMethodClause(token);
		
		ClassNameAndIdentifier extract = Parser.get(JavaClassMethodParser.class).extract(classMethod);
		
		Token parameter = getParametersClause(token);
		
		SideEffectBooleanExpressionParameterParser sideEffectBooleanExpressionParameterParser = 
				Parser.get(SideEffectBooleanExpressionParameterParser.class);
		
		List<Token> parameterTokens = sideEffectBooleanExpressionParameterParser.parameterTokens(parameter);
		
		return new MethodAndParameters(extract, parameterTokens);
	}
	
	public static class MethodAndParameters{
		public final ClassNameAndIdentifier classNameAndIdentifier;
		public final List<Token> parameterTokens;
		public final Class<?>[] parameterTypes;
		public MethodAndParameters(ClassNameAndIdentifier classNameAndIdentifier, List<Token> parameterTokens) {
			super();
			this.classNameAndIdentifier = classNameAndIdentifier;
			this.parameterTokens = parameterTokens;
			parameterTypes = new Class<?>[parameterTokens.size()+2];
			parameterTypes[0] = CalculationContext.class;
			parameterTypes[1] = boolean.class;
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
}