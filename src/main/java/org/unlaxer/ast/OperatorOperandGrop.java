package org.unlaxer.ast;

import java.util.List;
import java.util.function.Supplier;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;

public class OperatorOperandGrop extends Parsers{


  @SafeVarargs
  public OperatorOperandGrop(Class<? extends Parser>... parsers) {
    super(parsers);
    //TODO Auto-generated constructor stub
  }

  public OperatorOperandGrop(List<Class<? extends Parser>> parsers) {
    super(parsers);
    //TODO Auto-generated constructor stub
  }

  public OperatorOperandGrop(Parser... parsers) {
    super(parsers);
    //TODO Auto-generated constructor stub
  }

  @SafeVarargs
  public OperatorOperandGrop(Supplier<Parser>... parsers) {
    super(parsers);
    //TODO Auto-generated constructor stub
  }


}
