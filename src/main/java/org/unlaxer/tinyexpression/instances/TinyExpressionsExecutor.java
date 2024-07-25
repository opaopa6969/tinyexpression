package org.unlaxer.tinyexpression.instances;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public class TinyExpressionsExecutor {
  
  @SuppressWarnings("unchecked")
  public void execute(
      TenantID tenantID,
      CalculationContext calculationContext,
      ResultConsumer resultConsumer , 
      TinyExpressionInstancesCache tinyExpressionInstancesCache ,
      Comparator<Calculator<?>> comparator,
      Predicate<Calculator<?>> passFilter,
      ClassLoader classLoader) {
    
    List<Calculator<?>> list = tinyExpressionInstancesCache.get(tenantID , comparator , passFilter , classLoader);
    
    list.forEach(calculator->{
      Object result = calculator.apply(calculationContext);
      FormulaInfo formulaInfo = FormulaInfo.get(calculator);
      
      if(result instanceof Number) {
        resultConsumer.accept(calculationContext,(Calculator<? extends Number>)calculator, formulaInfo, (Number) result);
      }else if(result instanceof String) {
        resultConsumer.accept(calculationContext,(Calculator<String>)calculator, formulaInfo, (String) result);
      }else if(result instanceof Boolean) {
        resultConsumer.accept(calculationContext,(Calculator<Boolean>)calculator, formulaInfo, (Boolean) result);
      }else if(result instanceof Object) {
        resultConsumer.accept(calculationContext,(Calculator<Object>)calculator, formulaInfo, result);
      }else {
        throw new IllegalArgumentException("no match:" + result.getClass());
      }
    });
  }
}