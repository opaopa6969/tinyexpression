package org.unlaxer.tinyexpression.parser.bool;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedBooleanFactorParser extends AbstractBooleanFactorParser{

  
  public StrictTypedBooleanFactorParser() {
    super();
    addTag(StrictTyped.get());
  }

    @Override
   public List<Parser> getLazyParsers() {
     return getLazyParsers(false);
   }

   @Override
   public boolean hasNakedVariableParser() {
     return false;
   }
 }