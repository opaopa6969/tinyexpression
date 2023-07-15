package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.parser.elementary.QuotedParser;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.IfNotExistsParser;
import org.unlaxer.tinyexpression.parser.MethodInvocationParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.SliceParser;
import org.unlaxer.tinyexpression.parser.StringExpression;
import org.unlaxer.tinyexpression.parser.StringExpressionParser;
import org.unlaxer.tinyexpression.parser.StringFactorParser;
import org.unlaxer.tinyexpression.parser.StringIfExpressionParser;
import org.unlaxer.tinyexpression.parser.StringLiteralParser;
import org.unlaxer.tinyexpression.parser.StringMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.StringPlusParser;
import org.unlaxer.tinyexpression.parser.StringSetterParser;
import org.unlaxer.tinyexpression.parser.StringTermParser;
import org.unlaxer.tinyexpression.parser.StringVariableParser;
import org.unlaxer.tinyexpression.parser.ToLowerCaseParser;
import org.unlaxer.tinyexpression.parser.ToUpperCaseParser;
import org.unlaxer.tinyexpression.parser.TrimParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.util.FactoryBoundCache;

public class StringClauseBuilder {
  
  public static class StringCaseExpressionBuilder implements TokenCodeBuilder{

    public static StringCaseExpressionBuilder SINGLETON = new StringCaseExpressionBuilder();

