package org.unlaxer.tinyexpression.instances;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.instances.TinyExpressionInstancesCacheTest.FileBaseTinyExpressionInstancesCache;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public class TinyExpressionsExecutorTest {
  
  public static class CheckResult{
    public Map<String,Float> suspiciousByKind = new HashMap<>();
    public float theScore;
    public boolean theFlag;
    public String theName;
    public Timestamp theTimestamp;
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
      formulaInfo.getValue("field").ifPresent(fieldName->{
        try {
          CheckResult.class.getDeclaredField(fieldName).set(checkResult, result.floatValue());
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
          throw new RuntimeException(e);
        }
      });
      
    }

    @Override
    public void accept(CalculationContext calculationContext, Calculator<String> calclator, 
        FormulaInfo formulaInfo,String result) {
      
      formulaInfo.getValue("var").ifPresent(varName->{
        calculationContext.set(varName, result);
      });
      formulaInfo.getValue("field").ifPresent(fieldName->{
        try {
          CheckResult.class.getDeclaredField(fieldName).set(checkResult, result);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
          throw new RuntimeException(e);
        }
      });      
    }

    @Override
    public void accept(CalculationContext calculationContext, Calculator<Boolean> calclator, 
        FormulaInfo formulaInfo,boolean result) {
      formulaInfo.getValue("var").ifPresent(varName->{
        calculationContext.set(varName, result);
      });
      formulaInfo.getValue("field").ifPresent(fieldName->{
        try {
          CheckResult.class.getDeclaredField(fieldName).set(checkResult, result);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
          throw new RuntimeException(e);
        }
      });
    }

    @Override
    public void accept(CalculationContext calculationContext, Calculator<Object> calclator, 
        FormulaInfo formulaInfo,Object result) {
      formulaInfo.getValue("var").ifPresent(varName->{
        calculationContext.setObject(varName, result);
      });
      formulaInfo.getValue("field").ifPresent(fieldName->{
        try {
          CheckResult.class.getDeclaredField(fieldName).set(checkResult, result);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }
  
  public static class NameAndDependsOnComparator implements Comparator<Calculator<?>>{
    
    public static final NameAndDependsOnComparator SINGLETON= new NameAndDependsOnComparator();
    
    public static final int PRE   =1000000;
    public static final int NORMAL=2000000;
    public static final int POST  =3000000;

    @Override
    public int compare(Calculator<?> o1, Calculator<?> o2) {
      return score(o1)-score(o2);
    }
    
    public int score(Calculator<?> calculator) {
      
      int dependsOnByNestLevel = calculator.dependsOnByNestLevel();
      if(dependsOnByNestLevel == 0) {
        return scoreByName(calculator);
      }
      return scoreByName(calculator.rootDependsOnBy()) - dependsOnByNestLevel;
    }
    
    public int scoreByName(Calculator<?> calculator) {
      Optional<FormulaInfo> formulaInfoOptional = calculator.formulaInfo();
      if(formulaInfoOptional.isEmpty()) {
        return NORMAL;
      }
      FormulaInfo formulaInfo = formulaInfoOptional.get();
      
      return formulaInfo.getValue("checkKind").map(NameAndDependsOnComparator::scoreByName)
        .orElseGet(()->
            formulaInfo.getValue("field").map(NameAndDependsOnComparator::scoreByName)
              .orElse(NORMAL)
        );
    }
    
    public static int scoreByName(String name) {
      if(name.startsWith("PRE")) {
        return PRE;
      }
      if(name.startsWith("POST")) {
        return POST;
      }
      return NORMAL;
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
//        コンパレータの実装。dependsOn,PRE,POSTを考慮。,
        NameAndDependsOnComparator.SINGLETON,
//        PRE,POSTなどでフィルターして複数の実行をおこなったり,
        x->true,
        Thread.currentThread().getContextClassLoader());
    
  }
  

}
