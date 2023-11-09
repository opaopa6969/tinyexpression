package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.bool.BooleanFactorParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringExpression;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class SideEffectStringToBooleanExpressionParameterParser extends JavaStyleDelimitedLazyChain {

	public SideEffectStringToBooleanExpressionParameterParser() {
		super();
	}

	public SideEffectStringToBooleanExpressionParameterParser(Name name) {
		super(name);
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }
	
	@Override
	public Parsers getLazyParsers() {
	  return
      new Parsers(
          Parser.get(StringExpressionParser.class),
          new ZeroOrMore(
            new WhiteSpaceDelimitedChain(
              Parser.get(CommaParser.class),
              new Choice(
                Parser.get(StringExpressionParser.class),
                Parser.get(BooleanFactorParser.class),
                Parser.get(NumberExpressionParser.class)
              )   
            )
          ) 
        );
	}

	@TokenExtractor
	public List<Token> parameterTokens(Token sideEffectExpressionParameterParserToken) {
		if (false == sideEffectExpressionParameterParserToken.parser instanceof SideEffectStringToBooleanExpressionParameterParser) {
			throw new IllegalArgumentException("token is invalid");
		}
		
		return sideEffectExpressionParameterParserToken.filteredChildren.stream()
			.filter(token -> {
				Parser parser = token.parser;
				return parser instanceof NumberExpression ||
						parser instanceof BooleanExpression ||
						parser instanceof StringExpression;
			}).collect(Collectors.toList());
	}

}