    public void build(SimpleJavaCodeBuilder builder, Token token ,
        TinyExpressionTokens tinyExpressionTokens) {

      List<Token> originalTokens = token.filteredChildren;
      Iterator<Token> iterator = originalTokens.iterator();

      while(iterator.hasNext()){
        Token caseFactor = iterator.next();

        Token booleanExpression = caseFactor.filteredChildren.get(0);
        Token expression = caseFactor.filteredChildren.get(1);
        
//        Token booleanExpression = BooleanCaseFactorParser.getBooleanExpression(caseFactor);
//        Token expression = BooleanCaseFactorParser.getExpression(caseFactor);
        
        BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression , tinyExpressionTokens);
        builder.append(" ? ");
        StringExpressionBuilder.SINGLETON.build(builder, expression , tinyExpressionTokens);
        builder
          .append(":")
          .n();
      }
    }
  }

	public static final StringClauseBuilder SINGLETON = new StringClauseBuilder();

	public ExpressionOrLiteral build(Token token , TinyExpressionTokens tinyExpressionTokens) {

		Parser parser = token.parser;
		
    if (parser instanceof StringExpressionParser) {
      
      parser = token.filteredChildren.get(0).parser;
      if(parser instanceof StringTermParser) {
        
        List<Token> terms= new ArrayList<Token>();
        terms.add(token.filteredChildren.get(0));
        Token successor = token.filteredChildren.get(1);
        List<Token> addings = successor.filteredChildren.stream()
          .map(ChoiceInterface::choiced)
          .filter(_token->_token.parser instanceof StringTermParser)
          .collect(Collectors.toList());
        
        terms.addAll(addings);
        
        Iterator<Token> iterator = terms.iterator();

        StringBuilder builder = new StringBuilder();

        while (iterator.hasNext()) {
          Token term = iterator.next();
          ExpressionOrLiteral build = build(term , tinyExpressionTokens);
          builder.append(build.toString());
          if(iterator.hasNext()) {
            builder.append("+");
          }
        }
        return ExpressionOrLiteral.expressionOf("(" + builder.toString() + ")");
      }
    }
      
    if(parser instanceof StringTermParser) {
      
      token = token.filteredChildren.get(0);
      parser = token.parser;
      
      if(parser instanceof StringFactorParser) {
        token = token.filteredChildren.get(0);
        parser = token.parser;
        
      }
    }
    if (parser instanceof StringPlusParser) {

			Iterator<Token> iterator = token.filteredChildren.iterator();

			StringBuilder builder = new StringBuilder();
			iterator.next();// this is operator
//			Token termToken = iterator.next();
//
//			ExpressionOrLiteral built = build(termToken);
//
//			builder.append(built);

			while (iterator.hasNext()) {
				Token successor = iterator.next();
				ExpressionOrLiteral build = build(successor , tinyExpressionTokens);
				builder.append(build.toString());
				if(iterator.hasNext()) {
					builder.append("+");
				}
			}
			return ExpressionOrLiteral.expressionOf("(" + builder.toString() + ")");

		} else if (parser instanceof SliceParser) {

			Token stringFactorToken = token.filteredChildren.get(1);
			Token slicerToken = token.filteredChildren.get(0);

			ExpressionOrLiteral inner = build(stringFactorToken , tinyExpressionTokens);

			Optional<String> specifier = slicerToken.getToken()
					.map(wrapped -> wrapped.substring(1, wrapped.length() - 1));

			ExpressionOrLiteral evaluate = specifier.map(slicerSpecifier -> 
				ExpressionOrLiteral.expressionOf(
					"new org.unlaxer.util.Slicer("+inner+").pythonian(\""+slicerSpecifier+"\").get()"))
				.orElse(inner);

			return evaluate;

		} else if (parser instanceof StringLiteralParser) {

			Token literalChoiceToken = ChoiceInterface.choiced(token);
			String contents = stringByToken.get(literalChoiceToken);
			return ExpressionOrLiteral.literalOf(contents == null ? "" : contents);

		} else if (parser instanceof NakedVariableParser || parser instanceof StringVariableParser) {
		  
      List<Token> variableDeclarationsTokens = tinyExpressionTokens.getVariableDeclarationTokens();
      
      TypedToken<VariableParser> typed = token.typed(VariableParser.class);
      VariableParser variableParser = typed.getParser();
      
      
			String variableName = variableParser.getVariableName(typed);
			
			SimpleBuilder builder = new SimpleBuilder();
			
	     boolean isMatch =false;
	     for (Token declarationTtoken : variableDeclarationsTokens) {
	       
	       TypedToken<?  extends VariableParser> nakedVariableToken = declarationTtoken.getChildWithParserTyped(NakedVariableParser.class);
	       
	       VariableParser variabvleParser = nakedVariableToken.getParser();
	       String _variableName = variabvleParser.getVariableName(nakedVariableToken);
	       
	       if(_variableName.equals(variableName)) {
	         Optional<Token> setterToken = declarationTtoken.getChildWithParserAsOptional(StringSetterParser.class);
	         if(setterToken.isEmpty()) {
	           continue;
	         }
	         Token _setterToken = setterToken.get();
	         Token expression = _setterToken.getChild(TokenPredicators.parserImplements(ExpressionInterface.class));
	         Optional<Token> ifNotExists = _setterToken.getChildWithParserAsOptional(IfNotExistsParser.class);
	         
	         ExpressionOrLiteral build = build( expression, tinyExpressionTokens);
	         String expseeionString = build.toString();
//	     String expseeionString = expression.getToken().orElseThrow();
	         
	         if(ifNotExists.isPresent()) {
	           
	           builder.append("calculateContext.getString(").w(variableName).append(").orElse("+expseeionString+")");
	         }else {
	           builder.append("calculateContext.setAndGet(").w(variableName).append(","+expseeionString+")");
	         }
	         isMatch = true;
	         break;
	       }
	     }
	     if(false == isMatch) {
	       builder.append("calculateContext.getString(").w(variableName).append(").orElse(\"\")");
	     }
	     
	     return ExpressionOrLiteral.expressionOf(builder.toString());
			
//			return ExpressionOrLiteral.expressionOf(
//				simpleBuilder
//					.append("calculateContext.getString(")
//					.w(variableName)
//					.append(").orElse(\"\")")
//					.toString()
//			);

		} else if (parser instanceof ParenthesesParser) {

			Token parenthesesed = token.filteredChildren.get(0);

			return build(parenthesesed , tinyExpressionTokens);

		} else if (parser instanceof TrimParser) {

			Token parenthesesed = token.filteredChildren.get(0);
			ExpressionOrLiteral evaluate = build(parenthesesed , tinyExpressionTokens);
			return ExpressionOrLiteral.expressionOf(evaluate.toString()+".trim()");

		} else if (parser instanceof ToUpperCaseParser) {

			Token parenthesesed = token.filteredChildren.get(0);
			ExpressionOrLiteral evaluate = build(parenthesesed , tinyExpressionTokens);
			return ExpressionOrLiteral.expressionOf(evaluate.toString()+".toUpperCase()");

		} else if (parser instanceof ToLowerCaseParser) {

			Token parenthesesed = token.filteredChildren.get(0);
			ExpressionOrLiteral evaluate = build(parenthesesed , tinyExpressionTokens);
			return ExpressionOrLiteral.expressionOf(evaluate.toString()+".toLowerCase()");
		} else if(parser instanceof StringIfExpressionParser) {
		  
      Token booleanExpression = IfExpressionParser.getBooleanExpression(token);
      Token factor1 = IfExpressionParser.getThenExpression(token , StringExpression.class , booleanExpression);
      Token factor2 = IfExpressionParser.getElseExpression(token , StringExpression.class , booleanExpression);
      
      ExpressionOrLiteral factor1EOL = build(factor1 , tinyExpressionTokens);
      ExpressionOrLiteral factor2EOL = build(factor2 , tinyExpressionTokens);

      SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
      builder.setKind(Kind.Main);
      
      /*
       * BooleanExpressionOperator.SINGLETON.evaluate(calculateContext, booleanExpression)?
       * factor1: factor2
       */

      builder.append("(");

      BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression , tinyExpressionTokens);

      builder.append(" ? ").n().incTab();
      
      builder.append(factor1EOL.toString());

      builder.append(":").n();

      builder.append(factor2EOL.toString());

      builder.decTab();

      builder.append(")");

      return ExpressionOrLiteral.expressionOf(builder.getBuilder(Kind.Main).toString());
      
		} else if (parser instanceof StringMatchExpressionParser) {

      Token caseExpression = token.filteredChildren.get(0);
      Token defaultCaseFactor = token.filteredChildren.get(1);
      
      SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();

      ExpressionOrLiteral defaultFactor = build(defaultCaseFactor , tinyExpressionTokens);

      builder.setKind(Kind.Main);


      builder.n();
      builder.incTab();

      builder.append("(");

      StringCaseExpressionBuilder.SINGLETON.build(builder, caseExpression , tinyExpressionTokens);
      builder.n();
      builder.append(defaultFactor.toString());

      builder.append(")");
      builder.decTab();
      return ExpressionOrLiteral.expressionOf(builder.getBuilder(Kind.Main).toString());

		}else if(parser instanceof MethodInvocationParser) {
		  
      SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
		  
      MethodInvocationBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);

      return ExpressionOrLiteral.expressionOf(builder.getBuilder(Kind.Main).toString());
		}

		throw new IllegalArgumentException();
	}

	static FactoryBoundCache<Token, String> stringByToken = new FactoryBoundCache<>(QuotedParser::contents);
}