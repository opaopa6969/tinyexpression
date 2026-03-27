package org.unlaxer.tinyexpression.evaluator.javacode;

import org.junit.Test;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class CodeGenComparison {

  @Test
  public void compareCodeGeneration() throws Exception {
    String formula = "3*4+2";
    SpecifiedExpressionTypes types = new SpecifiedExpressionTypes(
        ExpressionTypes._float, 
        ExpressionTypes._float
    );
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    
    // Hand-written backend (JavaCodeCalculatorV3)
    System.out.println("\n\n==== Hand-Written Backend (JAVA_CODE) ====");
    Calculator javaCode = CalculatorCreatorRegistry.forBackend(ExecutionBackend.JAVA_CODE)
        .create(new Source(formula), "TestHandWritten", types, cl);
    String handWritten = javaCode.javaCode();
    System.out.println(handWritten);
    
    // P4-typed backend (via DslJavaCodeCalculator)
    System.out.println("\n\n==== P4-Typed Backend (DSL_JAVA_CODE) ====");
    Calculator p4Code = CalculatorCreatorRegistry.forBackend(ExecutionBackend.DSL_JAVA_CODE)
        .create(new Source(formula), "TestP4Typed", types, cl);
    String p4Typed = p4Code.javaCode();
    System.out.println(p4Typed);
    
    if (p4Code instanceof DslJavaCodeCalculator dsl) {
      System.out.println("\nDSL Emitter Mode: " + dsl.dslEmitterMode());
      System.out.println("Native Emitter Used: " + dsl.nativeEmitterUsed());
    }
  }
}
