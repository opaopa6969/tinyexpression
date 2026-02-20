package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.unlaxer.tinyexpression.parser.StringSideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.StringTermParser;
import org.unlaxer.tinyexpression.parser.StringVariableParser;
import org.unlaxer.tinyexpression.parser.ToLowerCaseParser;
import org.unlaxer.tinyexpression.parser.ToUpperCaseParser;
import org.unlaxer.tinyexpression.parser.TrimParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.util.FactoryBoundCache;

public class StringClauseBuilder {

  @FunctionalInterface
  private interface NodeHandler {
    ExpressionOrLiteral build(StringClauseBuilder self, Token token, TinyExpressionTokens tinyExpressionTokens);
  }

  private static final LinkedHashMap<Class<?>, NodeHandler> HANDLERS = new LinkedHashMap<>();

  static {
    registerHandler(StringExpressionParser.class, StringClauseBuilder::buildStringExpression);
    registerHandler(StringTermParser.class, StringClauseBuilder::buildStringTerm);
    registerHandler(StringPlusParser.class, StringClauseBuilder::buildStringPlus);
    registerHandler(SliceParser.class, StringClauseBuilder::buildSlice);
    registerHandler(StringLiteralParser.class, StringClauseBuilder::buildStringLiteral);
    registerHandler(NakedVariableParser.class, StringClauseBuilder::buildStringVariable);
    registerHandler(StringVariableParser.class, StringClauseBuilder::buildStringVariable);
    registerHandler(ParenthesesParser.class, StringClauseBuilder::buildParentheses);
    registerHandler(TrimParser.class, (self, token, tinyExpressionTokens) ->
        self.buildUnaryStringMethod(token, tinyExpressionTokens, ".trim()"));
    registerHandler(ToUpperCaseParser.class, (self, token, tinyExpressionTokens) ->
        self.buildUnaryStringMethod(token, tinyExpressionTokens, ".toUpperCase()"));
    registerHandler(ToLowerCaseParser.class, (self, token, tinyExpressionTokens) ->
        self.buildUnaryStringMethod(token, tinyExpressionTokens, ".toLowerCase()"));
    registerHandler(StringIfExpressionParser.class, StringClauseBuilder::buildStringIf);
    registerHandler(StringMatchExpressionParser.class, StringClauseBuilder::buildStringMatch);
    registerHandler(MethodInvocationParser.class, StringClauseBuilder::buildMethodInvocation);
    registerHandler(StringSideEffectExpressionParser.class, StringClauseBuilder::buildStringSideEffect);
  }
  
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
        
        BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression , 
            tinyExpressionTokens);
        builder.append(" ? ");
        StringExpressionBuilder.SINGLETON.build(builder, expression , tinyExpressionTokens);
        builder
          .append(":")
          .n();
      }
    }
  }

	public static final StringClauseBuilder SINGLETON = new StringClauseBuilder();

  private static void registerHandler(Class<?> parserType, NodeHandler handler) {
    HANDLERS.put(parserType, handler);
  }

  private static NodeHandler findHandler(Parser parser) {
    return ParserDispatch.requireHandler(HANDLERS, parser, "StringClauseBuilder");
  }

	public ExpressionOrLiteral build(Token token , TinyExpressionTokens tinyExpressionTokens) {

			Parser parser = token.parser;

      return findHandler(parser).build(this, token, tinyExpressionTokens);
		}

  private ExpressionOrLiteral buildStringExpression(Token token, TinyExpressionTokens tinyExpressionTokens) {
    Parser parser = token.filteredChildren.get(0).parser;
    if (parser instanceof StringTermParser) {
      List<Token> terms = new ArrayList<Token>();
      terms.add(token.filteredChildren.get(0));
      Token successor = token.filteredChildren.get(1);
      List<Token> addings = successor.filteredChildren.stream()
          .map(ChoiceInterface::choiced)
          .filter(_token -> _token.parser instanceof StringTermParser)
          .collect(Collectors.toList());
      terms.addAll(addings);

      Iterator<Token> iterator = terms.iterator();
      StringBuilder builder = new StringBuilder();
      while (iterator.hasNext()) {
        Token term = iterator.next();
        ExpressionOrLiteral built = build(term, tinyExpressionTokens);
        builder.append(built.toString());
        if (iterator.hasNext()) {
          builder.append("+");
        }
      }
      return ExpressionOrLiteral.expressionOf("(" + builder.toString() + ")");
    }
    return build(token.filteredChildren.get(0), tinyExpressionTokens);
  }

  private ExpressionOrLiteral buildStringTerm(Token token, TinyExpressionTokens tinyExpressionTokens) {
    Token inner = token.filteredChildren.get(0);
    if (inner.parser instanceof StringFactorParser) {
      return build(inner.filteredChildren.get(0), tinyExpressionTokens);
    }
    return build(inner, tinyExpressionTokens);
  }

  private ExpressionOrLiteral buildStringPlus(Token token, TinyExpressionTokens tinyExpressionTokens) {
    Iterator<Token> iterator = token.filteredChildren.iterator();
    StringBuilder builder = new StringBuilder();
    iterator.next();
    while (iterator.hasNext()) {
      Token successor = iterator.next();
      ExpressionOrLiteral built = build(successor, tinyExpressionTokens);
      builder.append(built.toString());
      if (iterator.hasNext()) {
        builder.append("+");
      }
    }
    return ExpressionOrLiteral.expressionOf("(" + builder.toString() + ")");
  }

  private ExpressionOrLiteral buildSlice(Token token, TinyExpressionTokens tinyExpressionTokens) {
    Token stringFactorToken = token.filteredChildren.get(1);
    Token slicerToken = token.filteredChildren.get(0);
    ExpressionOrLiteral inner = build(stringFactorToken, tinyExpressionTokens);

    Optional<String> specifier = slicerToken.getToken()
        .map(wrapped -> wrapped.substring(1, wrapped.length() - 1));

    return specifier.map(slicerSpecifier ->
        ExpressionOrLiteral.expressionOf(
            "new org.unlaxer.util.Slicer(" + inner + ").pythonian(\"" + slicerSpecifier + "\").get()"))
        .orElse(inner);
  }

  private ExpressionOrLiteral buildStringLiteral(Token token, TinyExpressionTokens tinyExpressionTokens) {
    Token literalChoiceToken = ChoiceInterface.choiced(token);
    String contents = stringByToken.get(literalChoiceToken);
    return ExpressionOrLiteral.literalOf(contents == null ? "" : contents);
  }

  private ExpressionOrLiteral buildStringVariable(Token token, TinyExpressionTokens tinyExpressionTokens) {
    List<Token> variableDeclarationsTokens = tinyExpressionTokens.getVariableDeclarationTokens();

    TypedToken<VariableParser> typed = token.typed(VariableParser.class);
    VariableParser variableParser = typed.getParser();
    String variableName = variableParser.getVariableName(typed);

    SimpleBuilder builder = new SimpleBuilder();
    boolean isMatch = false;

    for (Token declarationTtoken : variableDeclarationsTokens) {
      TypedToken<? extends VariableParser> nakedVariableToken =
          declarationTtoken.getChildWithParserTyped(NakedVariableParser.class);
      VariableParser variabvleParser = nakedVariableToken.getParser();
      String _variableName = variabvleParser.getVariableName(nakedVariableToken);

      if (_variableName.equals(variableName)) {
        Optional<Token> setterToken = declarationTtoken.getChildWithParserAsOptional(StringSetterParser.class);
        if (setterToken.isEmpty()) {
          continue;
        }
        Token _setterToken = setterToken.get();
        Token expression = _setterToken.getChild(TokenPredicators.parserImplements(ExpressionInterface.class));
        Optional<Token> ifNotExists = _setterToken.getChildWithParserAsOptional(IfNotExistsParser.class);

        ExpressionOrLiteral built = build(expression, tinyExpressionTokens);
        String expressionString = built.toString();
        if (ifNotExists.isPresent()) {
          builder.append("calculateContext.getString(").w(variableName).append(").orElse(" + expressionString + ")");
        } else {
          builder.append("calculateContext.setAndGet(").w(variableName).append("," + expressionString + ")");
        }
        isMatch = true;
        break;
      }
    }

    if (!isMatch) {
      builder.append("calculateContext.getString(").w(variableName).append(").orElse(\"\")");
    }

    return ExpressionOrLiteral.expressionOf(builder.toString());
  }

  private ExpressionOrLiteral buildParentheses(Token token, TinyExpressionTokens tinyExpressionTokens) {
    Token parenthesesed = token.filteredChildren.get(0);
    return build(parenthesesed, tinyExpressionTokens);
  }

  private ExpressionOrLiteral buildUnaryStringMethod(Token token, TinyExpressionTokens tinyExpressionTokens, String suffix) {
    Token parenthesesed = token.filteredChildren.get(0);
    ExpressionOrLiteral evaluate = build(parenthesesed, tinyExpressionTokens);
    return ExpressionOrLiteral.expressionOf(evaluate.toString() + suffix);
  }

  private ExpressionOrLiteral buildStringIf(Token token, TinyExpressionTokens tinyExpressionTokens) {
    Token booleanExpression = IfExpressionParser.getBooleanExpression(token);
    Token factor1 = IfExpressionParser.getThenExpression(token, StringExpression.class, booleanExpression);
    Token factor2 = IfExpressionParser.getElseExpression(token, StringExpression.class, booleanExpression);

    ExpressionOrLiteral factor1EOL = build(factor1, tinyExpressionTokens);
    ExpressionOrLiteral factor2EOL = build(factor2, tinyExpressionTokens);

    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
    builder.setKind(Kind.Main);
    builder.append("(");
    BooleanExpressionBuilder.SINGLETON.build(builder, booleanExpression, tinyExpressionTokens);
    builder.append(" ? ").n().incTab();
    builder.append(factor1EOL.toString());
    builder.append(":").n();
    builder.append(factor2EOL.toString());
    builder.decTab();
    builder.append(")");
    return ExpressionOrLiteral.expressionOf(builder.getBuilder(Kind.Main).toString());
  }

  private ExpressionOrLiteral buildStringMatch(Token token, TinyExpressionTokens tinyExpressionTokens) {
    Token caseExpression = token.filteredChildren.get(0);
    Token defaultCaseFactor = token.filteredChildren.get(1);

    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
    ExpressionOrLiteral defaultFactor = build(defaultCaseFactor, tinyExpressionTokens);
    builder.setKind(Kind.Main);
    builder.n();
    builder.incTab();
    builder.append("(");
    StringCaseExpressionBuilder.SINGLETON.build(builder, caseExpression, tinyExpressionTokens);
    builder.n();
    builder.append(defaultFactor.toString());
    builder.append(")");
    builder.decTab();
    return ExpressionOrLiteral.expressionOf(builder.getBuilder(Kind.Main).toString());
  }

  private ExpressionOrLiteral buildMethodInvocation(Token token, TinyExpressionTokens tinyExpressionTokens) {
    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
    MethodInvocationBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
    return ExpressionOrLiteral.expressionOf(builder.getBuilder(Kind.Main).toString());
  }

  private ExpressionOrLiteral buildStringSideEffect(Token token, TinyExpressionTokens tinyExpressionTokens) {
    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
    SideEffectExpressionBuilder.SINGLETON.build(builder, token, tinyExpressionTokens);
    return ExpressionOrLiteral.expressionOf(builder.getBuilder(Kind.Calculation).toString()).setReturning(builder);
  }

	static FactoryBoundCache<Token, String> stringByToken = new FactoryBoundCache<>(QuotedParser::contents);
}
