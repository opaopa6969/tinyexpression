package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.DivisionParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionType.PrePost;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.MethodInvocationParser;
import org.unlaxer.tinyexpression.parser.MinusParser;
import org.unlaxer.tinyexpression.parser.MultipleParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.NumberCaseExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberExpression;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberFactorParser;
import org.unlaxer.tinyexpression.parser.NumberIfExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberParser;
import org.unlaxer.tinyexpression.parser.NumberSetterParser;
import org.unlaxer.tinyexpression.parser.NumberTermParser;
import org.unlaxer.tinyexpression.parser.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.PlusParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.StringLengthParser;
import org.unlaxer.tinyexpression.parser.ToNumParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;

public class NumberExpressionBuilder implements TokenCodeBuilder {

  @FunctionalInterface
  private interface NodeHandler {
    void build(NumberExpressionBuilder self, SimpleJavaCodeBuilder builder, Token token,
        TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber);
  }

  private static final LinkedHashMap<Class<?>, NodeHandler> SIMPLE_HANDLERS = new LinkedHashMap<>();

  static {
    registerSimpleHandler(PlusParser.class,
        (self, builder, token, tinyExpressionTokens, numberType, wrapNumber) ->
            self.binaryOperate(builder, token, "+", tinyExpressionTokens));
    registerSimpleHandler(MinusParser.class,
        (self, builder, token, tinyExpressionTokens, numberType, wrapNumber) ->
            self.binaryOperate(builder, token, "-", tinyExpressionTokens));
    registerSimpleHandler(MultipleParser.class,
        (self, builder, token, tinyExpressionTokens, numberType, wrapNumber) ->
            self.binaryOperate(builder, token, "*", tinyExpressionTokens));
    registerSimpleHandler(DivisionParser.class,
        (self, builder, token, tinyExpressionTokens, numberType, wrapNumber) ->
            self.binaryOperate(builder, token, "/", tinyExpressionTokens));
    registerSimpleHandler(NumberParser.class,
        (self, builder, token, tinyExpressionTokens, numberType, wrapNumber) ->
            builder.append(numberType.numberWithSuffix(token.tokenString.get())));
    registerSimpleHandler(NakedVariableParser.class, NumberExpressionBuilder::buildVariable);
    registerSimpleHandler(NumberVariableParser.class, NumberExpressionBuilder::buildVariable);
    registerSimpleHandler(SinParser.class, (self, builder, token, tinyExpressionTokens, numberType, wrapNumber) ->
        self.buildAngleFunction(builder, token, tinyExpressionTokens, wrapNumber, "sin"));
    registerSimpleHandler(CosParser.class, (self, builder, token, tinyExpressionTokens, numberType, wrapNumber) ->
        self.buildAngleFunction(builder, token, tinyExpressionTokens, wrapNumber, "cos"));
    registerSimpleHandler(TanParser.class, (self, builder, token, tinyExpressionTokens, numberType, wrapNumber) ->
        self.buildAngleFunction(builder, token, tinyExpressionTokens, wrapNumber, "tan"));
    registerSimpleHandler(SquareRootParser.class, NumberExpressionBuilder::buildSqrt);
    registerSimpleHandler(MinParser.class, NumberExpressionBuilder::buildMin);
    registerSimpleHandler(MaxParser.class, NumberExpressionBuilder::buildMax);
    registerSimpleHandler(RandomParser.class, NumberExpressionBuilder::buildRandom);
    registerSimpleHandler(NumberIfExpressionParser.class, NumberExpressionBuilder::buildNumberIf);
    registerSimpleHandler(NumberMatchExpressionParser.class, NumberExpressionBuilder::buildNumberMatch);
    registerSimpleHandler(ToNumParser.class, NumberExpressionBuilder::buildToNum);
    registerSimpleHandler(StringLengthParser.class, NumberExpressionBuilder::buildStringLength);
    registerSimpleHandler(SideEffectExpressionParser.class, NumberExpressionBuilder::buildSideEffect);
    registerSimpleHandler(MethodInvocationParser.class, NumberExpressionBuilder::buildMethodInvocation);
  }

  public static class NumberCaseExpressionBuilder implements TokenCodeBuilder{

    public static NumberCaseExpressionBuilder SINGLETON = new NumberCaseExpressionBuilder();

