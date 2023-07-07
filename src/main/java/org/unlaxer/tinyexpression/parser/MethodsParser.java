package org.unlaxer.tinyexpression.parser;

import java.util.function.Supplier;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyZeroOrMore;

public class MethodsParser extends JavaStyleDelimitedLazyZeroOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return MethodChoiceParser::new;
  }

  @Override
  public java.util.Optional<Parser> getLazyTerminatorParser() {
    return java.util.Optional.empty();
  }
  
}