package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.CodePointIndex;
import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser.JavaMethodParser;
import org.unlaxer.tinyexpression.parser.ReturningParser.Returning;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.bool.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;
import org.unlaxer.tinyexpression.parser.number.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.string.StringExpression;
import org.unlaxer.tinyexpression.parser.string.StringVariableParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public abstract class SideEffectExpressionParser extends JavaStyleDelimitedLazyChain implements ExpressionInterface{
  
  private static final long serialVersionUID = 8228933717392969866L;
	
  static final Tag classMethodOrMethod = Tag.of("classMethodOrMethod");
	
	public SideEffectExpressionParser() {
		super();
	}
	
	@Override
	public Parsers getLazyParsers() {
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
        new Optional(
            Parser.get(ArgumentsParser.class)
        ),
        Parser.get(RightParenthesisParser.class)
      );
	}
	
	public abstract Parser typedReturningParser();

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
  
  static CodePointIndex getReturningPosition(Token thisParserParsed) {
    Token tokenOfChildWithParser = thisParserParsed.getChildWithParser(SideEffectNameParser.class);
    return tokenOfChildWithParser.getSource().cursorRange().endIndexExclusive.getPosition();
  }

	@TokenExtractor
	public static Token getMethodClause(Token thisParserParsed) {
		return thisParserParsed.getChild(TokenPredicators.hasTag(classMethodOrMethod)); //2
	}
	
  @TokenExtractor
	public static Token getParametersClause(Token thisParserParsed) {
    return thisParserParsed.getChildWithParser(ArgumentsParser.class); //4
	}
  
  @TokenExtractor
	public static MethodAndParameters extract(Token token , TinyExpressionTokens tinyExpressionTokens) {
    
    Token returning = token.getChildFromAstNodes(0);
    Parser parser = ChoiceInterface.choiced(token).parser;
    if(parser instanceof ReturningParser) {
      parser = ChoiceInterface.choiced(returning).parser;
    }
    
    Returning returnParser =  (Returning) parser;
    
    Class<?> returningType = returnParser.returningType(); 
        
	  
	  Token classMethodToken = getMethodClause(token);
	  ClassNameAndIdentifier extract = ((ClassNameAndIdentifierExtractor)classMethodToken.parser)
	    .extractClassNameAndIdentifier(classMethodToken, tinyExpressionTokens);
		
		Token parametersClause = getParametersClause(token);
		
		List<Token> parameterTokens = ArgumentsParser.parameterTokens(extract.getIdentifier(), parametersClause , tinyExpressionTokens);
		
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
            parser instanceof ExclusiveNakedVariableParser ? float.class :
						parser instanceof NumberExpression ? float.class :
						parser instanceof BooleanExpression ? boolean.class :
						parser instanceof StringExpression ? String.class :
						null;
				i++;
			}
		}
	}
}