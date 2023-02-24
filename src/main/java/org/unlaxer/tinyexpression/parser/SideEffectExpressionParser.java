package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser.ClassNameAndIdentifier;

public class SideEffectExpressionParser extends WhiteSpaceDelimitedLazyChain implements Expression{
	
	private static final long serialVersionUID = 8228933717392969866L;
	
	
	public SideEffectExpressionParser() {
		super();
	}
	
	public static class SideEffectNameParser extends LazyChoice{

    private static final long serialVersionUID = -5885382161035099103L;
    
    List<Parser> parsers;


    public SideEffectNameParser() {
      super();
    }
    
    @Override
    public void initialize() {
      parsers = 
        new Parsers(
          Parser.get(SideEffectName1Parser.class),
          Parser.get(SideEffectName2Parser.class)
        );
    }

    @Override
    public List<Parser> getLazyParsers() {
      return parsers;
    }
  }
	
	public static class SideEffectName1Parser extends WhiteSpaceDelimitedLazyChain{

		private static final long serialVersionUID = -5885382161035099103L;
		
		List<Parser> parsers;


		public SideEffectName1Parser() {
			super();
		}
		
		@Override
		public void initialize() {
			parsers = 
				new Parsers(
					Parser.get(()->new WordParser("with")),
					Parser.get(()->new WordParser("side")),
					Parser.get(()->new WordParser("effect"))
				);
		}

		@Override
		public List<Parser> getLazyParsers() {
			return parsers;
		}
	}
	
	public static class SideEffectName2Parser extends WordParser{

    private static final long serialVersionUID = -5885382161035099103L;
    
    List<Parser> parsers;


    public SideEffectName2Parser() {
      super("external");
    }
  }
	
	
	
	List<Parser> parsers;

	
	@Override
	public void initialize() {
		parsers = 
			new Parsers(
				Parser.get(SideEffectNameParser.class),
				Parser.get(()->new WordParser(":")),
				Parser.get(JavaClassMethodParser.class),//2
				Parser.get(LeftParenthesisParser.class),
				Parser.get(SideEffectExpressionParameterParser.class),//4
				Parser.get(RightParenthesisParser.class)
			);
	}
	
	


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
	
	public static Token getMethodClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(2);
	}
	
	public static Token getParametersClause(Token thisParserParsed) {
		return thisParserParsed.filteredChildren.get(4);
	}
	
	public static MethodAndParameters extract(Token token) {
		
		Token classMethod = token.filteredChildren.get(0);//TODO token.getChild(JavaClassMethodParser.class);
		
		ClassNameAndIdentifier extract = Parser.get(JavaClassMethodParser.class).extract(classMethod);
		
		Token parameter = token.filteredChildren.get(1);
		
		SideEffectExpressionParameterParser sideEffectExpressionParameterParser = 
				Parser.get(SideEffectExpressionParameterParser.class);
		
		List<Token> parameterTokens = sideEffectExpressionParameterParser.parameterTokens(parameter);
		
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
			parameterTypes[1] = float.class;
			int i = 2;
			for (Token token : parameterTokens) {
				Parser parser = token.parser;
				parameterTypes[i] =
				    parser instanceof VariableParser ? VariableParser.class :
						parser instanceof Expression ? float.class :
						parser instanceof BooleanExpression ? boolean.class :
						parser instanceof StringExpression ? String.class :
						null;
				i++;
			}
		}
	}
}