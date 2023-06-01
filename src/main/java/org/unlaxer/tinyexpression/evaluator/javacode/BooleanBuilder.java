package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculator.CodeBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.validator.ParserValuesValidator;
import org.unlaxer.tinyexpression.parser.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.EqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.FalseTokenParser;
import org.unlaxer.tinyexpression.parser.GreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.GreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.InTimeRangeParser;
import org.unlaxer.tinyexpression.parser.IsPresentParser;
import org.unlaxer.tinyexpression.parser.LessExpressionParser;
import org.unlaxer.tinyexpression.parser.LessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.NotBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.NotEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.SideEffectBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.SideEffectStringToBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.StringContainsParser;
import org.unlaxer.tinyexpression.parser.StringEndsWithParser;
import org.unlaxer.tinyexpression.parser.StringEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.StringInParser;
import org.unlaxer.tinyexpression.parser.StringNotEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.StringStartsWithParser;
import org.unlaxer.tinyexpression.parser.TrueTokenParser;

@SuppressWarnings("deprecation")
public class BooleanBuilder implements CodeBuilder {
	
	public static final BooleanBuilder SINGLETON = new BooleanBuilder();
	private ParserValuesValidator parserValuesValidator = new ParserValuesValidator();


	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token) {
		Parser parser = token.parser;
		
		if(parser instanceof NotBooleanExpressionParser) {
			
			builder.append("(false ==(");
			build(builder , token.filteredChildren.get(0));
			builder.append("))");
				
		}else if(parser instanceof ParenthesesParser){
		
			Token parenthesesed = ParenthesesParser.getParenthesesed(token);
			builder.append("(");
			build(builder , parenthesesed);
			builder.append(")");
			
		
		}else if(parser instanceof IsPresentParser){
			
			String variableName = token.tokenString.get().substring(1);

			builder.append("calculateContext.isExists(").w(variableName).append(")");

		} else if (parser instanceof InTimeRangeParser) {
			String fromHour = token.filteredChildren.get(0).tokenString.get();
			String toHour= token.filteredChildren.get(1).tokenString.get();

			parserValuesValidator.validateTimeRangeValues(fromHour, toHour);
			builder.append("org.unlaxer.tinyexpression.function.EmbeddedFunction.inTimeRange(calculateContext,").append(fromHour).append("f,")
					.append(toHour).append("f)");
					
		}else if(parser instanceof BooleanVariableParser) {
		  
			String variableName = BooleanVariableParser.getVariableName(token);
			builder.append("calculateContext.getBoolean(").w(variableName).append(").orElse(false)");
			
    }else if(parser instanceof NakedVariableParser) {
      
      String variableName = NakedVariableParser.getVariableName(token);
      builder.append("calculateContext.getBoolean(").w(variableName).append(").orElse(false)");
      
		}else if(parser instanceof TrueTokenParser){
			
			builder.append("true");
			
		}else if(parser instanceof FalseTokenParser){
			
			builder.append("false");
			
		}else if(
			parser instanceof EqualEqualExpressionParser ||
			parser instanceof NotEqualExpressionParser ||
			parser instanceof GreaterOrEqualExpressionParser ||
			parser instanceof LessOrEqualExpressionParser ||
			parser instanceof GreaterExpressionParser ||
			parser instanceof LessExpressionParser
		){
			BinaryConditionBuilder.SINGLETON.build(builder, token);
			
		}else if (parser instanceof StringEqualsExpressionParser) {

			StringBooleanEqualClauseBuilder.SINGLETON.build(builder, token);

		} else if (token.parser instanceof StringNotEqualsExpressionParser) {

			StringBooleanNotEqualClauseBuilder.SINGLETON.build(builder, token);

		}else if(
			parser instanceof StringStartsWithParser||
			parser instanceof StringEndsWithParser||
			parser instanceof StringContainsParser
		){
			
			StringMethodClauseBuilder.SINGLETON.build(builder, token);
			
		} else if (token.parser instanceof StringInParser) {

			StringInBooleanClauseBuilder.SINGLETON.build(builder, token);

		} else if (token.parser instanceof SideEffectBooleanExpressionParser) {
			
			SideEffectBooleanExpressionBuilder.SINGLETON.build(builder , token.filteredChildren.get(0));
			
		} else if (token.parser instanceof SideEffectStringToBooleanExpressionParser) {
			
			SideEffectStringToBooleanExpressionBuilder.SINGLETON.build(builder , token.filteredChildren.get(0));
			
		}else {
			throw new IllegalArgumentException();
		}
	}
}