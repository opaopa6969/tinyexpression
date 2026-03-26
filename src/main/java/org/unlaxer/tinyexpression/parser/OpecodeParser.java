package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

public interface OpecodeParser extends ExpressionInterface{

  public Optional<Opecode> opecode();

}