package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

import java.util.Optional;

import org.unlaxer.TypedToken;
import org.unlaxer.tinyexpression.parser.ConcreteNumberType;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface NumberExpression extends org.unlaxer.tinyexpression.parser.NumberExpression{

  public static Optional<ExpressionType> resolveConcreteType(TypedToken<NumberExpression> token) {

    // FIXME! 変数も考慮しないといけない
    Optional<ExpressionType> expressionType = token.flatten().stream()
      .filter(token_->token_.getParser() instanceof ConcreteNumberType)
      .findFirst()
      .map(token_->token_.<ConcreteNumberType>typed(ConcreteNumberType.class))
      .map(TypedToken::getParser)
      .map(ConcreteNumberType::concreteExpressionType);
//      .orElse(specifiedExpressionTypes.numberType());
      
    return expressionType;
  }


}

