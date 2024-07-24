package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.Calculator;

public interface BooleanResultConsumer{
  
  void accept(Calculator<Boolean> calclator , String formulaName , boolean result);
}