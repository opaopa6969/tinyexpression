package org.unlaxer.tinyexpression.instances;

import org.unlaxer.Name;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;

public class TinyExpressionsExecutor {
  
  
  @Inject
  public CustomFunctionEvaluatorService(CustomFunctionsCache customFunctionsCache) {
    this.customFunctionsCache = customFunctionsCache;
  }

  public void evaluate(CustomFunctionEvaluationRequest customFunctionEvaluationRequest, CheckResult checkResult) {
    // There are 3 types of custom functions available, and we run them in a specific order
    // Each type has access to the execution results of the previous type
    // Normal - standard CF
    // Post Process - executes after normal (and can use those results)
    // Post Process Relative Suspicious - uses all the data to determine the rsv score (危険度)

    String appliedCFTag = customFunctionsCache.getActiveTagName(customFunctionEvaluationRequest.siteID);

    evaluateNormal(customFunctionEvaluationRequest, appliedCFTag);
    evaluatePostProcess(customFunctionEvaluationRequest, appliedCFTag);
    calculatePostProcessRelativeSuspicious(customFunctionEvaluationRequest, appliedCFTag);

    checkResult.setTags(appliedCFTag);
  }

  private void evaluateNormal(CustomFunctionEvaluationRequest request, String appliedCFTag) {
    customFunctionsCache.get(request.siteID, request.timestamp, CalculatorKind.normal, Optional.of(appliedCFTag))
        .forEach((checkKind, calculator) -> {
          int res = calculator.apply(request.calculationContext).intValue();
          request.calculationContext.set("calculated_" + checkKind.name(), res);
          request.response.addSuspiciousByKind(checkKind.name(), res);
        }
    );
  }

  private void evaluatePostProcess(CustomFunctionEvaluationRequest request, String appliedCFTag) {
    customFunctionsCache.get(request.siteID, request.timestamp, CalculatorKind.postProcess, Optional.of(appliedCFTag))
        .forEach((checkKind, calculator) -> {
              int res = calculator.apply(request.calculationContext).intValue();
              request.calculationContext.set(checkKind.name(), res);
              request.response.addSuspiciousByKind(checkKind.name(), res);
            }
        );
  }

  private void calculatePostProcessRelativeSuspicious(CustomFunctionEvaluationRequest request, String appliedCFTag) {
    customFunctionsCache.get(request.siteID, request.timestamp, CalculatorKind.postProcessRelative, Optional.of(appliedCFTag))
        .values()
        .stream()
        .findFirst()
        .ifPresent(calc -> {
          int calculatedRelativeSuspiciousValue = calc.apply(request.calculationContext).intValue();
          request.response.setRelativeSuspiciousValue(calculatedRelativeSuspiciousValue);
        });

    // Otherwise just use whatever has been set up until now
  }

  public void evaluatePreProcess(CustomFunctionEvaluationRequest request, CheckResult checkResult){
    String appliedCFTag = customFunctionsCache.getActiveTagName(request.siteID);

    customFunctionsCache.get(request.siteID, request.timestamp, CalculatorKind.preProcess, Optional.of(appliedCFTag))
        .forEach((checkKind, calculator) -> {
              int res = calculator.apply(request.calculationContext).intValue();
              request.calculationContext.set(checkKind.name(), res);
            }
        );

    checkResult.setTags(appliedCFTag);
  }
  
  public void execute(ResultConsumer resultConsumer) {
    
  }
  
  

  
  
  public interface TenantID{
    
    int asNumber();
    String asString();
  }
  
  
  public interface NumberResultConsumer{
    
    void set(Calculator<? extends Number> calclator , Name name , Number result);
  }
  
  public interface StringResultConsumer{
    
    void set(Calculator<String> calclator , Name name , String result);
  }
  
  public interface BooleanResultConsumer{
    
    void set(Calculator<Boolean> calclator , Name name , boolean result);
  }
  
  public interface ObjectResultConsumer{
    
    void set(Calculator<Object> calclator , Name name , Object result);
  }
  
  public interface TypedResultConsumer<T>{
    
    void set(Calculator<T> calclator , Name name , T result);
  }
  
  public interface ResultConsumer extends NumberResultConsumer , StringResultConsumer,
    BooleanResultConsumer , ObjectResultConsumer {}
  
  
  
  
  public interface UpdatableByCustomFunction {

    void setRelativeSuspiciousValue(int rsv);
    void addSuspiciousByKind(String checkKind, int value);
    default void addSuspiciousByKind(CheckKind checkKind, Score value) {
      this.addSuspiciousByKind(checkKind.name(), value.value());
    }
  }
  
  


}
