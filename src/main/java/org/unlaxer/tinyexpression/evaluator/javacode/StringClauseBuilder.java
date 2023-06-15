package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.parser.elementary.QuotedParser;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.SliceParser;
import org.unlaxer.tinyexpression.parser.StringExpressionParser;
import org.unlaxer.tinyexpression.parser.StringFactorParser;
import org.unlaxer.tinyexpression.parser.StringIfExpressionParser;
import org.unlaxer.tinyexpression.parser.StringLiteralParser;
import org.unlaxer.tinyexpression.parser.StringPlusParser;
import org.unlaxer.tinyexpression.parser.StringTermParser;
import org.unlaxer.tinyexpression.parser.StringVariableParser;
import org.unlaxer.tinyexpression.parser.ToLowerCaseParser;
import org.unlaxer.tinyexpression.parser.ToUpperCaseParser;
import org.unlaxer.tinyexpression.parser.TrimParser;
import org.unlaxer.util.FactoryBoundCache;

public class StringClauseBuilder {

	public static final StringClauseBuilder SINGLETON = new StringClauseBuilder();

	public ExpressionOrLiteral build(Token token) {

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
          ExpressionOrLiteral build = build(term);
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
				ExpressionOrLiteral build = build(successor);
				builder.append(build.toString());
				if(iterator.hasNext()) {
					builder.append("+");
				}
			}
			return ExpressionOrLiteral.expressionOf("(" + builder.toString() + ")");

		} else if (parser instanceof SliceParser) {

			Token stringFactorToken = token.filteredChildren.get(1);
			Token slicerToken = token.filteredChildren.get(0);

			ExpressionOrLiteral inner = build(stringFactorToken);

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

			String variableName = parser instanceof NakedVariableParser  ?
			    NakedVariableParser.getVariableName(token):
			    StringVariableParser.getVariableName(token);

			return ExpressionOrLiteral.expressionOf(
				new SimpleBuilder()
					.append("calculateContext.getString(")
					.w(variableName)
					.append(").orElse(\"\")")
					.toString()
			);

		} else if (parser instanceof ParenthesesParser) {

			Token parenthesesed = token.filteredChildren.get(0);

			return build(parenthesesed);

		} else if (parser instanceof TrimParser) {

			Token parenthesesed = token.filteredChildren.get(0);
			ExpressionOrLiteral evaluate = build(parenthesesed);
			return ExpressionOrLiteral.expressionOf(evaluate.toString()+".trim()");

		} else if (parser instanceof ToUpperCaseParser) {

			Token parenthesesed = token.filteredChildren.get(0);
			ExpressionOrLiteral evaluate = build(parenthesesed);
			return ExpressionOrLiteral.expressionOf(evaluate.toString()+".toUpperCase()");

		} else if (parser instanceof ToLowerCaseParser) {

			Token parenthesesed = token.filteredChildren.get(0);
			ExpressionOrLiteral evaluate = build(parenthesesed);
			return ExpressionOrLiteral.expressionOf(evaluate.toString()+".toLowerCase()");
		} else if(parser instanceof StringIfExpressionParser) {
		  
      Token booleanClause = token.filteredChildren.get(0);
      Token factor1 = token.filteredChildren.get(1);
      Token factor2 = token.filteredChildren.get(2);
      
      ExpressionOrLiteral factor1EOL = build(factor1);
      ExpressionOrLiteral factor2EOL = build(factor2);

      SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
      builder.setKind(Kind.Main);
      
      /*
       * BooleanClauseOperator.SINGLETON.evaluate(calculateContext, booleanClause)?
       * factor1: factor2
       */

      builder.append("(");

      BooleanClauseBuilder.SINGLETON.build(builder, booleanClause);

      builder.append(" ? ").n().incTab();
      
      builder.append(factor1EOL.toString());

      builder.append(":").n();

      builder.append(factor2EOL.toString());

      builder.decTab();

      builder.append(")");

      return ExpressionOrLiteral.expressionOf(builder.getBuilder(Kind.Main).toString());
		}
		throw new IllegalArgumentException();
	}

	static FactoryBoundCache<Token, String> stringByToken = new FactoryBoundCache<>(QuotedParser::contents);
}