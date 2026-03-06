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
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedAstAdapter;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedAstNode;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedBinaryAstNode;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedLiteralAstNode;

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
            builder.append(self.numberLiteral(numberType, token.tokenString.get())));
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
    return ParserDispatch.requireHandler(SIMPLE_HANDLERS, parser, "NumberExpressionBuilder");
  }

  public void build(SimpleJavaCodeBuilder builder, Token token , 
		  TinyExpressionTokens tinyExpressionTokens) {
    
    ExpressionType numberType = tinyExpressionTokens.numberType();
    PrePost wrapNumber = numberType.wrapNumber();

    token = unwrapNumberExpressionToken(token);
    Parser parser = token.parser;

    Optional<NumberGeneratedAstNode> generatedAst = NumberGeneratedAstAdapter.SINGLETON.tryGenerate(token);
    if (generatedAst.isPresent()) {
      build(builder, generatedAst.get(), tinyExpressionTokens);
      return;
    }

    findSimpleHandler(parser).build(this, builder, token, tinyExpressionTokens, numberType, wrapNumber);
  }

  public void build(SimpleJavaCodeBuilder builder, NumberGeneratedAstNode generatedAst,
      TinyExpressionTokens tinyExpressionTokens) {
    ExpressionType numberType = tinyExpressionTokens.numberType();

    if (generatedAst instanceof NumberGeneratedLiteralAstNode literalNode) {
      builder.append(numberLiteral(numberType, literalNode.literal()));
      return;
    }

    if (generatedAst instanceof NumberGeneratedBinaryAstNode binaryNode) {
      buildBinary(builder, binaryNode, tinyExpressionTokens, numberType);
      return;
    }

    throw new IllegalArgumentException("Unsupported generated number AST node: " + generatedAst.getClass().getName());
  }

  Token unwrapNumberExpressionToken(Token token) {
    if (token == null) {
      throw new IllegalArgumentException("Number expression token is null");
    }
    Parser parser = token.parser;

    if (parser instanceof NumberExpressionParser) {
      token = token.filteredChildren.get(0);
      parser = token.parser;

      if (parser instanceof NumberTermParser) {
        token = token.filteredChildren.get(0);
        parser = token.parser;

        if (parser instanceof NumberFactorParser) {
          token = token.filteredChildren.get(0);
        }
      }
    }
    return token;
  }

  void binaryOperate(SimpleJavaCodeBuilder builder, Token token, String operator ,
      TinyExpressionTokens tinyExpressionTokens) {

    ExpressionType numberType = tinyExpressionTokens.numberType();
    if (numberType.isBigInteger() || numberType.isBigDecimal()) {
      if (!isSupportedBinaryOperator(operator)) {
        throw new IllegalArgumentException("Unsupported operator for big-number expression: " + operator);
      }
      builder.append("(");
      build(builder, token.filteredChildren.get(1), tinyExpressionTokens);
      builder.append(".").append(bigNumberMethodName(operator)).append("(");
      build(builder, token.filteredChildren.get(2), tinyExpressionTokens);
      if (numberType.isBigDecimal() && "/".equals(operator)) {
        builder.append(",calculateContext.scale(),calculateContext.roundingMode()");
      }
      builder.append("))");
      return;
    }

    builder.append("(");
    build(builder, token.filteredChildren.get(1), tinyExpressionTokens);
    builder.append(operator);
    build(builder, token.filteredChildren.get(2), tinyExpressionTokens);
    builder.append(")");
  }

  private void buildBinary(SimpleJavaCodeBuilder builder, NumberGeneratedBinaryAstNode binaryNode,
      TinyExpressionTokens tinyExpressionTokens, ExpressionType numberType) {
    if (numberType.isBigInteger() || numberType.isBigDecimal()) {
      if (!isSupportedBinaryOperator(binaryNode.operator())) {
        throw new IllegalArgumentException(
            "Unsupported operator for generated big-number expression: " + binaryNode.operator());
      }
      builder.append("(");
      build(builder, binaryNode.left(), tinyExpressionTokens);
      builder.append(".").append(bigNumberMethodName(binaryNode.operator())).append("(");
      build(builder, binaryNode.right(), tinyExpressionTokens);
      if (numberType.isBigDecimal() && "/".equals(binaryNode.operator())) {
        builder.append(",calculateContext.scale(),calculateContext.roundingMode()");
      }
      builder.append("))");
      return;
    }

    builder.append("(");
    build(builder, binaryNode.left(), tinyExpressionTokens);
    builder.append(binaryNode.operator());
    build(builder, binaryNode.right(), tinyExpressionTokens);
    builder.append(")");
  }

  private String numberLiteral(ExpressionType numberType, String literalToken) {
    if (numberType.isBigInteger()) {
      try {
        numberType.parseNumber(literalToken);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Invalid BigInteger literal: " + literalToken, e);
      }
      return "new java.math.BigInteger(\"" + literalToken + "\")";
    }
    if (numberType.isBigDecimal()) {
      try {
        numberType.parseNumber(literalToken);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Invalid BigDecimal literal: " + literalToken, e);
      }
      return "new java.math.BigDecimal(\"" + literalToken + "\")";
    }
    return numberType.numberWithSuffix(literalToken);
  }

  private boolean isSupportedBinaryOperator(String operator) {
    return "+".equals(operator) || "-".equals(operator) || "*".equals(operator) || "/".equals(operator);
  }

  private String bigNumberMethodName(String operator) {
    return "+".equals(operator) ? "add"
        : "-".equals(operator) ? "subtract"
            : "*".equals(operator) ? "multiply"
                : "/".equals(operator) ? "divide"
                    : "";
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
    if (factor1 == null) {
      factor1 = extractChoiceBranchToken(token, 0);
    }
    if (factor2 == null) {
      factor2 = extractChoiceBranchToken(token, 4);
    }
    if (factor1 == null || factor2 == null) {
      throw new IllegalArgumentException(
          "if-expression branch token missing then/else");
    }

    builder.append("(");
    BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression, tinyExpressionTokens);
    builder.append(" ? ").n().incTab();
    build(builder, factor1, tinyExpressionTokens);
    builder.append(":").n();
    build(builder, factor2, tinyExpressionTokens);
    builder.decTab();
    builder.append(")");
  }

  private Token extractChoiceBranchToken(Token ifToken, int branchIndex) {
    if (ifToken == null || ifToken.filteredChildren == null) {
      return null;
    }
    if (ifToken.filteredChildren.size() >= 3) {
      if (branchIndex == 0) {
        return ifToken.filteredChildren.get(1);
      }
      if (branchIndex == 4) {
        return ifToken.filteredChildren.get(2);
      }
      return null;
    }
    if (ifToken.filteredChildren.size() <= 5) {
      return null;
    }
    Token choiceNode = ifToken.filteredChildren.get(5);
    if (choiceNode == null || choiceNode.filteredChildren == null || choiceNode.filteredChildren.size() <= branchIndex) {
      return null;
    }
    return choiceNode.filteredChildren.get(branchIndex);
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
