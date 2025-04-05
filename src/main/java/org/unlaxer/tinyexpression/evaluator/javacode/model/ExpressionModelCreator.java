package org.unlaxer.tinyexpression.evaluator.javacode.model;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.ParseException;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionsParser;
import org.unlaxer.tinyexpression.parser.FactorOfStringParser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser.ThenAndElse;
import org.unlaxer.tinyexpression.parser.LeftAndOperatorPlusRights;
import org.unlaxer.tinyexpression.parser.MethodInvocationParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.Opecode;
import org.unlaxer.tinyexpression.parser.OpecodeParser;
import org.unlaxer.tinyexpression.parser.Opecodes;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedNumberFactorParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
import org.unlaxer.tinyexpression.parser.ToNumParser;
import org.unlaxer.tinyexpression.parser.VariableDeclarationMatchedTokenParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser.VariableInfo;
import org.unlaxer.tinyexpression.parser.numbertype.AbstractNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberFactorParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberIfExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberPrefixedVariableParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberSuffixedVariableParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberTypeHintParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpression;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpressionParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringLengthParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class ExpressionModelCreator {

  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static ExpressionModel extract(TypedToken<TinyExpressionParser> tinyExpressionToken,
      SpecifiedExpressionTypes specifiedExpressionTypes){

    TypedToken<ExpressionsParser> childWithParserTyped = tinyExpressionToken.getChildWithParserTyped(ExpressionsParser.class);
    TypedToken<ExpressionInterface> typed = ChoiceInterface.choiced(childWithParserTyped).typed(ExpressionInterface.class);
    return apply(typed , specifiedExpressionTypes);
  }

  public static ExpressionModel apply(TypedToken<? extends ExpressionInterface> typedToken ,SpecifiedExpressionTypes specifiedExpressionTypes) {

    ExpressionInterface parser = typedToken.getParser();
    if(parser instanceof LeftAndOperatorPlusRights) {

      List<Token> originalTokens = typedToken.filteredChildren;
      Iterator<Token> iterator = originalTokens.iterator();

      TypedToken<ExpressionInterface> next = iterator.next().typed(ExpressionInterface.class);
      ExpressionModel left = apply(next , specifiedExpressionTypes);

      ExpressionModel lastOpearatorAndOperands = left;

      while(iterator.hasNext()){
        TypedToken<OpecodeParser> opecodeToken = iterator.next().typed(OpecodeParser.class);
        Opecode opecode = opecodeToken.getParser().opecode();

        ExpressionModel right  = apply(iterator.next().typed(ExpressionInterface.class),specifiedExpressionTypes);
        lastOpearatorAndOperands = new ExpressionModel(opecode,  lastOpearatorAndOperands , right);
      }
      return lastOpearatorAndOperands;

    }

    if(parser instanceof StrictTypedNumberFactorParser ||
        parser instanceof NumberFactorParser) {

      return numberFactor(typedToken.typed(NumberFactorParser.class),specifiedExpressionTypes);
    }



//    Optional<TypedToken<NumberExpressionParser>> numberExpression = extractNumberExpression(typedToken);
//        ExpressionModel expression  = numberExpression.map(x->NumberExpressionModel.applyNumber(x, specifiedExpressionTypes))
//            .orElseGet(()->extractBooleanExpression(typedToken).map(ExpressionModel::applyBoolean)
//                .orElseGet(()->extractBooleanExpression(typedToken).map(ExpressionModel::applyBoolean).orElseThrow()
//                ));
//
//        ;
//
//        return expression;
  }


  private static ExpressionModel numberFactor(TypedToken<NumberFactorParser> typedToken,
      SpecifiedExpressionTypes specifiedExpressionTypes) {

    Token operator = ChoiceInterface.choiced(typedToken);
    Parser parser = operator.parser;

//    if(parser instanceof ExclusiveNakedVariableParser) {
//
//      TypedToken<? extends VariableParser> resolveTypedVariable =
//          VariableTypeResolver.resolveTypedVariable(token.typed(ExclusiveNakedVariableParser.class));
//
//      parser = resolveTypedVariable.getParser();
//    }

    if(parser instanceof AbstractNumberParser){

      String number = typedToken.getToken().get();
      ExpressionType expressionType = ((AbstractNumberParser) parser).expressionType();

      return new ExpressionModel(Opecodes.numberValue, expressionType , number);


    }else if(parser instanceof NakedVariableParser ){

      TypedToken<NakedVariableParser> token = operator.typed(NakedVariableParser.class);
      String variableName = token.apply(VariableParser::getVariableName);

      ExpressionType nakedVariableType = specifiedExpressionTypes.nakedVariableType();
//      return nakedVariableType.isNumber() ?
//           new ExpressionModel(Opecodes.numberVariable, nakedVariableType , variableName):
        return new ExpressionModel(Opecodes.numberVariable, nakedVariableType , variableName);

    }else if(parser instanceof NumberVariableParser){
      TypedToken<VariableParser> choiced = ChoiceInterface.choiced(operator).typed(VariableParser.class);

      if(choiced.getParser() instanceof VariableDeclarationMatchedTokenParser) {
        VariableInfo variableInfo = choiced
             .typed(VariableDeclarationMatchedTokenParser.class)
             .apply(VariableDeclarationMatchedTokenParser::variableInfo);
        return new ExpressionModel(Opecodes.numberVariable, variableInfo.expressionType , variableInfo.name);
      }else if(choiced.getParser() instanceof NumberPrefixedVariableParser ||
          choiced.getParser() instanceof NumberSuffixedVariableParser ) {

        TypedToken<NumberTypeHintParser> childWithParserTyped = choiced.getChildWithParserTyped(NumberTypeHintParser.class);

        ExpressionType expressionType = childWithParserTyped.apply(NumberTypeHintParser::expressionType);
        String varName = choiced.apply(VariableParser::getVariableName);

        return new ExpressionModel(Opecodes.numberVariable, expressionType , varName);

      }else {
          throw new ParseException();
      }

    }else if(parser instanceof NumberIfExpressionParser){

      TypedToken<BooleanExpression> booleanExpression = IfExpressionParser.getBooleanExpression(operator);

      ThenAndElse thenAndElse = 
          IfExpressionParser.getThenAndElse(operator.typed(IfExpressionParser.class), NumberExpression.class, booleanExpression);
      
      ExpressionType concreteNumberType = NumberExpression.resolveConcreteType(thenAndElse.thenToken().typed(NumberExpression.class))
        .or(()->NumberExpression.resolveConcreteType(thenAndElse.elseToken().typed(NumberExpression.class)))
        .orElse(specifiedExpressionTypes.numberType());
      
      return new ExpressionModel(
          Opecodes.numberIf,
          concreteNumberType,
          applyBoolean(booleanExpression),
          apply(thenAndElse.thenToken(), specifiedExpressionTypes),
          apply(thenAndElse.elseToken(), specifiedExpressionTypes)
      );

    }else if(parser instanceof NumberMatchExpressionParser){
      
      new ExpressionModel(Opecodes.numberCase, null);

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
      String path = typedToken.getPath();
      return extracteMethodInvocation(operator);

    }
    throw new IllegalArgumentException();
  }



  public static ExpressionModel applyBoolean(TypedToken<BooleanExpression> typedToken ) {


        ;

        return null;
  }

  public static ExpressionModel applyString(TypedToken<StringExpression> typedToken ) {


    ;

    return null;
  }

  public static class NumberExpressionModel extends ExpressionModel{

    public NumberExpressionModel(ExpressionModel parent, ExpressionInterface opecodeToken,
        ExpressionType expressionType, ExpressionModel leftOperand, ExpressionModel rightOperand) {
      super(parent, opecodeToken, expressionType, leftOperand, rightOperand);
    }

    public NumberExpressionModel(ExpressionModel parent, ExpressionInterface opecodeToken,
        ExpressionType expressionType, ExpressionModel leftOperand) {
      super(parent, opecodeToken, expressionType, leftOperand);
    }

    public static ExpressionModel applyNumber(TypedToken<NumberExpressionParser> typedToken ,SpecifiedExpressionTypes specifiedExpressionTypes) {

      List<Token> originalTokens = typedToken.filteredChildren;
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
      ;

      return new NumberExpressionModel(null, null, null, null);
    }






  }






  @TokenExtractor
  public static TypedToken<ExpressionInterface> extractExpressionToken(TypedToken<ExpressionsParser> thisParserParsed){
    return ChoiceInterface.choiced(thisParserParsed).typed(ExpressionInterface.class);
  }

  @TokenExtractor
  public static Optional<TypedToken<NumberExpressionParser>> extractNumberExpression(TypedToken<ExpressionsParser> thisParserParsed){
    TypedToken<ExpressionInterface> expressionToken = extractExpressionToken(thisParserParsed);
    return expressionToken.getParser() instanceof NumberExpressionParser ?
        Optional.of(expressionToken.typed(NumberExpressionParser.class)):
        Optional.empty();
  }

  @TokenExtractor
  public static Optional<TypedToken<BooleanExpressionParser>> extractBooleanExpression(TypedToken<ExpressionsParser> thisParserParsed){
    TypedToken<ExpressionInterface> expressionToken = extractExpressionToken(thisParserParsed);
    return expressionToken.getParser() instanceof BooleanExpressionParser ?
        Optional.of(expressionToken.typed(BooleanExpressionParser.class)):
        Optional.empty();
  }

  @TokenExtractor
  public static Optional<TypedToken<StringExpressionParser>> extractStringExpression(TypedToken<ExpressionsParser> thisParserParsed){
    TypedToken<ExpressionInterface> expressionToken = extractExpressionToken(thisParserParsed);
    return expressionToken.getParser() instanceof StringExpressionParser ?
        Optional.of(expressionToken.typed(StringExpressionParser.class)):
        Optional.empty();
  }

}



}
