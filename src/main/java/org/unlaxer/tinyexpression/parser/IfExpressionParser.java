package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.function.Predicate;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class IfExpressionParser extends JavaStyleDelimitedLazyChain {
	
	private static final long serialVersionUID = 8228933717392969866L;
	
	
	public IfExpressionParser() {
		super();
	}
	
	public static class IfFuctionNameParser extends SuggestableParser{

		private static final long serialVersionUID = -6045428101193616423L;

		public IfFuctionNameParser() {
			super(true, "if");
		}
		
		@Override
		public String getSuggestString(String matchedString) {
			return "(".concat(matchedString).concat("){ }else{ }");
		}
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  
    Parsers parsers = new Parsers(
      Parser.get(IfFuctionNameParser.class),
      Parser.get(LeftParenthesisParser.class),
      Parser.get(BooleanClauseParser.class),//2
      Parser.get(RightParenthesisParser.class),
      Parser.get(LeftCurlyBraceParser.class),
   // if(condition){$variable}else{$variable}だった時にどちらかの変数が型指定をする事を求める
      new Choice(
          new Chain(
              Parser.get(strictTypedReturning()),
              Parser.get(RightCurlyBraceParser.class),
              Parser.get(()->new WordParser("else")),
              Parser.get(LeftCurlyBraceParser.class),
              Parser.get(nonStrictTypedReturning())
          ),
          new Chain(
              Parser.get(nonStrictTypedReturning()),
              Parser.get(RightCurlyBraceParser.class),
              Parser.get(()->new WordParser("else")),
              Parser.get(LeftCurlyBraceParser.class),
              Parser.get(strictTypedReturning())
          )
      ),
      Parser.get(RightCurlyBraceParser.class)
    );
    
    return parsers;
	}

	/* 
	 */
	public abstract Class<? extends Parser> strictTypedReturning(); 
  public abstract Class<? extends Parser> nonStrictTypedReturning(); 
	
	public static Token getBooleanClause(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(BooleanClauseParser.class);//2
	}
	
	public static Token getThenExpression(Token thisParserParsed , 
      Class<? extends ExpressionInterface> expressionInterfaceClass) {
    Predicate<Token> expressionFilter = Token.parserImplements(expressionInterfaceClass);
    return thisParserParsed.getChildrenAsList(expressionFilter).get(0);
	}
	
	public static Token getElseExpression(Token thisParserParsed , 
	    Class<? extends ExpressionInterface> expressionInterfaceClass) {
	  Predicate<Token> expressionFilter = Token.parserImplements(expressionInterfaceClass);
		return thisParserParsed.getChildrenAsList(expressionFilter).get(1);
	}
	
}