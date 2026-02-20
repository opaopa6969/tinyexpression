package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.unlaxer.tinyexpression.parser.InDayTimeRangeParser;
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

  @FunctionalInterface
  private interface NodeHandler {
    void build(BooleanBuilder self, SimpleJavaCodeBuilder builder, Token token,
        TinyExpressionTokens tinyExpressionTokens);
  }

  private static final LinkedHashMap<Class<?>, NodeHandler> HANDLERS = new LinkedHashMap<>();

  static {
    registerHandler(NotBooleanExpressionParser.class, BooleanBuilder::buildNotExpression);
    registerHandler(ParenthesesParser.class, BooleanBuilder::buildParenthesized);
    registerHandler(IsPresentParser.class, BooleanBuilder::buildIsPresent);
    registerHandler(InTimeRangeParser.class, BooleanBuilder::buildInTimeRange);
    registerHandler(InDayTimeRangeParser.class, BooleanBuilder::buildInDayTimeRange);
    registerHandler(BooleanVariableParser.class, BooleanBuilder::buildVariable);
    registerHandler(NakedVariableParser.class, BooleanBuilder::buildVariable);
    registerHandler(TrueTokenParser.class, (self, builder, token, tinyExpressionTokens) -> builder.append("true"));
    registerHandler(FalseTokenParser.class, (self, builder, token, tinyExpressionTokens) -> builder.append("false"));

    registerHandler(NumberEqualEqualExpressionParser.class, BooleanBuilder::buildNumberCondition);
    registerHandler(NumberNotEqualExpressionParser.class, BooleanBuilder::buildNumberCondition);
    registerHandler(NumberGreaterOrEqualExpressionParser.class, BooleanBuilder::buildNumberCondition);
    registerHandler(NumberLessOrEqualExpressionParser.class, BooleanBuilder::buildNumberCondition);
    registerHandler(NumberGreaterExpressionParser.class, BooleanBuilder::buildNumberCondition);
    registerHandler(NumberLessExpressionParser.class, BooleanBuilder::buildNumberCondition);

    registerHandler(StringEqualsExpressionParser.class, BooleanBuilder::buildStringEquals);
    registerHandler(StringNotEqualsExpressionParser.class, BooleanBuilder::buildStringNotEquals);
    registerHandler(StringMultipleParameterPredicator.class, BooleanBuilder::buildStringMultiplePredicator);
    registerHandler(BooleanSideEffectExpressionParser.class, BooleanBuilder::buildSideEffect);
    registerHandler(BooleanIfExpressionParser.class, BooleanBuilder::buildBooleanIf);
    registerHandler(BooleanMatchExpressionParser.class, BooleanBuilder::buildBooleanMatch);
    registerHandler(MethodInvocationParser.class, BooleanBuilder::buildMethodInvocation);
  }
	
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

  private static void registerHandler(Class<?> parserType, NodeHandler handler) {
    HANDLERS.put(parserType, handler);
  }

  private static NodeHandler findHandler(Parser parser) {
    return ParserDispatch.findHandler(HANDLERS, parser);
  }

	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token, 
	    TinyExpressionTokens tinyExpressionTokens) {
			Parser parser = token.parser;

      NodeHandler handler = findHandler(parser);
      if (handler != null) {
        handler.build(this, builder, token, tinyExpressionTokens);
        return;
      }

      // ここでエラーが発生するのはOperatorOperandTreeCreator側で再構成が崩れている時
      throw new IllegalArgumentException("Unsupported parser for BooleanBuilder: " + parser.getClass().getName());
		}

  private void buildNotExpression(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    builder.append("(false ==(");
    BooleanExpressionBuilder.SINGLETON.build(builder, token.filteredChildren.get(0), tinyExpressionTokens);
    builder.append("))");
  }

  private void buildParenthesized(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    Token parenthesesed = ParenthesesParser.getParenthesesed(token);
    builder.append("(");
    BooleanExpressionBuilder.SINGLETON.build(builder, parenthesesed, tinyExpressionTokens);
    builder.append(")");
  }

  private void buildIsPresent(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    String variableName = token.tokenString.get().substring(1);
    builder.append("calculateContext.isExists(").w(variableName).append(")");
  }

  private void buildInTimeRange(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    String fromHour = token.filteredChildren.get(0).tokenString.get();
    String toHour = token.filteredChildren.get(1).tokenString.get();

    parserValuesValidator.validateTimeRangeValues(fromHour, toHour);
    builder.append("org.unlaxer.tinyexpression.function.EmbeddedFunction.inTimeRange(calculateContext,")
        .append(fromHour).append("f,").append(toHour).append("f)");
  }

  private void buildInDayTimeRange(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    String fromDay = token.filteredChildren.get(0).tokenString.get();
    String fromHour = token.filteredChildren.get(1).tokenString.get();
    String toDay = token.filteredChildren.get(2).tokenString.get();
    String toHour = token.filteredChildren.get(3).tokenString.get();

    parserValuesValidator.validateTimeRangeValues(fromHour, toHour);
    builder
        .append("calculateContext.inDayTimeRange(")
        .append("java.time.DayOfWeek.").append(fromDay).append(",")
        .append(fromHour).append("f,")
        .append("java.time.DayOfWeek.").append(toDay).append(",")
        .append(toHour).append("f")
        .append(")");
  }

  private void buildVariable(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    TypedToken<VariableParser> typed = token.typed(VariableParser.class);
    VariableBuilder.build(this, builder, typed, tinyExpressionTokens, BooleanSetterParser.class,
        "false", "getBoolean", "setAndGet", ExpressionTypes._boolean);
  }

  private void buildNumberCondition(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    BinaryConditionBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
  }

  private void buildStringEquals(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    StringBooleanEqualClauseBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
  }

  private void buildStringNotEquals(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    StringBooleanNotEqualClauseBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
  }

  private void buildStringMultiplePredicator(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    StringMultipleParameterPredicator.class.cast(token.parser).build(builder, token, tinyExpressionTokens);
  }

  private void buildSideEffect(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    SideEffectExpressionBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
  }

  private void buildBooleanIf(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    Token booleanExpression = IfExpressionParser.getBooleanExpression(token);
    Token factor1 = IfExpressionParser.getThenExpression(token, BooleanExpression.class, booleanExpression);
    Token factor2 = IfExpressionParser.getElseExpression(token, BooleanExpression.class, booleanExpression);

    builder.append("(");
    BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression, tinyExpressionTokens);
    builder.append(" ? ").n().incTab();
    BooleanExpressionBuilder.SINGLETON.build(builder, factor1, tinyExpressionTokens);
    builder.append(":").n();
    BooleanExpressionBuilder.SINGLETON.build(builder, factor2, tinyExpressionTokens);
    builder.decTab();
    builder.append(")");
  }

  private void buildBooleanMatch(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    Token caseExpression = token.filteredChildren.get(0);
    Token defaultCaseFactor = token.filteredChildren.get(1);

    builder.n();
    builder.incTab();
    builder.append("(");
    BooleanCaseExpressionBuilder.SINGLETON.build(builder, caseExpression, tinyExpressionTokens);
    builder.n();
    BooleanExpressionBuilder.SINGLETON.build(builder, defaultCaseFactor, tinyExpressionTokens);
    builder.append(")");
    builder.decTab();
  }

  private void buildMethodInvocation(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens) {
    MethodInvocationBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
  }
}
