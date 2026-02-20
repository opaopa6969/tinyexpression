package org.unlaxer.tinyexpression.loader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.instances.FileBaseTinyExpressionInstancesCache;
import org.unlaxer.tinyexpression.instances.TenantID;
import org.unlaxer.tinyexpression.instances.TinyExpressionsExecutorTest.NameAndDependsOnComparator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public class FormulaInfoParserTest {

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
