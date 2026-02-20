package org.unlaxer.tinyexpression.roadmap;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class TypeSystemRoadmapTest {

  @Test
  @Ignore("Roadmap-3: short..BigDecimalの統一数値系をParser/AST/Codegenまで通すまでは保留")
  public void testUnifiedNumericTypeRoadmapFromShortToBigDecimal() {
    List<ExpressionType> numericTypes = List.of(
        ExpressionTypes._short,
        ExpressionTypes._byte,
        ExpressionTypes._int,
        ExpressionTypes._long,
        ExpressionTypes._float,
        ExpressionTypes._double,
        ExpressionTypes.bigInteger,
        ExpressionTypes.bigDecimal);

    for (ExpressionType type : numericTypes) {
      assertTrue(type.isNumber());
    }
  }

  @Test
  @Ignore("Roadmap-4: javaTypeを第4型として宣言/推論/コード生成まで一貫対応するまでは保留")
  public void testJavaTypeAsFourthTypeRoadmap() {
    ExpressionType objectType = ExpressionTypes.object;

    assertTrue(objectType.isObject());
    assertTrue(!objectType.isNumber());
    assertTrue(!objectType.isString());
    assertTrue(!objectType.isBoolean());
  }
}
