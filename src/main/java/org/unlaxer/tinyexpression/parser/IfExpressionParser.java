package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.function.Predicate;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.SuggestableParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

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
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  
    Parsers parsers = new Parsers(
      Parser.get(IfFuctionNameParser.class),
      Parser.get(LeftParenthesisParser.class),
      Parser.newInstance(BooleanExpressionParser.class).addTag(ExpressionTags.condition.tag()),//2
      Parser.get(RightParenthesisParser.class),
      Parser.get(LeftCurlyBraceParser.class),
   // if(condition){$variable}else{$variable}だった時にどちらかの変数が型指定をする事を求める
      new Choice(
          new Chain(
              Parser.newInstance(strictTypedReturning())
                .addTag(ExpressionTags.returning.tag(),ExpressionTags.thenClause.tag()),
              Parser.get(RightCurlyBraceParser.class),
              Parser.get(()->new WordParser("else")),
              Parser.get(LeftCurlyBraceParser.class),
              Parser.newInstance(nonStrictTypedReturning())
                .addTag(ExpressionTags.returning.tag(),ExpressionTags.elseClause.tag())
          ),
          new Chain(
              Parser.newInstance(nonStrictTypedReturning())
                .addTag(ExpressionTags.returning.tag(),ExpressionTags.thenClause.tag()),
              Parser.get(RightCurlyBraceParser.class),
              Parser.get(()->new WordParser("else")),
              Parser.get(LeftCurlyBraceParser.class),
              Parser.newInstance(strictTypedReturning())
                .addTag(ExpressionTags.returning.tag(),ExpressionTags.elseClause.tag())

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
	
  @TokenExtractor(timings = {Timing.CreateOperatorOperandTree,Timing.UseOperatorOperandTree})
	public static Token getBooleanExpression(Token thisParserParsed) {
		return thisParserParsed.getChild(
		    TokenPredicators.parserImplements(BooleanExpression.class , VariableParser.class)
		);
	}
	
  @TokenExtractor(timings = {Timing.CreateOperatorOperandTree,Timing.UseOperatorOperandTree})
	public static Token getThenExpression(Token thisParserParsed , 
      Class<? extends ExpressionInterface> expressionInterfaceClass , Token conditionToken) {
    Predicate<Token> expressionFilter = 
        TokenPredicators.parserImplements(expressionInterfaceClass, VariableParser.class)
          .and(TokenPredicators.afterToken(conditionToken));
    return thisParserParsed.getChildrenAsList(expressionFilter).get(0);
	}
	
  @TokenExtractor(timings = {Timing.CreateOperatorOperandTree,Timing.UseOperatorOperandTree})
	public static Token getElseExpression(Token thisParserParsed , 
	    Class<? extends ExpressionInterface> expressionInterfaceClass, Token conditionToken) {
	  Predicate<Token> expressionFilter = 
	      TokenPredicators.parserImplements(expressionInterfaceClass, VariableParser.class)
  	      .and(TokenPredicators.afterToken(conditionToken));
		return thisParserParsed.getChildrenAsList(expressionFilter).get(1);
	}
	
}