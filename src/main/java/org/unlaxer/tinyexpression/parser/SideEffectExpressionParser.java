package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser.JavaMethodParser;
import org.unlaxer.tinyexpression.parser.ReturningParser.Returning;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public abstract class SideEffectExpressionParser extends JavaStyleDelimitedLazyChain implements NumberExpression{
  
  private static final long serialVersionUID = 8228933717392969866L;
	
  static final Tag classMethodOrMethod = Tag.of("classMethodOrMethod");
	
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
        new Optional(
            Parser.get(()->new WordParser(":"))
        ),
        new Choice(
            Parser.get(JavaClassMethodParser.class).addTag(classMethodOrMethod),
            Parser.get(JavaMethodParser.class).addTag(classMethodOrMethod)
        ),
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
		return thisParserParsed.getChild(TokenPredicators.hasTag(classMethodOrMethod)); //2
	}
	
  @TokenExtractor
	public static Token getParametersClause(Token thisParserParsed) {
    return thisParserParsed.getChildWithParser(SideEffectExpressionParameterParser.class); //4
	}
  
  @TokenExtractor
	public static MethodAndParameters extract(Token token , TinyExpressionTokens tinyExpressionTokens) {
    
    Token returning = token.getChildFromAstNodes(0);
    Parser parser = returning.getParser();
    Class<?> returningType = 
        parser instanceof NumberExpression ?
            float.class : 
            parser instanceof StringExpression ?
                String.class :
                boolean.class;
        
	  
	  Token classMethodToken = getMethodClause(token);
	  ClassNameAndIdentifier extract = ((ClassNameAndIdentifierExtractor)classMethodToken.parser)
	    .extractClassNameAndIdentifier(classMethodToken, tinyExpressionTokens);
		
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