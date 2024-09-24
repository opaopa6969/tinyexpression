package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.Token.ScanDirection;
import org.unlaxer.TokenEffecterWithMatcher;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.ParseException;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.PseudoRootParser;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.ArgumentChoiceParser;
import org.unlaxer.tinyexpression.parser.ArgumentSuccessorParser;
import org.unlaxer.tinyexpression.parser.BooleanCaseExpressionParser;
import org.unlaxer.tinyexpression.parser.BooleanCaseFactorParser;
import org.unlaxer.tinyexpression.parser.BooleanDefaultCaseFactorParser;
import org.unlaxer.tinyexpression.parser.BooleanExpression;
import org.unlaxer.tinyexpression.parser.BooleanExpressionOfStringParser;
import org.unlaxer.tinyexpression.parser.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.BooleanFactorParser;
import org.unlaxer.tinyexpression.parser.BooleanIfExpressionParser;
import org.unlaxer.tinyexpression.parser.BooleanMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.BooleanSetterParser;
import org.unlaxer.tinyexpression.parser.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.ExclusiveNakedVariableParser;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.FactorOfStringParser;
import org.unlaxer.tinyexpression.parser.FalseTokenParser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.InMethodParser;
import org.unlaxer.tinyexpression.parser.InTimeRangeParser;
import org.unlaxer.tinyexpression.parser.IsPresentParser;
import org.unlaxer.tinyexpression.parser.MethodInvocationParser;
import org.unlaxer.tinyexpression.parser.MethodsParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.NotBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberCaseExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberCaseFactorParser;
import org.unlaxer.tinyexpression.parser.NumberDefaultCaseFactorParser;
import org.unlaxer.tinyexpression.parser.NumberEqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberExpression;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberFactorParser;
import org.unlaxer.tinyexpression.parser.NumberGreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberGreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberIfExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberLessExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberLessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberNotEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberParser;
import org.unlaxer.tinyexpression.parser.NumberSetterParser;
import org.unlaxer.tinyexpression.parser.NumberTermParser;
import org.unlaxer.tinyexpression.parser.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedBooleanFactorParser;
import org.unlaxer.tinyexpression.parser.StrictTypedNumberExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedNumberFactorParser;
import org.unlaxer.tinyexpression.parser.StrictTypedNumberTermParser;
import org.unlaxer.tinyexpression.parser.StrictTypedStringExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedStringFactorParser;
import org.unlaxer.tinyexpression.parser.StrictTypedStringTermParser;
import org.unlaxer.tinyexpression.parser.StringCaseExpressionParser;
import org.unlaxer.tinyexpression.parser.StringCaseFactorParser;
import org.unlaxer.tinyexpression.parser.StringContainsParser;
import org.unlaxer.tinyexpression.parser.StringDefaultCaseFactorParser;
import org.unlaxer.tinyexpression.parser.StringEndsWithParser;
import org.unlaxer.tinyexpression.parser.StringEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.StringExpression;
import org.unlaxer.tinyexpression.parser.StringExpressionMethodParser;
import org.unlaxer.tinyexpression.parser.StringExpressionParser;
import org.unlaxer.tinyexpression.parser.StringFactorParser;
import org.unlaxer.tinyexpression.parser.StringIfExpressionParser;
import org.unlaxer.tinyexpression.parser.StringInParser;
import org.unlaxer.tinyexpression.parser.StringLengthParser;
import org.unlaxer.tinyexpression.parser.StringLiteralParser;
import org.unlaxer.tinyexpression.parser.StringMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.StringMethodExpressionParser;
import org.unlaxer.tinyexpression.parser.StringNotEqualsExpressionParser;
import org.unlaxer.tinyexpression.parser.StringSetterParser;
import org.unlaxer.tinyexpression.parser.StringStartsWithParser;
import org.unlaxer.tinyexpression.parser.StringTermParser;
import org.unlaxer.tinyexpression.parser.StringVariableParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
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
import org.unlaxer.tinyexpression.parser.javalang.AnnotationParameterParser;
import org.unlaxer.tinyexpression.parser.javalang.AnnotationParametersParser;
import org.unlaxer.tinyexpression.parser.javalang.AnnotationParser;
import org.unlaxer.tinyexpression.parser.javalang.AnnotationsParser;
import org.unlaxer.tinyexpression.parser.javalang.BooleanVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.NumberVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.StringVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationsParser;
import org.unlaxer.util.annotation.TokenReConstructor;
import org.unlaxer.util.annotation.TokenReConstructor.TokenReConstructorInterface;

