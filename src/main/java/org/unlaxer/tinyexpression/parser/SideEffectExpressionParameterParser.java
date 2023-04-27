package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.CommaParser;

public class SideEffectExpressionParameterParser extends WhiteSpaceDelimitedLazyChain {

	private static final long serialVersionUID = -1540940685498628668L;

	public SideEffectExpressionParameterParser() {
		super();
	}

	public SideEffectExpressionParameterParser(Name name) {
		super(name);
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(ExpressionParser.class),
        new ZeroOrMore(
          new WhiteSpaceDelimitedChain(
            Parser.get(CommaParser.class),
            new Choice(
              Parser.get(BooleanClauseParser.class),
              Parser.get(StringExpressionParser.class),
              Parser.get(ExpressionParser.class)
            )
          ) 
        )
      );
	}
	
	public List<Token> parameterTokens(Token sideEffectExpressionParameterParserToken){
		
		if(false == sideEffectExpressionParameterParserToken.parser instanceof SideEffectExpressionParameterParser) {
			throw new IllegalArgumentException("token is invalid");
		}
		
		return sideEffectExpressionParameterParserToken.filteredChildren.stream()
			.filter(token->{
				Parser parser = token.parser;
				return parser instanceof Expression ||
						parser instanceof BooleanExpression ||
						parser instanceof StringExpression;
			}).collect(Collectors.toList());
			
	}
}