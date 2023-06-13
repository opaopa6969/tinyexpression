package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.Token.SearchFirst;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.PseudoRootParser;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.BooleanClauseParser;
import org.unlaxer.tinyexpression.parser.BooleanExpression;
import org.unlaxer.tinyexpression.parser.BooleanExpressionOfStringParser;
import org.unlaxer.tinyexpression.parser.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.CaseExpressionParser;
import org.unlaxer.tinyexpression.parser.CaseFactorParser;
import org.unlaxer.tinyexpression.parser.DefaultCaseFactorParser;
import org.unlaxer.tinyexpression.parser.EqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.Expression;
import org.unlaxer.tinyexpression.parser.ExpressionParser;
import org.unlaxer.tinyexpression.parser.FactorOfStringParser;
import org.unlaxer.tinyexpression.parser.FactorParser;
import org.unlaxer.tinyexpression.parser.FalseTokenParser;
import org.unlaxer.tinyexpression.parser.GreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.GreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.InMethodParser;
import org.unlaxer.tinyexpression.parser.InTimeRangeParser;
import org.unlaxer.tinyexpression.parser.IsPresentParser;
import org.unlaxer.tinyexpression.parser.LessExpressionParser;
import org.unlaxer.tinyexpression.parser.LessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.MatchExpressionParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.NotBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.NotEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberParser;
import org.unlaxer.tinyexpression.parser.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParameterChoice;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParameterSuccessor;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedBooleanClauseParser;
import org.unlaxer.tinyexpression.parser.StrictTypedBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedFactorParser;
import org.unlaxer.tinyexpression.parser.StrictTypedStringExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedStringFactorParser;
import org.unlaxer.tinyexpression.parser.StrictTypedStringTermParser;
import org.unlaxer.tinyexpression.parser.StrictTypedTermParser;
import org.unlaxer.tinyexpression.parser.StringContainsParser;
import org.unlaxer.tinyexpression.parser.StringEndsWithParser;
import org.unlaxer.tinyexpression.parser.StringEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.StringExpression;
import org.unlaxer.tinyexpression.parser.StringExpressionParser;
import org.unlaxer.tinyexpression.parser.StringFactorParser;
import org.unlaxer.tinyexpression.parser.StringInParser;
import org.unlaxer.tinyexpression.parser.StringLengthParser;
import org.unlaxer.tinyexpression.parser.StringLiteralParser;
import org.unlaxer.tinyexpression.parser.StringMethodExpressionParser;
import org.unlaxer.tinyexpression.parser.StringMethodParser;
import org.unlaxer.tinyexpression.parser.StringNotEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.StringStartsWithParser;
import org.unlaxer.tinyexpression.parser.StringTermParser;
import org.unlaxer.tinyexpression.parser.StringVariableParser;
import org.unlaxer.tinyexpression.parser.TermParser;
import org.unlaxer.tinyexpression.parser.ToLowerCaseParser;
import org.unlaxer.tinyexpression.parser.ToNumParser;
import org.unlaxer.tinyexpression.parser.ToUpperCaseParser;
import org.unlaxer.tinyexpression.parser.TrimParser;
import org.unlaxer.tinyexpression.parser.TrueTokenParser;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;

public class ASTCreator implements UnaryOperator<Token>{
	
	public static ASTCreator SINGLETON = new ASTCreator();

