package org.unlaxer.tinyexpression.instances;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.BeforeClass;
import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.instances.TinyExpressionInstancesCacheTest.FileBaseTinyExpressionInstancesCache;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

/**
 * TinyExpressionsExecutorはCalculatorのCacheを指定されたComparator<Calculator>とPredicate<Calculator>でsort/filteringを
 * 行って実行するクラス。ResultConsumerを指定することにより計算結果をcheckResultや変数に書き込んだりする
 * 以下のJVM optionが必要
 * --add-opens java.base/java.lang=ALL-UNNAMED
 */
public class TinyExpressionsExecutorTest {
  
  @BeforeClass
  public static void setup() {
    System.setProperty("--add-opens", "java.base/java.lang=ALL-UNNAMED");
  }
  
  /*
   * Test用の簡易的なCheckResult class
   */
  public static class CheckResult{
    public Map<String,Float> suspiciousByKind = new HashMap<>();
    public float theScore;
    public boolean theFlag;
    public String theName;
    public Timestamp theTimestamp;
  }
  
  /**
   * CheckResultを内部にもち計算結果を処理してcheckResultや変数に書き込む
   */
  public static class ResultConsumerWithCheckResult implements ResultConsumer{
    
    public final CheckResult checkResult;

    public ResultConsumerWithCheckResult(CheckResult checkResult) {
      super();
      this.checkResult = checkResult;
    }

    /**
     * 数値の計算結果を処理する
     */
    @Override
    public void accept(CalculationContext calculationContext, Calculator calclator,
        FormulaInfo formulaInfo, Number result) {
    
      //formulaInfoにcheckKindが指定されていればsuspiciousByKindに書き込む
      formulaInfo.getValue("checkKind").ifPresent(checkKindName->{
        checkResult.suspiciousByKind.put(checkKindName, result.floatValue());
      });
      
      //formulaInfoにvarが指定されていれば変数に書き込む
      formulaInfo.getValue("var").ifPresent(varName->{
        calculationContext.set(varName, result);
      });
      
      //formulaInfoにfieldが指定されていればcheckResultのfieldに書き込む
      formulaInfo.getValue("field").ifPresent(fieldName->{
        try {
          CheckResult.class.getDeclaredField(fieldName).set(checkResult, result);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
          throw new RuntimeException(e);
        }
      });
      
    }

