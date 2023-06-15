package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser.ClassNameAndIdentifier;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class SideEffectStringToBooleanExpressionParser extends JavaStyleDelimitedLazyChain implements NumberExpression {

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
		
	public static MethodAndParameters extract(Token token) {
		Token classMethod = getMethodClause(token);
		Token parameter = getParametersClause(token);
		
		ClassNameAndIdentifier extract = Parser.get(JavaClassMethodParser.class).extract(classMethod);
		SideEffectStringToBooleanExpressionParameterParser sideEffectStringToBooleanExpressionParameterParser = 
				Parser.get(SideEffectStringToBooleanExpressionParameterParser.class);
		List<Token> parameterTokens = sideEffectStringToBooleanExpressionParameterParser.parameterTokens(parameter);
		return new MethodAndParameters(extract, parameterTokens);
	}

  @TokenExtractor
	private static Token getParametersClause(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(SideEffectStringToBooleanExpressionParameterParser.class); //4
	}

  @TokenExtractor
	private static Token getMethodClause(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(JavaClassMethodParser.class); //2
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
						parser instanceof NumberExpression ? float.class :
						parser instanceof BooleanExpression ? boolean.class :
						parser instanceof StringExpression ? String.class :
						null;
				i++;
			}
		}
	}
	
	



	@Override
	public List<Parser> getLazyParsers() {
	  return
	    new Parsers(
        Parser.get(SideEffectNameParser.class),
        Parser.get(()->new WordParser(":")),
        Parser.get(JavaClassMethodParser.class),//2
        Parser.get(LeftParenthesisParser.class),
        Parser.get(SideEffectStringToBooleanExpressionParameterParser.class),//4
        Parser.get(RightParenthesisParser.class)
      );    

	}

}
