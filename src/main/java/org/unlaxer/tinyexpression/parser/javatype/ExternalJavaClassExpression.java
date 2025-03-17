package org.unlaxer.tinyexpression.parser.javatype;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface ExternalJavaClassExpression extends ExpressionInterface{

  ExpressionType expressionType(Token thisParserParsed);

}