    /**
     * 文字列の計算結果を処理する
     */
    @Override
    public void accept(CalculationContext calculationContext, Calculator calclator, 
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

    /**
     * booleanの計算結果を処理する
     */
    @Override
    public void accept(CalculationContext calculationContext, Calculator calclator, 
        FormulaInfo formulaInfo,Boolean result) {
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

    /**
     * Objectの計算結果を処理する
     */
    @Override
    public void accept(CalculationContext calculationContext, Calculator calclator, 
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
  
  /**
   * Calculatorの実行順序を決定するComparator
   * PREやPOSTが名前についていればそれに従い優先順を決める
   * DependsOnが指定されていればDependsOnのnestLevelで補正を行う
   */
  public static class NameAndDependsOnComparator implements Comparator<Calculator>{
    
    public static final NameAndDependsOnComparator SINGLETON= new NameAndDependsOnComparator();
    
    public static final int PRE   =1000000;
    public static final int NORMAL=2000000;
    public static final int POST  =3000000;

    @Override
    public int compare(Calculator o1, Calculator o2) {
      return score(o1)-score(o2);
    }
    
    public int score(Calculator calculator) {
      
      int dependsOnByNestLevel = calculator.dependsOnByNestLevel();
      if(dependsOnByNestLevel == 0) {
        return scoreByName(calculator);
      }
      return scoreByName(calculator.rootDependsOnBy()) - dependsOnByNestLevel;
    }
    
    public int scoreByName(Calculator calculator) {
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
  public void testFromSource() {
    testWith(69);
  }
  
  @Test
  public void testFromByteCoe() {
    testWith(86);
  }
  
  
  public void testWith(int tenantId) {
    // MultiTenancyで使用されるIDと結果の出力の項目名を指定する
    FormulaInfoAdditionalFields formulaInfoAdditionalFields = 
        new FormulaInfoAdditionalFields("siteId",
            //formulaInfoからnameを取得するfunction。calculatorNameがあればそれをnameになぇればcheckKindを使用する
            formulaInfo->{
              return formulaInfo.calculatorName != null ? formulaInfo.calculatorName : formulaInfo.extraValueByKey.get("checkKind");
            }
        );
    
    // TestのformulaInfo.txtが保存されているroot dirを指定する。ここからtenantId毎にsub directoryが掘られてformulaInfo.txtが保存される
    Path rootPath = Paths.get(".", "src","test","resources","formulaInfo-test");
    
    //formula-info.txtからCalculatorのlistをcacheするクラス
    //実際のapplicationではRDBから読み込む実装になったりする
    FileBaseTinyExpressionInstancesCache fileBaseTinyExpressionInstancesCache = 
        new FileBaseTinyExpressionInstancesCache(rootPath,formulaInfoAdditionalFields);
    
    //実行するexecutor
    TinyExpressionsExecutor tinyExpressionsExecutor = new TinyExpressionsExecutor();
    
    //最終敵に値を格納するCheckResult
    CheckResult checkResult = new CheckResult();
    
    //計算結果を格納するためのResultConsumer
    ResultConsumerWithCheckResult resultConsumerWithCheckResult = new ResultConsumerWithCheckResult(checkResult);
    
    ;
    CalculationContext calculationContext = CalculationContext.newConcurrentContext();
    
    calculationContext.set("input", "1234");
    calculationContext.set("inputName", "ABCD");
    
    //実行はしてCalculationResultのListを得る
    List<CalculationResult> execute = tinyExpressionsExecutor.execute(
        TenantID.create(tenantId), 
        calculationContext,
        resultConsumerWithCheckResult,
        fileBaseTinyExpressionInstancesCache,
//        コンパレータの実装。dependsOn,PRE,POSTを考慮。,
        NameAndDependsOnComparator.SINGLETON,
//        PRE,POSTなどでフィルターして複数の実行をおこなったり,
        x->true,
        Thread.currentThread().getContextClassLoader());
    
    CalculationResult calculationResult = execute.get(0);
    System.out.println(calculationContext.getValue("sqrt"));
    System.out.println(calculationResult.result);
    System.out.println(calculationContext.getValue("matchNumber"));
    System.out.println(calculationContext.getValue("callJavaCodeBlock"));
    
    
    for (CalculationResult calculationResult_ : execute) {
      System.out.println(calculationResult_.toString());
      //エラーがあればthrow
      calculationResult_.throwIfMatch();
    }
    
    assertEquals("1.4142135", String.valueOf(calculationResult.result));
    assertEquals("6969", String.valueOf((int)checkResult.theScore));
    assertEquals("1.0", String.valueOf(calculationContext.getValue("matchNumber").orElseThrow()));
    assertEquals("1.0", String.valueOf(calculationContext.getValue("matchAlphabet").orElseThrow()));
    assertEquals("opaopa", calculationContext.getString("name").orElseThrow());
    assertEquals("opaopa", checkResult.theName);
    assertEquals(String.valueOf(423372036854775807L*2), 
        String.valueOf(calculationContext.getNumber("ロング計算結果").orElseThrow()));
    assertEquals(String.valueOf(423372036854775807D*2), 
        String.valueOf(calculationContext.getNumber("ダブル計算結果").orElseThrow()));
    
    assertEquals(true,calculationContext.getBoolean("真偽値計算結果").orElseThrow());
    assertEquals(true,calculationContext.getBoolean("numberType指定真偽値計算結果").orElseThrow());
    
  }
  
  public static void main(String[] args) {
    boolean matches = "a10c".matches(".\\d+.");
    System.out.println(matches);
  }

}
