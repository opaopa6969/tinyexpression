package org.unlaxer.tinyexpression.loader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeBlockPolicy;
import org.unlaxer.tinyexpression.instances.FileBaseTinyExpressionInstancesCache;
import org.unlaxer.tinyexpression.instances.TenantID;
import org.unlaxer.tinyexpression.instances.TinyExpressionsExecutorTest.NameAndDependsOnComparator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public class FormulaInfoParserTest {

  // The test fixtures contain Java code-block formulas (CheckDigits). The policy is a
  // process-wide static flag (default off, secure-by-default), so each test that needs
  // code blocks must opt in itself — relying on another test having enabled it makes the
  // suite order-/parallelism-dependent (tinyexpression#27).
  @Before public void enableJavaCodeBlocks() { JavaCodeBlockPolicy.setEnabled(true); }
  @After public void resetJavaCodeBlocks() { JavaCodeBlockPolicy.reset(); }

  @Test
  public void test() {
    // MultiTenancyで使用されるIDと結果の出力の項目名を指定する
    FormulaInfoAdditionalFields formulaInfoAdditionalFields =
        new FormulaInfoAdditionalFields("siteId",
            //formulaInfoからnameを取得するfunction。checkKindがあればそれをnameになぇればcalculatorNameを使用する
            formulaInfo->{
              String checkKind = formulaInfo.extraValueByKey.get("checkKind");
              return checkKind != null ? checkKind : formulaInfo.calculatorName;
            }
        );

    // TestのformulaInfo.txtが保存されているroot dirを指定する。ここからtenantId毎にsub directoryが掘られてformulaInfo.txtが保存される
    Path rootPath = Paths.get(".", "src","test","resources","formulaInfo-test");

    //formula-info.txtからCalculatorのlistをcacheするクラス
    //実際のapplicationではRDBから読み込む実装になったりする
    FileBaseTinyExpressionInstancesCache fileBaseTinyExpressionInstancesCache =
        new FileBaseTinyExpressionInstancesCache(rootPath,formulaInfoAdditionalFields);

    List<Calculator> list = fileBaseTinyExpressionInstancesCache.cache(
        TenantID.create(69),
        NameAndDependsOnComparator.SINGLETON,
        Thread.currentThread().getContextClassLoader()
    );

    for (Calculator calculator : list) {
      FormulaInfo formulaInfo = calculator.formulaInfo();
      System.out.println( formulaInfo.toString());
    }


  }

}
