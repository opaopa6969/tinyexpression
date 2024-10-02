package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.evaluator.javacode.validator.ParserValuesValidator;
import org.unlaxer.tinyexpression.parser.BooleanExpression;
import org.unlaxer.tinyexpression.parser.BooleanIfExpressionParser;
import org.unlaxer.tinyexpression.parser.BooleanMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.BooleanSetterParser;
import org.unlaxer.tinyexpression.parser.BooleanSideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.FalseTokenParser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.InTimeRangeParser;
import org.unlaxer.tinyexpression.parser.IsPresentParser;
import org.unlaxer.tinyexpression.parser.MethodInvocationParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.NotBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberEqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberGreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberGreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberLessExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberLessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberNotEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.StringEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.StringMultipleParameterPredicator;
import org.unlaxer.tinyexpression.parser.StringNotEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.TrueTokenParser;
import org.unlaxer.tinyexpression.parser.VariableParser;

public class BooleanBuilder implements TokenCodeBuilder {
	
  public static class BooleanCaseExpressionBuilder implements TokenCodeBuilder{

    public static BooleanCaseExpressionBuilder SINGLETON = new BooleanCaseExpressionBuilder();

    public void build(SimpleJavaCodeBuilder builder, Token token,
        TinyExpressionTokens tinyExpressionTokens) {

      List<Token> originalTokens = token.filteredChildren;
      Iterator<Token> iterator = originalTokens.iterator();

      while(iterator.hasNext()){
        Token caseFactor = iterator.next();

        Token booleanExpression = caseFactor.filteredChildren.get(0);
        Token expression = caseFactor.filteredChildren.get(1);
        
//        Token booleanExpression = BooleanCaseFactorParser.getBooleanExpression(caseFactor);
//        Token expression = BooleanCaseFactorParser.getExpression(caseFactor);
        
        BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression ,
            tinyExpressionTokens);
        builder.append(" ? ");
        BooleanExpressionBuilder.SINGLETON.build(builder, expression , 
            tinyExpressionTokens);
        builder
          .append(":")
          .n();
      }
    }
  }
  
	public static final BooleanBuilder SINGLETON = new BooleanBuilder();
	private ParserValuesValidator parserValuesValidator = new ParserValuesValidator();


	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token, 
	    TinyExpressionTokens tinyExpressionTokens) {
		Parser parser = token.parser;
		
		if(parser instanceof NotBooleanExpressionParser) {
			
			builder.append("(false ==(");
			BooleanExpressionBuilder.SINGLETON.build(builder , token.filteredChildren.get(0) , 
			    tinyExpressionTokens);
			builder.append("))");
				
		}else if(parser instanceof ParenthesesParser){
		
			Token parenthesesed = ParenthesesParser.getParenthesesed(token);
			builder.append("(");
			BooleanExpressionBuilder.SINGLETON.build(builder , parenthesesed , 
			    tinyExpressionTokens);
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
					
		}else if(parser instanceof BooleanVariableParser || parser instanceof NakedVariableParser) {
		  TypedToken<VariableParser> typed = token.typed(VariableParser.class);
		  
      VariableBuilder.build(this, builder, typed, tinyExpressionTokens, BooleanSetterParser.class,
          "false","getBoolean","setAndGet" , ExpressionTypes._boolean);
//			String variableName = BooleanVariableParser.getVariableName(token);
//			builder.append("calculateContext.getBoolean(").w(variableName).append(").orElse(false)");
//			
//    }else if(parser instanceof NakedVariableParser) {
//      
//      String variableName = NakedVariableParser.getVariableName(token);
//      builder.append("calculateContext.getBoolean(").w(variableName).append(").orElse(false)");
      
		}else if(parser instanceof TrueTokenParser){
			
			builder.append("true");
			
		}else if(parser instanceof FalseTokenParser){
			
			builder.append("false");
			
		}else if(
			parser instanceof NumberEqualEqualExpressionParser ||
			parser instanceof NumberNotEqualExpressionParser ||
			parser instanceof NumberGreaterOrEqualExpressionParser ||
			parser instanceof NumberLessOrEqualExpressionParser ||
			parser instanceof NumberGreaterExpressionParser ||
			parser instanceof NumberLessExpressionParser
		){
			BinaryConditionBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);
			
		}else if (parser instanceof StringEqualsExpressionParser) {

			StringBooleanEqualClauseBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);

		} else if (parser instanceof StringNotEqualsExpressionParser) {

			StringBooleanNotEqualClauseBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);

		} else if (parser instanceof StringMultipleParameterPredicator) {
		  
		  StringMultipleParameterPredicator.class.cast(parser)
		    .build(builder, token, tinyExpressionTokens);

		} else if (parser instanceof BooleanSideEffectExpressionParser) {
			
			SideEffectExpressionBuilder.SINGLETON.build(builder , token ,tinyExpressionTokens);
			
		}else if (parser instanceof BooleanIfExpressionParser) {
		  
      Token booleanExpression = IfExpressionParser.getBooleanExpression(token);
      Token factor1 = IfExpressionParser.getThenExpression(token , BooleanExpression.class , booleanExpression);
      Token factor2 = IfExpressionParser.getElseExpression(token , BooleanExpression.class , booleanExpression);

      /*
       * BooleanExpressionOperator.SINGLETON.evaluate(calculateContext, booleanExpression)?
       * factor1: factor2
       */

      builder.append("(");

      BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression , 
          tinyExpressionTokens);

      builder.append(" ? ").n().incTab();
      BooleanExpressionBuilder.SINGLETON.build(builder, factor1 , tinyExpressionTokens);

      builder.append(":").n();
      BooleanExpressionBuilder.SINGLETON.build(builder, factor2 , tinyExpressionTokens);

      builder.decTab();

      builder.append(")");
      
    } else if (parser instanceof BooleanMatchExpressionParser) {

      Token caseExpression = token.filteredChildren.get(0);
      Token defaultCaseFactor = token.filteredChildren.get(1);

      builder.n();
      builder.incTab();

      builder.append("(");

      BooleanCaseExpressionBuilder.SINGLETON.build(builder, caseExpression , 
          tinyExpressionTokens);
      builder.n();
      BooleanExpressionBuilder.SINGLETON.build(builder, defaultCaseFactor , 
          tinyExpressionTokens);

      builder.append(")");
      builder.decTab();
      
    }else if (parser instanceof MethodInvocationParser) {
      
      MethodInvocationBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);

		}else {
		  //ここでBooleanExpressionParserでエラーが発生するのはOperatorOperandTreeCreatorできちんとapplyされてない時
		  throw new IllegalArgumentException();
		}
	}
}