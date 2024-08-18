package org.unlaxer.tinyexpression.instances;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.unlaxer.compiler.CompileError;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public class TinyExpressionsExecutor {
  
  final Consumer<Map<String,Object>> mapConsumer;
  
  
  public TinyExpressionsExecutor() {
    super();
    mapConsumer = x->{};
  }

  public TinyExpressionsExecutor(Consumer<Map<String, Object>> mapConsumer) {
    super();
    this.mapConsumer = mapConsumer;
  }

  @SuppressWarnings("unchecked")
  public List<CalculationResult> execute(
      TenantID tenantID,
      CalculationContext calculationContext,
      ResultConsumer resultConsumer , 
      TinyExpressionInstancesCache tinyExpressionInstancesCache ,
      Comparator<Calculator<?>> comparator,
      Predicate<Calculator<?>> passFilter,
      ClassLoader classLoader) {
    
    List<Calculator<?>> list = null ;
    try {
      
      list = tinyExpressionInstancesCache.get(tenantID , comparator , passFilter , classLoader);
      
      return list.stream().map(calculator->{
        
        Object result;
        Throwable throwable;
        try {
          
          calculator.before(calculationContext);
          result = calculator.apply(calculationContext);
          calculator.after(calculationContext);

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
          return new CalculationResult(calculator,result,null);
        }catch(Throwable e) {
          throwable = e;
          return new CalculationResult(calculator,null,throwable);
        }
      }).collect(Collectors.toList());
    }catch (Exception e) {
      List<Calculator<?>> _list = list;
      throw new CompileError(e,map->{
        map.put(CompileError.ComileErrorContextKey.calculatorList.name(), _list);
      });
    }
  }
}