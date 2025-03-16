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
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpression;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpressionParser;
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
	public org.unlaxer.parser.Parsers getLazyParsers() {
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