@TokenReConstructor
public class OperatorOperandTreeCreator implements TokenReConstructorInterface{
  
  public static OperatorOperandTreeCreator SINGLETON = new OperatorOperandTreeCreator();
  
  @Override
  public Token apply(Token token) {
    
    Parser parser = token.parser;
    
//    if(parser instanceof ExclusiveNakedVariableParser) {
//      
//      TypedToken<? extends VariableParser> resolveTypedVariable = 
//          VariableTypeResolver.resolveTypedVariable(token.typed(ExclusiveNakedVariableParser.class));
//      
//      parser = resolveTypedVariable.getParser();
//    }
    
    if(parser instanceof TinyExpressionParser) {
      
      TypedToken<TinyExpressionParser> tinyExpressionToken = token.typed(TinyExpressionParser.class);
      
      Token extractCodes = TinyExpressionParser.extractCodesToken(token);
      
      Token extractImports = TinyExpressionParser.extractImportsToken(token);
      
      Token extractNumberExpression = TinyExpressionParser.extractExpression(token);
      Token appliedNumberExpression = apply(extractNumberExpression);
      extractNumberExpression = extractNumberExpression.newCreatesOf(appliedNumberExpression);
      
      Token extractAnnotaions = apply(TinyExpressionParser.extractAnnotaionsToken(token));
      Token extractVariables = apply(TinyExpressionParser.extractVariablesToken(token));
      List</*Typed*/Token/*<MethodParser>*/> extractMethodsTokens = TinyExpressionParser.extractMethods(tinyExpressionToken);
      
      extractMethodsTokens = extractMethodsTokens.stream()
        .map(methodToken->{
          return methodToken.newCreatesOf(
              new TokenEffecterWithMatcher(
                TokenPredicators.parserImplements(ExpressionInterface.class),
                this::apply
              )
            );   
        }).collect(Collectors.toList());
      Token extractMethodsToken = new Token(TokenKind.consumed, extractMethodsTokens, Parser.get(MethodsParser.class),0);
//      extractMethodsToken = extractMethodsToken.newCreatesOf(
//        new TokenEffecterWithMatcher(
//          TokenPredicators.parserImplements(ExpressionInterface.class),
//          this::apply
//        )
//      );
      
      
//      System.out.println(extractMethodsToken.getPath()); 
      Token newCreatesOf = token.newCreatesOf(extractCodes,extractImports,extractVariables,extractAnnotaions,extractNumberExpression, extractMethodsToken);
//      String path = newCreatesOf.getPath();
//      System.out.println(path);
      return newCreatesOf;
    }
    
    if(parser instanceof NumberVariableDeclarationParser ||
        parser instanceof BooleanVariableDeclarationParser ||
        parser instanceof StringVariableDeclarationParser) {
      
      return token.newCreatesOf(
        new TokenEffecterWithMatcher(
          TokenPredicators.parserImplements(ExpressionInterface.class),
          this::apply
        )
      );
    }
    
    if(parser instanceof VariableDeclarationsParser || 
        parser instanceof AnnotationsParser) {
      return token.newCreatesOf(
          new TokenEffecterWithMatcher(
              TokenPredicators.allMatch(),
              this::apply
          )
      );
    }
    
    if(parser instanceof BooleanSetterParser||
        parser instanceof StringSetterParser||
        parser instanceof NumberSetterParser) {
      
      return token.newCreatesOf(
          new TokenEffecterWithMatcher(
            TokenPredicators.parserImplements(ExpressionInterface.class),
            this::apply
      ));
    }
    
    if(parser instanceof AnnotationsParser) {
      return token.newCreatesOf(
          new TokenEffecterWithMatcher(
              TokenPredicators.parsers(AnnotationParametersParser.class),
              this::apply
          )
      );
    }
    
    if(parser instanceof AnnotationParser) {
        List<Token> collect = token.flatten().stream()
          .filter(TokenPredicators.parsers(AnnotationParameterParser.class))
          .map(this::apply)
          .collect(Collectors.toList());
        
        return token.newCreatesOf(collect);
    }
    
    if(parser instanceof AnnotationParameterParser) {
      
      return token.newCreatesOf(
          token.getChild(TokenPredicators.parsers(IdentifierParser.class)),
          apply(token.getChild(TokenPredicators.parserImplements(ExpressionInterface.class)))
      );
    }


    
    if(parser instanceof ArgumentSuccessorParser) {
      token = ArgumentSuccessorParser.extractParameter(token);
      return apply(token);
    }
    
    if(parser instanceof ArgumentChoiceParser) {
      token = ChoiceInterface.choiced(token);
      return apply(token);
    }
    
    if(
      parser instanceof StrictTypedNumberExpressionParser || 
      parser instanceof NumberExpressionParser || 
      parser instanceof StrictTypedNumberTermParser ||
      parser instanceof NumberTermParser ||
      parser instanceof StrictTypedBooleanExpressionParser ||
      parser instanceof BooleanExpressionParser ||
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
        parser instanceof StrictTypedNumberFactorParser ||
        parser instanceof NumberFactorParser
        ) {
      
      return factor(token);
      
    }else if(parser instanceof NumberCaseExpressionParser){
      
      List<Token> casefactors = token.filteredChildren.stream()
        .filter(child-> child.parser instanceof NumberCaseFactorParser)
        .map(this::apply)
        .collect(Collectors.toList());
      return token.newCreatesOf(casefactors);
      
    }else if(parser instanceof NumberCaseFactorParser){
      
      return token.newCreatesOf(
        apply(NumberCaseFactorParser.getBooleanExpression(token)),
        apply(NumberCaseFactorParser.getExpression(token))
      );
      
    }else if(parser instanceof NumberDefaultCaseFactorParser){
      
      return apply(NumberCaseFactorParser.getExpression(token));

    }else if(parser instanceof BooleanCaseExpressionParser){
      
      List<Token> casefactors = token.filteredChildren.stream()
        .filter(child-> child.parser instanceof BooleanCaseFactorParser)
        .map(this::apply)
        .collect(Collectors.toList());
      return token.newCreatesOf(casefactors);
      
    }else if(parser instanceof BooleanCaseFactorParser){
      
      return token.newCreatesOf(
        apply(BooleanCaseFactorParser.getBooleanExpression(token)),
        apply(BooleanCaseFactorParser.getExpression(token))
      );
      
    }else if(parser instanceof BooleanDefaultCaseFactorParser){
      
      return apply(BooleanDefaultCaseFactorParser.getExpression(token));

    }else if(parser instanceof StringCaseExpressionParser){
      
      List<Token> casefactors = token.filteredChildren.stream()
        .filter(child-> child.parser instanceof StringCaseFactorParser)
        .map(this::apply)
        .collect(Collectors.toList());
      return token.newCreatesOf(casefactors);
      
    }else if(parser instanceof StringCaseFactorParser){
      
      return token.newCreatesOf(
        apply(StringCaseFactorParser.getBooleanExpression(token)),
        apply(StringCaseFactorParser.getExpression(token))
      );
      
    }else if(parser instanceof StringDefaultCaseFactorParser){
      
      return apply(StringDefaultCaseFactorParser.getExpression(token));

    }else if(parser instanceof BooleanFactorParser || 
        parser instanceof StrictTypedBooleanFactorParser ||
        parser instanceof BooleanExpression
        ) {
      
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
    
    Parser parser = operator.parser;
    
//    if(parser instanceof ExclusiveNakedVariableParser) {
//      
//      TypedToken<? extends VariableParser> resolveTypedVariable = 
//          VariableTypeResolver.resolveTypedVariable(token.typed(ExclusiveNakedVariableParser.class));
//      
//      parser = resolveTypedVariable.getParser();
//    }

    
    if(parser instanceof StringLiteralParser){
      
      return operator;
      
    }else if(parser instanceof StringVariableParser|| 
        parser instanceof NakedVariableParser || parser instanceof ExclusiveNakedVariableParser){
      
      return operator;
      
    }else if(parser instanceof ParenthesesParser){
      
      return apply(((ParenthesesParser)parser).getInnerParserParsed(operator));

    }else if(parser instanceof TrimParser){
      
      return operator.newCreatesOf(apply(TrimParser.getInnerParserParsed(operator)));

    }else if(parser instanceof ToUpperCaseParser){
      
      return operator.newCreatesOf(apply(ToUpperCaseParser.getInnerParserParsed(operator)));

    }else if(parser instanceof ToLowerCaseParser){
      
      return operator.newCreatesOf(apply(ToLowerCaseParser.getInnerParserParsed(operator)));

    }else if(parser instanceof StringIfExpressionParser) {
      Token booleanExpression = IfExpressionParser.getBooleanExpression(operator);
      return operator.newCreatesOf(
          apply(booleanExpression),
          apply(IfExpressionParser.getThenExpression(operator , StringExpression.class , booleanExpression)),
          apply(IfExpressionParser.getElseExpression(operator , StringExpression.class , booleanExpression))
        );
    }else if(parser instanceof StringMatchExpressionParser){
      
      return operator.newCreatesOf(
        apply(StringMatchExpressionParser.getCaseExpression(operator)),
        apply(StringMatchExpressionParser.getDefaultExpression(operator))
      );
    }else if(parser instanceof MethodInvocationParser) {
      
      return extracteMethodInvocation(operator);
      
    }else if(parser instanceof SideEffectExpressionParser){
      
      Token extractParameters = extractParameters(SideEffectExpressionParser.getParametersClause(operator));
      Optional<Token> firstParameter = extractFirstParmeter(extractParameters);
      Token returningClause = SideEffectExpressionParser.getReturningClause(operator,firstParameter);
      
      return operator.newCreatesOf(
//          returning causeをtoken化する。
//          ただし、optionalなのでemptyの場合はreturn as number default 1stParameter　 にする
          returningClause,
          SideEffectExpressionParser.getMethodClause(operator),
          extractParameters
          
      );

    }
    throw new IllegalArgumentException();
  }

  private Token factor(Token token) {
    
    Token operator = ChoiceInterface.choiced(token);
    Parser parser = operator.parser;
    
//    if(parser instanceof ExclusiveNakedVariableParser) {
//      
//      TypedToken<? extends VariableParser> resolveTypedVariable = 
//          VariableTypeResolver.resolveTypedVariable(token.typed(ExclusiveNakedVariableParser.class));
//      
//      parser = resolveTypedVariable.getParser();
//    }

    if(parser instanceof NumberParser){
      
      return clearChildren(operator);
      
    }else if(parser instanceof NakedVariableParser ){
      
      return clearChildren(operator);
      
    }else if(parser instanceof NumberVariableParser){
//      Token choiced = ChoiceInterface.choiced(operator);
      return operator;
      
    }else if(parser instanceof NumberIfExpressionParser){
      
      Token booleanExpression = IfExpressionParser.getBooleanExpression(operator);
      return operator.newCreatesOf(
        apply(booleanExpression),
        apply(IfExpressionParser.getThenExpression(operator , NumberExpression.class , booleanExpression)),
        apply(IfExpressionParser.getElseExpression(operator , NumberExpression.class , booleanExpression))
      );
      
    }else if(parser instanceof NumberMatchExpressionParser){
      
      return operator.newCreatesOf(
        apply(NumberMatchExpressionParser.getCaseExpression(operator)),
        apply(NumberMatchExpressionParser.getDefaultExpression(operator))
      );
      
    }else if(parser instanceof ParenthesesParser){
      
      return apply(((ParenthesesParser)parser).getInnerParserParsed(operator));
      
    }else if(parser instanceof SinParser){
      
      return operator.newCreatesOf(apply(SinParser.getExpression(operator)));
      
    }else if(parser instanceof CosParser){
      
      return operator.newCreatesOf(apply(CosParser.getExpression(operator)));
      
    }else if(parser instanceof TanParser){
      
      return operator.newCreatesOf(apply(TanParser.getExpression(operator)));
      
    }else if(parser instanceof SquareRootParser){
      
      return operator.newCreatesOf(apply(SquareRootParser.getExpression(operator)));
      
    }else if(parser instanceof MinParser){
      
      return operator.newCreatesOf(
        apply(MinParser.getLeftExpression(operator)),
        apply(MinParser.getRightExpression(operator))
      );

    }else if(parser instanceof MaxParser){
      
      return operator.newCreatesOf(
        apply(MaxParser.getLeftExpression(operator)),
        apply(MaxParser.getRightExpression(operator))
      );

    }else if(parser instanceof RandomParser){
      
      return operator;

    }else if(parser instanceof FactorOfStringParser){
      
      Token choiceToken = operator.filteredChildren.get(0);
      
      if(choiceToken.parser instanceof StringLengthParser) {
        
        return choiceToken.newCreatesOf(apply(choiceToken.filteredChildren.get(2)));
        
//      }else if(choiceToken.parser instanceof StringIndexOfParser) {
//        
//        return choiceToken;
      }
    } else if (parser instanceof ToNumParser) {
      return operator.newCreatesOf(
          apply(ToNumParser.getLeftExpression(operator)),
          apply(ToNumParser.getRightExpression(operator))
      );
      
    }else if(parser instanceof SideEffectExpressionParser){
      
      Token extractParameters = extractParameters(SideEffectExpressionParser.getParametersClause(operator));
      Optional<Token> firstParameter = extractFirstParmeter(extractParameters);
      Token returningClause = SideEffectExpressionParser.getReturningClause(operator,firstParameter);
      
      // defaultを廃止したのでコメントアウト。実装ヒントとして残しておく
//      Token returning = apply(extractReturning(returningClause));
      
      return operator.newCreatesOf(
//          returning causeをtoken化する。
//          ただし、optionalなのでemptyの場合はreturn as number default 1stParameter　 にする
          returningClause,
          SideEffectExpressionParser.getMethodClause(operator),
          extractParameters
          
      );

    }else if(parser instanceof MethodInvocationParser){
      String path = token.getPath();
      return extracteMethodInvocation(operator);

    }
    throw new IllegalArgumentException();
  }

  private Token extracteMethodInvocation(Token operator) {
    Optional<Token> extractParameters = MethodInvocationParser.getParametersClause(operator)
        .map(this::extractParameters);
    
    Token methodNameToken = MethodInvocationParser.getMethodName(operator);
    String methodName = methodNameToken.getToken().get();
    
    Token tinyExpressionParserToken = operator.getAncestor(TokenPredicators.parsers(TinyExpressionParser.class));
    
    Optional<Token> returningTypeHint = TinyExpressionParser.returningTypeHint(tinyExpressionParserToken, methodName);
    if(returningTypeHint.isEmpty()) {
      throw new ParseException(methodName + " is not declared");
    }
    
    
    // defaultを廃止したのでコメントアウト。実装ヒントとして残しておく
//      Token returning = apply(extractReturning(returningClause));
    
    return 
        extractParameters.isEmpty() ?
          operator.newCreatesOf(
            returningTypeHint.get(),
            methodNameToken
          ):
          operator.newCreatesOf(
            returningTypeHint.get(),
            methodNameToken,
            extractParameters.get()
          );
  }
  
  private Optional<Token> extractFirstParmeter(Token extractParameters) {
    List<Token> filteredChildren = extractParameters.filteredChildren;
    return filteredChildren.isEmpty() ?
        Optional.empty():
        Optional.of(filteredChildren.get(0));
  }

  Token extractReturning(Token returningClause) {
//    ExpressionParserかBooleanExpressionParserかStringExpressionParserを探す。
//    ただし幅優先で探さなければならないのでTokenにdepth/breadthのどちらでlistを作るかを指定するものを追加する。
//
//    その後、defaultを無くしたのでこの処理はいらなくなるけどSearchFirstの例として実装のヒントとして残しておく
    
    List<Token> flatten = returningClause.flatten(ScanDirection.Breadth);
    Token expressionToken = flatten.stream()
      .filter(token->{
        return (token.parser instanceof NumberExpression ||
            token.parser instanceof BooleanExpression ||
            token.parser instanceof StringExpression
            );
      })
      .findFirst().orElseThrow();

    return expressionToken;
  }

  Token extractParameters(Token argumentsToken) {
    
    List<Token> appliedChildren = argumentsToken.filteredChildren.stream()
        .filter(token-> false == token.parser instanceof CommaParser)
        .map(this::apply)
        .collect(Collectors.toList());
    
    return argumentsToken.newCreatesOf(appliedChildren);
  }

  private Token booleanExpression(Token token) {
    
    Token operator = ChoiceInterface.choiced(token);
    Parser parser = operator.parser;
    
//    if(parser instanceof ExclusiveNakedVariableParser) {
//      
//      TinyExpressionTokens tinyExpressionTokens = 
//          new TinyExpressionTokens(operator.getAncestor(TokenPredicators.parsers(TinyExpressionParser.class)));
//      
//      
//      Optional<VariableParser> resolveTypedVariable = 
//          VariableTypeResolver.resolveTypedVariable(token.typed(ExclusiveNakedVariableParser.class),tinyExpressionTokens.variableDeclarationByVariableName);
//      
//      if(resolveTypedVariable.isPresent()) {
//        parser = resolveTypedVariable.get();
//      }
//    }
    
    if(parser instanceof TrueTokenParser ||
      parser instanceof FalseTokenParser) {
      return operator;
      
    }else if(parser instanceof NotBooleanExpressionParser) {
      
      Token booleanExpression = NotBooleanExpressionParser.getBooleanExpression(operator);
      return operator.newCreatesOf(apply(booleanExpression));
      
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

    }else if(parser instanceof NumberEqualEqualExpressionParser) {
      
      return operator.newCreatesOf(
        apply(NumberEqualEqualExpressionParser.getLeftExpression(operator)),
        apply(NumberEqualEqualExpressionParser.getRightExpression(operator))
      );

    }else if(parser instanceof NumberNotEqualExpressionParser) {
      
      return operator.newCreatesOf(
        apply(NumberNotEqualExpressionParser.getLeftExpression(operator)),
        apply(NumberNotEqualExpressionParser.getRightExpression(operator))
      );

    }else if(parser instanceof NumberGreaterOrEqualExpressionParser) {
      
      return operator.newCreatesOf(
        apply(NumberGreaterOrEqualExpressionParser.getLeftExpression(operator)),
        apply(NumberGreaterOrEqualExpressionParser.getRightExpression(operator))
      );

    }else if(parser instanceof NumberLessOrEqualExpressionParser) {
      
      return operator.newCreatesOf(
        apply(NumberLessOrEqualExpressionParser.getLeftExpression(operator)),
        apply(NumberLessOrEqualExpressionParser.getRightExpression(operator))
      );

    }else if(parser instanceof NumberGreaterExpressionParser) {
      
      return operator.newCreatesOf(
        apply(NumberGreaterExpressionParser.getLeftExpression(operator)),
        apply(NumberGreaterExpressionParser.getRightExpression(operator))
      );

    }else if(parser instanceof NumberLessExpressionParser) {
      
      return operator.newCreatesOf(
        apply(NumberLessExpressionParser.getLeftExpression(operator)),
        apply(NumberLessExpressionParser.getRightExpression(operator))
      );

    }else if(parser instanceof BooleanIfExpressionParser) {
      
      Token booleanExpression = IfExpressionParser.getBooleanExpression(operator);
      return operator.newCreatesOf(
          apply(booleanExpression),
          apply(IfExpressionParser.getThenExpression(operator , BooleanExpression.class , booleanExpression)),
          apply(IfExpressionParser.getElseExpression(operator , BooleanExpression.class , booleanExpression))
        );
      
    }else if(parser instanceof BooleanMatchExpressionParser){
      
      return operator.newCreatesOf(
        apply(BooleanMatchExpressionParser.getCaseExpression(operator)),
        apply(BooleanMatchExpressionParser.getDefaultExpression(operator))
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
        Token argument = StringExpressionMethodParser.getStringExpressions(StringMethodExpressionParser.getMethod(operatorWithString));
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
      
    }else if(parser instanceof MethodInvocationParser){
      
      return extracteMethodInvocation(operator);
      
    }else if(parser instanceof SideEffectExpressionParser){
      
      Token extractParameters = extractParameters(SideEffectExpressionParser.getParametersClause(operator));
      Optional<Token> firstParameter = extractFirstParmeter(extractParameters);
      Token returningClause = SideEffectExpressionParser.getReturningClause(operator,firstParameter);
      
      return operator.newCreatesOf(
//          returning causeをtoken化する。
//          ただし、optionalなのでemptyの場合はreturn as number default 1stParameter　 にする
          returningClause,
          SideEffectExpressionParser.getMethodClause(operator),
          extractParameters
          
      );
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