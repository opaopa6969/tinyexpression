package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;

public interface ClassNameAndIdentifierExtractor{
  ClassNameAndIdentifier extractClassNameAndIdentifier(
      Token token , TinyExpressionTokens tinyExpressionTokens);
}