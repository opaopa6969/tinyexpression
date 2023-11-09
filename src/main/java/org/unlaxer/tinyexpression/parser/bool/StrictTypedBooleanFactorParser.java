package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedBooleanFactorParser extends AbstractBooleanFactorParser{

  
  public StrictTypedBooleanFactorParser() {
    super();
    addTag(StrictTyped.get());
  }

    @Override
   public Parsers getLazyParsers() {
     return getLazyParsers(false);
   }

   @Override
   public boolean hasNakedVariableParser() {
     return false;
   }
 }