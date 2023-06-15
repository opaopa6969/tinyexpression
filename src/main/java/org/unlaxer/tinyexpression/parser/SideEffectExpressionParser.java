package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser.ClassNameAndIdentifier;
import org.unlaxer.tinyexpression.parser.ReturningParser.Returning;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public abstract class SideEffectExpressionParser extends JavaStyleDelimitedLazyChain implements NumberExpression{
  
  private static final long serialVersionUID = 8228933717392969866L;
	
	
	public SideEffectExpressionParser() {
		super();
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return
      new Parsers(
        Parser.get(SideEffectNameParser.class),
//        new Optional(Parser.get(ReturningParser.class)),
        typedReturningParser(),
        Parser.get(()->new WordParser(":")),
        Parser.get(JavaClassMethodParser.class),//2
        Parser.get(LeftParenthesisParser.class),
        Parser.get(SideEffectExpressionParameterParser.class),//4
        Parser.get(RightParenthesisParser.class)
      );
	}
	
	abstract Parser typedReturningParser();

//	@TokenExtractor
//  public static java.util.Optional<Token> getReturningClause(Token thisParserParsed) {
//    return thisParserParsed.getChildWithParserAsOptional(ReturningParser.class);
//  }
  @TokenExtractor
  @VirtualTokenCreator
  public static Token getReturningClause(Token thisParserParsed, java.util.Optional<Token> firstParameter) {
    return thisParserParsed.getChildAsOptional(token->token.parser instanceof Returning)
        .orElseGet(()->ReturningParser.getReturningParserWhenNotSpecifiedReturingClause(
            getReturningPosition(thisParserParsed),firstParameter));
  }
  
  static int getReturningPosition(Token thisParserParsed) {
    Token childWithParser = thisParserParsed.getChildWithParser(SideEffectNameParser.class);
    return childWithParser.tokenRange.endIndexExclusive;
  }

	@TokenExtractor
	public static Token getMethodClause(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(JavaClassMethodParser.class); //2
	}
	
  @TokenExtractor
	public static Token getParametersClause(Token thisParserParsed) {
    return thisParserParsed.getChildWithParser(SideEffectExpressionParameterParser.class); //4
	}
  
  @TokenExtractor
	public static MethodAndParameters extract(Token token) {
    
    Token returning = token.getChildFromAstNodes(0);
    Parser parser = returning.getParser();
    Class<?> returningType = 
        parser instanceof NumberExpression ?
            float.class : 
            parser instanceof StringExpression ?
                String.class :
                boolean.class;
        
	  
//		Token classMethod = token.filteredChildren.get(0);//TODO token.getChild(JavaClassMethodParser.class);
	  Token classMethod = token.getChildWithParser(JavaClassMethodParser.class);
		
		ClassNameAndIdentifier extract = Parser.get(JavaClassMethodParser.class).extract(classMethod);
		
//		Token parameter = token.filteredChildren.get(1);
		Token parametersClause = getParametersClause(token);
		
		SideEffectExpressionParameterParser sideEffectExpressionParameterParser = 
				Parser.get(SideEffectExpressionParameterParser.class);
		
		List<Token> parameterTokens = sideEffectExpressionParameterParser.parameterTokens(parametersClause);
		
		return new MethodAndParameters(returning , returningType, extract, parameterTokens);
	}
	
	public static class MethodAndParameters{
	  public final Token returningToken;
	  public final Class<?> returningType;
	  
		public final ClassNameAndIdentifier classNameAndIdentifier;
		public final List<Token> parameterTokens;
		public final Class<?>[] parameterTypes;
		public MethodAndParameters(
		    Token returningToken, Class<?> returningType,
		    ClassNameAndIdentifier classNameAndIdentifier, List<Token> parameterTokens) {
			super();
			this.returningToken = returningToken;
			this.returningType = returningType;
			this.classNameAndIdentifier = classNameAndIdentifier;
			this.parameterTokens = parameterTokens;
			parameterTypes = new Class<?>[parameterTokens.size()+2];
			parameterTypes[0] = CalculationContext.class;
			parameterTypes[1] = float.class;
			int i = 2;
			for (Token token : parameterTokens) {
				Parser parser = token.parser;
				parameterTypes[i] =
				    parser instanceof StringVariableParser ? String.class :
            parser instanceof BooleanVariableParser ? boolean.class :
            parser instanceof NumberVariableParser ? float.class :
            parser instanceof NakedVariableParser ? float.class :
						parser instanceof NumberExpression ? float.class :
						parser instanceof BooleanExpression ? boolean.class :
						parser instanceof StringExpression ? String.class :
						null;
				i++;
			}
		}
	}
}