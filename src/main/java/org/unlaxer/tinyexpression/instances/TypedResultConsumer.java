package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.Calculator;

public interface TypedResultConsumer<T>{
  
  void accept(Calculator calclator , String formulaName , T result);
}