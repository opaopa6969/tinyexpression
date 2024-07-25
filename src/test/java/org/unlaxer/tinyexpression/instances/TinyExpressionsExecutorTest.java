package org.unlaxer.tinyexpression.instances;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.instances.TinyExpressionInstancesCacheTest.FileBaseTinyExpressionInstancesCache;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public class TinyExpressionsExecutorTest {
  
  public static class CheckResult{
    public Map<String,Float> suspiciousByKind = new HashMap<>();
  }
  
  public static class ResultConsumerWithCheckResult implements ResultConsumer{
    
    public final CheckResult checkResult;

    public ResultConsumerWithCheckResult(CheckResult checkResult) {
      super();
      this.checkResult = checkResult;
    }

    @Override
    public void accept(CalculationContext calculationContext, Calculator<? extends Number> calclator,
        FormulaInfo formulaInfo, Number result) {
      
      formulaInfo.getValue("checkKind").ifPresent(checkKindName->{
        checkResult.suspiciousByKind.put(checkKindName, result.floatValue());
      });
      formulaInfo.getValue("var").ifPresent(varName->{
        calculationContext.set(varName, result.floatValue());
      });
      
    }

    @Override
    public void accept(CalculationContext calculationContext, Calculator<String> calclator, 
        FormulaInfo formulaInfo,String result) {
      
      formulaInfo.getValue("var").ifPresent(varName->{
        calculationContext.set(varName, result);
      });
      
    }

    @Override
    public void accept(CalculationContext calculationContext, Calculator<Boolean> calclator, 
        FormulaInfo formulaInfo,boolean result) {
      formulaInfo.getValue("var").ifPresent(varName->{
        calculationContext.set(varName, result);
      });
      
    }

    @Override
    public void accept(CalculationContext calculationContext, Calculator<Object> calclator, 
        FormulaInfo formulaInfo,Object result) {
      formulaInfo.getValue("var").ifPresent(varName->{
        calculationContext.setObject(varName, result);
      });
    }
    
  }

  @Test
  public void test() {
    
    FormulaInfoAdditionalFields formulaInfoAdditionalFields = 
        new FormulaInfoAdditionalFields("siteId","checkKind");
    
    Path rootPath = Paths.get(".", "src","test","resources","formulaInfo-test");
    
    
    FileBaseTinyExpressionInstancesCache fileBaseTinyExpressionInstancesCache = 
        new FileBaseTinyExpressionInstancesCache(rootPath,formulaInfoAdditionalFields);
    
    TinyExpressionsExecutor tinyExpressionsExecutor = new TinyExpressionsExecutor();
    
    CheckResult checkResult = new CheckResult();
    ResultConsumerWithCheckResult resultConsumerWithCheckResult = new ResultConsumerWithCheckResult(checkResult);
    
    ;
    
    
    CalculationContext calculationContext = CalculationContext.newConcurrentContext();
    
    tinyExpressionsExecutor.execute(
        TenantID.create(69), 
        calculationContext,
        resultConsumerWithCheckResult,
        fileBaseTinyExpressionInstancesCache,
        コンパレータの実装。dependsOn,PRE,POSTを考慮。, 
        PRE,POSTなどでフィルターして複数の実行をおこなったり, 
        Thread.currentThread().getContextClassLoader());
    
  }
  

}
