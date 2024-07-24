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
        resultConsumer.accept((Calculator<? extends Number>)calculator, formulaInfo.formulaName, (Number) result);
      }else if(result instanceof String) {
        resultConsumer.accept((Calculator<String>)calculator, formulaInfo.formulaName, (String) result);
      }else if(result instanceof Boolean) {
        resultConsumer.accept((Calculator<Boolean>)calculator, formulaInfo.formulaName, (Boolean) result);
      }else if(result instanceof Object) {
        resultConsumer.accept((Calculator<Object>)calculator, formulaInfo.formulaName, result);
      }else {
        throw new IllegalArgumentException("no match:" + result.getClass());
      }
    });
  }
}