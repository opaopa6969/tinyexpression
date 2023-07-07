package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.TokenEffecterWithMatcher;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclaration;
import org.unlaxer.util.annotation.TokenExtractor;

public class ArgumentsParser extends JavaStyleDelimitedLazyChain {

	private static final long serialVersionUID = -1540940685498628668L;

	public ArgumentsParser() {
		super();
	}

	public ArgumentsParser(Name name) {
		super(name);
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(ArgumentChoiceParser.class),
        new ZeroOrMore(
            Parser.get(ArgumentSuccessorParser.class)
        )
      );
	}
	
	@TokenExtractor
	public List<Token> parameterTokens(Token sideEffectExpressionParameterParserToken,TinyExpressionTokens tinyExpressionTokens){
		
		if(false == sideEffectExpressionParameterParserToken.parser instanceof ArgumentsParser) {
			throw new IllegalArgumentException("token is invalid");
		}
		
		//ExclusiveNakedVariableParserのtokenをVariableDeclarationで定義されいていて型定義があれば型解決を行う
		sideEffectExpressionParameterParserToken = 
		    sideEffectExpressionParameterParserToken.newCreatesOf(
		        new TokenEffecterWithMatcher(TokenPredicators.parsers(ExclusiveNakedVariableParser.class),
		        _token->{
		          String variableName = ExclusiveNakedVariableParser.get().getVariableName(_token);
		          Optional<Token> matchedVariableDeclaration = tinyExpressionTokens.matchedVariableDeclaration(variableName);
		          if(matchedVariableDeclaration.isEmpty()) {
		            return _token;
		          }
		          Token token = matchedVariableDeclaration.get();
		          
		          VariableDeclaration parser = token.getParser(VariableDeclaration.class);
		          Optional<VariableType> type = parser.type();
		          
		          if(type.isEmpty()) {
		            return token;
		          }
		         
		          
		          VariableType variableType = type.get();
		          
		          Parser typeParser = variableType.isNumber() ?
		              Parser.get(NumberVariableParser.class):
		                variableType.isString() ?
		                    Parser.get(StringVariableParser.class):
	                      Parser.get(BooleanVariableParser.class);
		          
		          Token newWithReplacedParser = token.newWithReplace(typeParser);
		          
		          return newWithReplacedParser;
		        }));
		
		
		return sideEffectExpressionParameterParserToken.filteredChildren.stream()
			.filter(token->{
			  
			  
				Parser parser = token.parser;
				return parser instanceof NumberExpression ||
						parser instanceof BooleanExpression ||
						parser instanceof StringExpression ||
						parser instanceof ExclusiveNakedVariableParser;// to number;
			}).collect(Collectors.toList());
			
	}
}