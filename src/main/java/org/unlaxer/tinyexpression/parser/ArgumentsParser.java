package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.TokenEffecterWithMatcher;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
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
	public static List<Token> parameterTokens(String methodName, Token argumentsToken,TinyExpressionTokens tinyExpressionTokens){
		
		if(false == argumentsToken.parser instanceof ArgumentsParser) {
			throw new IllegalArgumentException("token is invalid");
		}
		
		//ExclusiveNakedVariableParserのtokenをVariableDeclarationで定義されいていて型定義があれば型解決を行う
		argumentsToken = 
		    argumentsToken.newCreatesOf(
		        new TokenEffecterWithMatcher(TokenPredicators.parsers(ExclusiveNakedVariableParser.class),
		        _token->{
		          TypedToken<ExclusiveNakedVariableParser> typedToken = 
		              _token.typed(ExclusiveNakedVariableParser.class);
		          
		          String variableName = ExclusiveNakedVariableParser.get().getVariableName(typedToken);
		          Optional<Token> matchedVariableDeclaration = tinyExpressionTokens.matchedVariableDeclaration(variableName);
		          Optional<ExpressionType> type = Optional.empty();
		          if(matchedVariableDeclaration.isPresent()) {
		            
		            Token token = matchedVariableDeclaration.get();
		            
		            VariableDeclaration parser = token.getParser(VariableDeclaration.class);
		            type = parser.type();
		          }
		          if(type.isEmpty()) {
		            
		            Optional<Token> methodToken = tinyExpressionTokens.getMethodToken(methodName);
		            if(methodToken.isPresent()) {
		              Token _methodToken = methodToken.get();
		              
		              Optional<Token> variableParserToken = _methodToken.flatten().stream()
		                .filter(TokenPredicators.parserImplements(VariableParser.class))
		                .filter(token->{
		                  TypedToken<VariableParser> typed = token.typed(VariableParser.class);
		                  VariableParser parser = typed.getParser();
		                  String _variableName = parser.getVariableName(typed);
		                  
		                  return variableName.equals(_variableName);
		                }).findFirst();
		              
		              type = variableParserToken.flatMap(token->{
                    VariableParser parser = token.getParser(VariableParser.class);
                    Optional<ExpressionType> _type = parser.typeAsOptional();
		                return _type;
		              });
		            }
		          }
		            
		          if(type.isEmpty()) {
		            return _token;
		          }
		         
		          ExpressionType variableType = type.get();
		          
		          RootVariableParser typeParser = variableType.isNumber() ?
		              Parser.get(NumberVariableParser.class):
		                variableType.isString() ?
		                    Parser.get(StringVariableParser.class):
	                      Parser.get(BooleanVariableParser.class);
		          
		          Token newWithTypedParser = typeParser.newWithTypedParser(typedToken);
		          return newWithTypedParser;
		        }));
		
		
		return argumentsToken.filteredChildren.stream()
			.filter(token->{
			  
			  
				Parser parser = token.parser;
				return parser instanceof NumberExpression ||
						parser instanceof BooleanExpression ||
						parser instanceof StringExpression ||
						parser instanceof ExclusiveNakedVariableParser;// to number;
			}).collect(Collectors.toList());
			
	}
}