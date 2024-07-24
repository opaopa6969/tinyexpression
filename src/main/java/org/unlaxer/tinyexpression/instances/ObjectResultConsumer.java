package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.Calculator;

public interface ObjectResultConsumer{
  
  void accept(Calculator<Object> calclator , String formulaName , Object result);
}