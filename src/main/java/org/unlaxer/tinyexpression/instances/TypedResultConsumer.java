package org.unlaxer.tinyexpression.instances;

import org.unlaxer.tinyexpression.Calculator;

public interface TypedResultConsumer<T>{
  
  void accept(Calculator<T> calclator , String formulaName , T result);
}