	@Override
	public Token apply(Token token) {
		
		Parser parser = token.parser;
		
		if(parser instanceof SideEffectExpressionParameterSuccessor) {
		  token = SideEffectExpressionParameterSuccessor.extractParameter(token);
      return apply(token);
		}
		
		if(parser instanceof SideEffectExpressionParameterChoice) {
		  token = ChoiceInterface.choiced(token);
		  return apply(token);
    }
		
		
		if(
      parser instanceof StrictTypedExpressionParser || 
		  parser instanceof ExpressionParser || 
      parser instanceof StrictTypedTermParser ||
      parser instanceof TermParser ||
			parser instanceof StrictTypedBooleanClauseParser ||
			parser instanceof BooleanClauseParser ||
      parser instanceof StrictTypedStringExpressionParser || 
			parser instanceof StringExpressionParser
			) {
			
			List<Token> originalTokens = token.filteredChildren;
			Iterator<Token> iterator = originalTokens.iterator();
			
			Token left = apply(iterator.next());
			
			Token lastOpearatorAndOperands = left;
			
			while(iterator.hasNext()){
				Token operator = iterator.next();
				Token right = apply(iterator.next());
				lastOpearatorAndOperands = 
					operator.newCreatesOf(operator , lastOpearatorAndOperands , right);
			}
			return lastOpearatorAndOperands;
			

		}else if(
		    parser instanceof StrictTypedFactorParser ||
		    parser instanceof FactorParser
		    ) {
			
			return factor(token);
			
		}else if(parser instanceof CaseExpressionParser){
			
			List<Token> casefactors = token.filteredChildren.stream()
				.filter(child-> child.parser instanceof CaseFactorParser)
				.map(this::apply)
				.collect(Collectors.toList());
			return token.newCreatesOf(casefactors);
			
		}else if(parser instanceof CaseFactorParser){
			
			return token.newCreatesOf(
				apply(CaseFactorParser.getBooleanClause(token)),
				apply(CaseFactorParser.getExpression(token))
			);
			
		}else if(parser instanceof DefaultCaseFactorParser){
			
			return apply(DefaultCaseFactorParser.getExpression(token));

		}else if(parser instanceof BooleanExpressionParser || 
		    parser instanceof StrictTypedBooleanExpressionParser) {
			
			return booleanExpression(token);
			
		}else if(
		    parser instanceof StrictTypedStringTermParser||
		    parser instanceof StringTermParser
		    ) {

			List<Token> originalTokens = token.filteredChildren;
			Iterator<Token> iterator = originalTokens.iterator();
			
			Token left = apply(iterator.next());
			
			Token lastOpearatorAndOperands = left;
			
			while(iterator.hasNext()){
				Token operator = iterator.next();
				lastOpearatorAndOperands = 
					operator.newCreatesOf(operator , lastOpearatorAndOperands);
			}
			return lastOpearatorAndOperands;
			
		}else if(
        parser instanceof StrictTypedStringFactorParser||
        parser instanceof StringFactorParser
		    ) {
			
			return stringFactor(token);
		
		}else if(parser instanceof PseudoRootParser) {
			
			return token;
			
		}

		
		throw new IllegalArgumentException();
			
	}

	private Token stringFactor(Token token) {
		Token operator = ChoiceInterface.choiced(token);
		
		if(operator.parser instanceof StringLiteralParser){
			
			return operator;
			
		}else if(operator.parser instanceof StringVariableParser|| 
        operator.parser instanceof NakedVariableParser ){
			
			return operator;
			
		}else if(operator.parser instanceof ParenthesesParser){
			
			return apply(((ParenthesesParser)operator.parser).getInnerParserParsed(operator));

		}else if(operator.parser instanceof TrimParser){
			
			return operator.newCreatesOf(apply(TrimParser.getInnerParserParsed(operator)));

		}else if(operator.parser instanceof ToUpperCaseParser){
			
			return operator.newCreatesOf(apply(ToUpperCaseParser.getInnerParserParsed(operator)));

		}else if(operator.parser instanceof ToLowerCaseParser){
			
			return operator.newCreatesOf(apply(ToLowerCaseParser.getInnerParserParsed(operator)));

		}
		throw new IllegalArgumentException();
	}

