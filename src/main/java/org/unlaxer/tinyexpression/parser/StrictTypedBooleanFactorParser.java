package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;

public class StrictTypedBooleanFactorParser extends AbstractBooleanFactorParser{

  
  public StrictTypedBooleanFactorParser() {
    super();
    addTag(StrictTyped.get());
  }

    @Override
   public org.unlaxer.parser.Parsers getLazyParsers() {
     return getLazyParsers(false);
   }

   @Override
   public boolean hasNakedVariableParser() {
     return false;
   }
 }