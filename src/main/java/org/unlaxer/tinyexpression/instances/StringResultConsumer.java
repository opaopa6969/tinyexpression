package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.Calculator;

public interface StringResultConsumer{
  
  void accept(Calculator<String> calclator , String formulaName , String result);
}