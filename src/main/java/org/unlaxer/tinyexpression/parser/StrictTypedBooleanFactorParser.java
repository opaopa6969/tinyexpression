package org.unlaxer.tinyexpression.parser;

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