	private Token factor(Token token) {
		
		Token operator = ChoiceInterface.choiced(token);
		
		if(operator.parser instanceof NumberParser){
			
			return clearChildren(operator);
			
		}else if(operator.parser instanceof NakedVariableParser ){
			
			return clearChildren(operator);
			
		}else if(operator.parser instanceof NumberVariableParser){
		  
		  return operator;
			
		}else if(operator.parser instanceof IfExpressionParser){
			
			return operator.newCreatesOf(
				apply(IfExpressionParser.getBooleanClause(operator)),
				apply(IfExpressionParser.getThenExpression(operator)),
				apply(IfExpressionParser.getElseExpression(operator))
			);
			
		}else if(operator.parser instanceof MatchExpressionParser){
			
			return operator.newCreatesOf(
				apply(MatchExpressionParser.getCaseExpression(operator)),
				apply(MatchExpressionParser.getDefaultExpression(operator))
			);
			
		}else if(operator.parser instanceof ParenthesesParser){
			
			return apply(((ParenthesesParser)operator.parser).getInnerParserParsed(operator));
			
		}else if(operator.parser instanceof SinParser){
			
			return operator.newCreatesOf(apply(SinParser.getExpression(operator)));
			
		}else if(operator.parser instanceof CosParser){
			
			return operator.newCreatesOf(apply(CosParser.getExpression(operator)));
			
		}else if(operator.parser instanceof TanParser){
			
			return operator.newCreatesOf(apply(TanParser.getExpression(operator)));
			
		}else if(operator.parser instanceof SquareRootParser){
			
			return operator.newCreatesOf(apply(SquareRootParser.getExpression(operator)));
			
		}else if(operator.parser instanceof MinParser){
			
			return operator.newCreatesOf(
				apply(MinParser.getLeftExpression(operator)),
				apply(MinParser.getRightExpression(operator))
			);

		}else if(operator.parser instanceof MaxParser){
			
			return operator.newCreatesOf(
				apply(MaxParser.getLeftExpression(operator)),
				apply(MaxParser.getRightExpression(operator))
			);

		}else if(operator.parser instanceof RandomParser){
			
			return operator;

		}else if(operator.parser instanceof FactorOfStringParser){
			
			Token choiceToken = operator.filteredChildren.get(0);
			
			if(choiceToken.parser instanceof StringLengthParser) {
				
				return choiceToken.newCreatesOf(apply(choiceToken.filteredChildren.get(2)));
				
//			}else if(choiceToken.parser instanceof StringIndexOfParser) {
//				
//				return choiceToken;
			}
		} else if (operator.parser instanceof ToNumParser) {
			return operator.newCreatesOf(
					apply(ToNumParser.getLeftExpression(operator)),
					apply(ToNumParser.getRightExpression(operator))
			);
			
		}else if(operator.parser instanceof SideEffectExpressionParser){
		  
		  Token extractParameters = extractParameters(SideEffectExpressionParser.getParametersClause(operator));
		  Optional<Token> firstParameter = extractFirstParmeter(extractParameters);
		  Token returningClause = SideEffectExpressionParser.getReturningClause(operator,firstParameter);
      Token returning = apply(extractReturning(returningClause));
			
			return operator.newCreatesOf(
//			    returning causeをtoken化する。
//			    ただし、optionalなのでemptyの場合はreturn as number default 1stParameter　 にする
			    returning,
			    SideEffectExpressionParser.getMethodClause(operator),
			    extractParameters
			    
			);
		}
		throw new IllegalArgumentException();
	}
	
	private Optional<Token> extractFirstParmeter(Token extractParameters) {
	  List<Token> filteredChildren = extractParameters.filteredChildren;
	  return filteredChildren.isEmpty() ?
	      Optional.empty():
	      Optional.of(filteredChildren.get(0));
  }

  private Token extractReturning(Token returningClause) {
//    ExpressionParserかBooleanClauseParserかStringExpressionParserを探す。
//    ただし幅優先で探さなければならないのでTokenにdepth/breadthのどちらでlistを作るかを指定するものを追加する。
    
    List<Token> flatten = returningClause.flatten(SearchFirst.Breadth);
    Token expressionToken = flatten.stream()
      .filter(token->{
        return (token.parser instanceof Expression ||
            token.parser instanceof BooleanExpression ||
            token.parser instanceof StringExpression
            );
      })
      .findFirst().orElseThrow();

    return expressionToken;
  }

  Token extractParameters(Token sideEffectExpressionParameterToken) {
	  
	  List<Token> appliedChildren = sideEffectExpressionParameterToken.filteredChildren.stream()
	      .filter(token-> false == token.parser instanceof CommaParser)
	      .map(this::apply)
	      .collect(Collectors.toList());
	  
	  return sideEffectExpressionParameterToken.newCreatesOf(appliedChildren);
	}

