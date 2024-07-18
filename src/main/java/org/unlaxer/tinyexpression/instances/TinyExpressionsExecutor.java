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
      Predicate<Calculator<?>> passFilter) {
    
    List<Calculator<?>> list = tinyExpressionInstancesCache.get(tenantID , comparator , passFilter);
    
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
  
  public interface TenantID{
    
    int asNumber();
    public default String asString() {
      return String.valueOf(asNumber());
    }
  }
  
  
  public interface NumberResultConsumer{
    
    void accept(Calculator<? extends Number> calclator , String formulaName, Number result);
  }
  
  public interface StringResultConsumer{
    
    void accept(Calculator<String> calclator , String formulaName , String result);
  }
  
  public interface BooleanResultConsumer{
    
    void accept(Calculator<Boolean> calclator , String formulaName , boolean result);
  }
  
  public interface ObjectResultConsumer{
    
    void accept(Calculator<Object> calclator , String formulaName , Object result);
  }
  
  public interface TypedResultConsumer<T>{
    
    void accept(Calculator<T> calclator , String formulaName , T result);
  }
  
  public interface ResultConsumer extends NumberResultConsumer , StringResultConsumer,
    BooleanResultConsumer , ObjectResultConsumer {
    
    
  }
  
  
    
  public interface TinyExpressionInstancesCache extends TinyExpressionInstances{
    
    boolean clearCache(TenantID siteID);
    
    List<Calculator<?>> get(
        TenantID tenantID,
        Comparator<Calculator<?>> comparator , 
        Predicate<Calculator<?>> passFilter);
  }
  
  public interface TinyExpressionInstances {
    
    List<Calculator<?>> get(TenantID tenantID,Comparator<Calculator<?>> comparator);
  }
}