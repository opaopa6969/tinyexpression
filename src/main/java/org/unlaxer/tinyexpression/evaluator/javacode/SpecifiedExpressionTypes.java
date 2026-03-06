package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.tinyexpression.parser.ExpressionType;

public record SpecifiedExpressionTypes(ExpressionType resultType,ExpressionType numberType ) {
//  public SpecifiedExpressionTypes{
//    if(resultType == null) {
//      resultType = ExpressionTypes._float;
//    }
//    if(numberType == null) {
//      numberType = ExpressionTypes._float;
//    }
//  }
}