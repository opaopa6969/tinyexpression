package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringExpression;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class SideEffectStringExpressionParameterParser extends JavaStyleDelimitedLazyChain {

	private static final long serialVersionUID = -1540940685498628668L;

	public SideEffectStringExpressionParameterParser() {
		super();
	}

	public SideEffectStringExpressionParameterParser(Name name) {
		super(name);
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
              Parser.get(BooleanExpressionParser.class),
              Parser.get(StringExpressionParser.class),
              Parser.get(NumberExpressionParser.class)
            )
          ) 
        )
      );
	}

	@TokenExtractor
	public List<Token> parameterTokens(Token sideEffectExpressionParameterParserToken){
		
		if(false == sideEffectExpressionParameterParserToken.parser instanceof SideEffectStringExpressionParameterParser) {
			throw new IllegalArgumentException("token is invalid");
		}
		
		return sideEffectExpressionParameterParserToken.filteredChildren.stream()
			.filter(token->{
				Parser parser = token.parser;
				return parser instanceof NumberExpression ||
						parser instanceof BooleanExpression ||
						parser instanceof StringExpression;
			}).collect(Collectors.toList());
	}
}