	private Token booleanExpression(Token token) {
		
		Token operator = ChoiceInterface.choiced(token);
		Parser parser = operator.parser;
		
		if(parser instanceof TrueTokenParser ||
			parser instanceof FalseTokenParser) {
			return operator;
			
		}else if(parser instanceof NotBooleanExpressionParser) {
			
			Token booleanClause = NotBooleanExpressionParser.getBooleanClause(operator);
			return operator.newCreatesOf(apply(booleanClause));
			
		}else if(parser instanceof BooleanVariableParser || 
		    parser instanceof NakedVariableParser) {
			
			return operator;
			
			
		}else if(parser instanceof ParenthesesParser) {

			return apply(((ParenthesesParser)parser).getInnerParserParsed(operator));
			
		}else if(parser instanceof IsPresentParser) {

			return operator.newCreatesOf(IsPresentParser.getVariable(operator));

		} else if(parser instanceof InTimeRangeParser) {
			return operator.newCreatesOf(
					apply(InTimeRangeParser.getLeftExpression(operator)),
					apply(InTimeRangeParser.getRightExpression(operator))
			);

		}else if(parser instanceof EqualEqualExpressionParser) {
			
			return operator.newCreatesOf(
				apply(EqualEqualExpressionParser.getLeftExpression(operator)),
				apply(EqualEqualExpressionParser.getRightExpression(operator))
			);

		}else if(parser instanceof NotEqualExpressionParser) {
			
			return operator.newCreatesOf(
				apply(NotEqualExpressionParser.getLeftExpression(operator)),
				apply(NotEqualExpressionParser.getRightExpression(operator))
			);

		}else if(parser instanceof GreaterOrEqualExpressionParser) {
			
			return operator.newCreatesOf(
				apply(GreaterOrEqualExpressionParser.getLeftExpression(operator)),
				apply(GreaterOrEqualExpressionParser.getRightExpression(operator))
			);

		}else if(parser instanceof LessOrEqualExpressionParser) {
			
			return operator.newCreatesOf(
				apply(LessOrEqualExpressionParser.getLeftExpression(operator)),
				apply(LessOrEqualExpressionParser.getRightExpression(operator))
			);

		}else if(parser instanceof GreaterExpressionParser) {
			
			return operator.newCreatesOf(
				apply(GreaterExpressionParser.getLeftExpression(operator)),
				apply(GreaterExpressionParser.getRightExpression(operator))
			);

		}else if(parser instanceof LessExpressionParser) {
			
			return operator.newCreatesOf(
				apply(LessExpressionParser.getLeftExpression(operator)),
				apply(LessExpressionParser.getRightExpression(operator))
			);

		}else if(parser instanceof BooleanExpressionOfStringParser) {
			
			Token operatorWithString = ChoiceInterface.choiced(operator);
			
			if(operatorWithString.parser instanceof StringEqualsExpressionParser) {
				
				return operatorWithString.newCreatesOf(
					apply(StringEqualsExpressionParser.getLeftExpression(operatorWithString)),
					apply(StringEqualsExpressionParser.getRightExpression(operatorWithString))
				);
				
			}else if(operatorWithString.parser instanceof StringNotEqualsExpressionParser) {
				
				return operatorWithString.newCreatesOf(
					apply(StringNotEqualsExpressionParser.getLeftExpression(operatorWithString)),
					apply(StringNotEqualsExpressionParser.getRightExpression(operatorWithString))
				);
				
			}else if(
					operatorWithString.parser instanceof StringStartsWithParser||
					operatorWithString.parser instanceof StringEndsWithParser||
					operatorWithString.parser instanceof StringContainsParser
			) {

				Token leftExpression = StringMethodExpressionParser.getLeftExpression(operatorWithString);
				Token argument = StringMethodParser.getStringExpressions(StringMethodExpressionParser.getMethod(operatorWithString));
				return operatorWithString.newCreatesOf(
					apply(leftExpression),
					apply(argument)
				);
				
			}else if(operatorWithString.parser instanceof StringInParser) {
				
				
				List<Token> stringExpressions = new ArrayList<>();
				Token leftExpression = StringInParser.getLeftExpression(operatorWithString);
				Token inMethod = StringInParser.getInMethod(operatorWithString);
				
				stringExpressions.add(leftExpression);
				stringExpressions.addAll(getStringExpressions(inMethod));
				
				List<Token> appliedExpressions = stringExpressions.stream()
					.map(this::apply)
					.collect(Collectors.toList());
			
				return operatorWithString.newCreatesOf(appliedExpressions);
			}
		}
		throw new IllegalArgumentException();
	}
	
	Token clearChildren(Token token) {
		token.filteredChildren.clear();
		return token;
	}
	
	static List<Token> getStringExpressions(Token inMethod){
		
		Token stringExpressions = InMethodParser.getStringExpressions(inMethod);
		List<Token> expressions = stringExpressions.filteredChildren.stream()
			.filter(token->token.parser instanceof StringExpressionParser)
			.collect(Collectors.toList());
		return expressions;
	}
}