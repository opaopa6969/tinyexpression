package org.unlaxer.tinyexpression.loader;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.Function;

import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class FormulaInfoAdditionalFields{
  
  private final String multiTenancyAttributeName;
  private final LinkedHashSet<String> additionalAttributeNames;
  private final Function<FormulaInfo,String> nameExtractor;
  private volatile ExecutionBackend executionBackend = ExecutionBackend.JAVA_CODE;

  public FormulaInfoAdditionalFields(String multiTenancyAttributeName,Function<FormulaInfo,String> nameExtractor) {
    super();
    this.multiTenancyAttributeName = multiTenancyAttributeName;
    additionalAttributeNames = new LinkedHashSet<>();
    this.nameExtractor = nameExtractor;
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
  
  /**
   * 
   * @param formulaInfo
   * @return
   */
  public String getName(FormulaInfo formulaInfo) {
    
//    String checkKind = formulaInfo.extraValueByKey.get("checkKind");
//    return checkKind != null ? checkKind : formulaInfo.calculatorName;
    return nameExtractor.apply(formulaInfo);
  }

  public ExecutionBackend executionBackend() {
    return executionBackend;
  }

  public FormulaInfoAdditionalFields setExecutionBackend(ExecutionBackend executionBackend) {
    this.executionBackend = executionBackend == null ? ExecutionBackend.JAVA_CODE : executionBackend;
    return this;
  }
//  public String formulaNameAttributeName() {
//    return formulaNameAttributeName;
//  }
}
