package org.unlaxer.tinyexpression.evaluator.javacode.model;

import org.unlaxer.TypedToken;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;

public record  CalcurationModel(
    TypedToken<TinyExpressionParser> tinyExpressionToken ,
    JavaCodes javaCodes,
    Imports imports,
    ExpressionModel expression
    ) {


  public static CalcurationModel create(TypedToken<TinyExpressionParser> tinyExpressionToken , SpecifiedExpressionTypes specifiedExpressionTypes) {

    JavaCodes codesToken = JavaCodes.extract(tinyExpressionToken);
    Imports imports = Imports.extract(tinyExpressionToken);
    ExpressionModel expression = ExpressionModelCreator.extract(tinyExpressionToken , specifiedExpressionTypes);

    return null;

  }
}