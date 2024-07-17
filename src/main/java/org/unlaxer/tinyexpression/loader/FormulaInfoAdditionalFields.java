package org.unlaxer.tinyexpression.loader;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;

public class FormulaInfoAdditionalFields{
  
  private final String multiTenancyAttributeName;
  private final String formulaNameAttributeName;
  private final LinkedHashSet<String> additionalAttributeNames;

  public FormulaInfoAdditionalFields(String multiTenancyAttributeName, String formulaNameAttributeName) {
    super();
    this.multiTenancyAttributeName = multiTenancyAttributeName;
    this.formulaNameAttributeName = formulaNameAttributeName;
    additionalAttributeNames = new LinkedHashSet<>();
  }
  
  public FormulaInfoAdditionalFields(String formulaNameAttributeName) {
    super();
    this.multiTenancyAttributeName = null;
    this.formulaNameAttributeName = formulaNameAttributeName;
    additionalAttributeNames = new LinkedHashSet<>();
  }
  
  public Collection<String> additionalAttributeNames(){
    return additionalAttributeNames;
  }
  
  public void addAttributeName(String attributeName) {
    additionalAttributeNames.add(attributeName);
  }
  
  public void addAttributeName(Collection<String> attributeNames) {
    additionalAttributeNames.addAll(attributeNames);
  }
  
  public Optional<String> multiTenancyAttributeName(){
    return Optional.ofNullable(multiTenancyAttributeName);
  }
  public String formulaNameAttributeName() {
    return formulaNameAttributeName;
  }
}