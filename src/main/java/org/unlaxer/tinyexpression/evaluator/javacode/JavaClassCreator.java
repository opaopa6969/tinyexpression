package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;

public interface JavaClassCreator{
  default String createJavaClass(String className, TinyExpressionTokens tinyExpressionToken) {

    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();

    String CalculationContextName = CalculationContext.class.getName();
    builder
      .setKind(Kind.Main)
      .line("import org.unlaxer.Token;")
      .line("import "+CalculationContextName+";")
      .line("import org.unlaxer.tinyexpression.TokenBaseOperator;")
      .n()
      .append("public class ")
      .append(className)
      .append(" implements TokenBaseOperator<"+CalculationContextName+", Float>{")
      .n()
      .n()
      .setKind(Kind.Function)
      .incTab()
      .line("@Override")
      .line("public Float evaluate("+CalculationContextName+" calculateContext , Token token) {")
      .setKind(Kind.Calculation)
      .incTab()
      .line("float answer = (float) ")
      .n();

    NumberExpressionBuilder.SINGLETON.build(builder, tinyExpressionToken.expressionToken, tinyExpressionToken);

    builder
      .setKind(Kind.Calculation)
      .n()
      .line(";")
      .line("return answer;")
      .decTab()
      .line("}")
      .decTab()
      .setKind(Kind.Main);


    String code = builder.toString();
    return code;
  }
  
}