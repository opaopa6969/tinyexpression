package org.unlaxer.tinyexpression.loader.model;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.util.StringUtils;
import org.unlaxer.util.Try;

public class FormulaInfoListTest {

  @Test
  public void test() throws IOException {

    // MultiTenancyで使用されるIDと結果の出力の項目名を指定する
    FormulaInfoAdditionalFields formulaInfoAdditionalFields =
        new FormulaInfoAdditionalFields("siteId",
            //formulaInfoからnameを取得するfunction。checkKindがあればそれをnameになぇればcalculatorNameを使用する
            formulaInfo->{
              String checkKind = formulaInfo.extraValueByKey.get("checkKind");
              return checkKind != null ? checkKind : formulaInfo.calculatorName;
            }
        );

    Path filePath = Paths.get(".", "src","test","resources","formulaInfo-test","69","formulaInfo.txt");

    String text;
    try(InputStream newInputStream = Files.newInputStream(filePath)){
      text = StringUtils.from(newInputStream, StandardCharsets.UTF_8);

    }
    try(InputStream newInputStream = Files.newInputStream(filePath)){
      FormulaInfoList formualInfoList =
          FormulaInfoList.parse(newInputStream, formulaInfoAdditionalFields, Thread.currentThread().getContextClassLoader()).get();

      System.out.println(formualInfoList.input());
      assertEquals(text, formualInfoList.input());
    }


  }

}
