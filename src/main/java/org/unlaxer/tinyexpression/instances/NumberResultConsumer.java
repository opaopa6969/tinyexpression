package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.Calculator;

public interface NumberResultConsumer{
  
  void accept(Calculator<? extends Number> calclator , String formulaName, Number result);
}