    public void build(SimpleJavaCodeBuilder builder, Token token ,
        TinyExpressionTokens tinyExpressionTokens) {

      List<Token> originalTokens = token.filteredChildren;
      Iterator<Token> iterator = originalTokens.iterator();

      while(iterator.hasNext()){
        Token caseFactor = iterator.next();

        Token booleanExpression = caseFactor.filteredChildren.get(0);
        Token expression = caseFactor.filteredChildren.get(1);
        BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression ,
            tinyExpressionTokens);
        builder.append(" ? ");
        NumberExpressionBuilder.SINGLETON.build(builder, expression , 
            tinyExpressionTokens);
        builder
          .append(":")
          .n();
      }
    }
  }
  
  public static NumberExpressionBuilder SINGLETON = new NumberExpressionBuilder();

  private static void registerSimpleHandler(Class<?> parserType, NodeHandler handler) {
    SIMPLE_HANDLERS.put(parserType, handler);
  }

  private static NodeHandler findSimpleHandler(Parser parser) {
    return ParserDispatch.findHandler(SIMPLE_HANDLERS, parser);
  }

  public void build(SimpleJavaCodeBuilder builder, Token token , 
		  TinyExpressionTokens tinyExpressionTokens) {
    
    ExpressionType numberType = tinyExpressionTokens.numberType();
    PrePost wrapNumber = numberType.wrapNumber();

    Parser parser = token.parser;
    
    if(parser instanceof NumberExpressionParser) {
      
      token = token.filteredChildren.get(0);
      parser = token.parser;
      
      if (parser instanceof NumberTermParser) {
        
        token = token.filteredChildren.get(0);
        parser = token.parser;
        
        if(parser instanceof NumberFactorParser) {
          token = token.filteredChildren.get(0);
          parser = token.parser;
          
        }
      }
    }

    NodeHandler simpleHandler = findSimpleHandler(parser);
    if (simpleHandler != null) {
      simpleHandler.build(this, builder, token, tinyExpressionTokens, numberType, wrapNumber);
      return;
    }

    throw new IllegalArgumentException("Unsupported parser for NumberExpressionBuilder: " + parser.getClass().getName());
  }

  void binaryOperate(SimpleJavaCodeBuilder builder, Token token, String operator ,
      TinyExpressionTokens tinyExpressionTokens) {

    builder.append("(");

    build(builder, token.filteredChildren.get(1) , tinyExpressionTokens);
    builder.append(operator);
    build(builder, token.filteredChildren.get(2) , tinyExpressionTokens);

    builder.append(")");
  }

  private void buildVariable(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    Optional<ExpressionType> fromVariableParserToken =
        VariableTypeResolver.resolveFromVariableParserToken(token, tinyExpressionTokens);
    TypedToken<VariableParser> typed = token.typed(VariableParser.class);

    VariableBuilder.build(this, builder, typed, tinyExpressionTokens, NumberSetterParser.class,
        numberType.zeroNumber(), "getValue", "setAndGet", fromVariableParserToken.orElse(ExpressionTypes.number));
  }

  private void buildAngleFunction(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, PrePost wrapNumber, String mathFunction) {

    Token value = token.filteredChildren.get(0);
    builder.append(wrapNumber.pre());
    builder.append(" Math.").append(mathFunction).append("(calculateContext.radianAngle(");
    build(builder, value, tinyExpressionTokens);
    builder.append(wrapNumber.post());
    builder.append("))");
  }

  private void buildSqrt(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    Token value = token.filteredChildren.get(0);
    builder.append(wrapNumber.pre());
    builder.append(" Math.sqrt(");
    build(builder, value, tinyExpressionTokens);
    builder.append(")");
    builder.append(wrapNumber.post());
  }

  private void buildMin(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    builder.append(wrapNumber.pre());
    builder.append(" Math.min(");
    build(builder, token.filteredChildren.get(0), tinyExpressionTokens);
    builder.append(",");
    build(builder, token.filteredChildren.get(1), tinyExpressionTokens);
    builder.append(")");
    builder.append(wrapNumber.post());
  }

  private void buildMax(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    builder.append(wrapNumber.pre());
    builder.append(" Math.max(");
    build(builder, token.filteredChildren.get(0), tinyExpressionTokens);
    builder.append(",");
    build(builder, token.filteredChildren.get(1), tinyExpressionTokens);
    builder.append(")");
    builder.append(wrapNumber.post());
  }

  private void buildRandom(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    builder.append(wrapNumber.pre());
    builder.append("calculateContext.nextRandom()");
    builder.append(wrapNumber.post());
  }

  private void buildNumberIf(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    Token booleanExpression = IfExpressionParser.getBooleanExpression(token);
    Token factor1 = IfExpressionParser.getThenExpression(token, NumberExpression.class, booleanExpression);
    Token factor2 = IfExpressionParser.getElseExpression(token, NumberExpression.class, booleanExpression);

    builder.append("(");
    BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression, tinyExpressionTokens);
    builder.append(" ? ").n().incTab();
    build(builder, factor1, tinyExpressionTokens);
    builder.append(":").n();
    build(builder, factor2, tinyExpressionTokens);
    builder.decTab();
    builder.append(")");
  }

  private void buildNumberMatch(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    Token caseExpression = token.getChild(TokenPredicators.parsers(NumberCaseExpressionParser.class));
    Token defaultCaseFactor = token.getChildFromAstNodes(1);

    builder.n();
    builder.incTab();
    builder.append("(");
    NumberCaseExpressionBuilder.SINGLETON.build(builder, caseExpression, tinyExpressionTokens);
    builder.n();
    build(builder, defaultCaseFactor, tinyExpressionTokens);
    builder.append(")");
    builder.decTab();
  }

  private void buildToNum(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    Token leftString = token.filteredChildren.get(0);
    Token rightFloatDefault = token.filteredChildren.get(1);

    builder.append("org.unlaxer.tinyexpression.function.EmbeddedFunction.toNum(");
    builder.append(StringClauseBuilder.SINGLETON.build(leftString, tinyExpressionTokens).toString());
    builder.append(",");
    build(builder, rightFloatDefault, tinyExpressionTokens);
    builder.append("f)");
  }

  private void buildStringLength(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    Token stringExpressionToken = token.filteredChildren.get(0);
    String string = StringClauseBuilder.SINGLETON.build(stringExpressionToken, tinyExpressionTokens).toString();
    if (string == null || string.isEmpty()) {
      string = "\"\"";
    }
    builder.append(string).append(".length()");
  }

  private void buildSideEffect(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    SideEffectExpressionBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
  }

  private void buildMethodInvocation(SimpleJavaCodeBuilder builder, Token token,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType, PrePost wrapNumber) {

    MethodInvocationBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
  }
}
