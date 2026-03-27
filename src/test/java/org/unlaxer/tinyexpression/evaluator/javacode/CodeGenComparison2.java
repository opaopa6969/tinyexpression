package org.unlaxer.tinyexpression.evaluator.javacode;

import java.lang.reflect.Method;
import org.junit.Test;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class CodeGenComparison2 {

  @Test
  public void compareP4EmitterDirectly() throws Exception {
    String formula = "3*4+2";
    SpecifiedExpressionTypes types = new SpecifiedExpressionTypes(
        ExpressionTypes._float, 
        ExpressionTypes._float
    );
    
    System.out.println("\n==== Testing P4 AST Generation ====");
    System.out.println("Formula: " + formula);
    
    // Try to parse via P4 mapper
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      Class<?> mapperClass = Class.forName(
          "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, cl);
      
      // Try parse with preferred AST type
      Method parsePreferred = mapperClass.getMethod("parse", String.class, String.class);
      Object ast = parsePreferred.invoke(null, formula, "BinaryExpr");
      System.out.println("P4 AST parsed: " + (ast != null));
      if (ast != null) {
        System.out.println("AST type: " + ast.getClass().getSimpleName());
        System.out.println("AST class: " + ast.getClass().getName());
        
        // Now use P4TypedJavaCodeEmitter directly
        P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(types);
        String javaExpr = emitter.eval((org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST) ast);
        System.out.println("\n==== P4TypedJavaCodeEmitter Expression ====");
        System.out.println(javaExpr);
        
        String fullJava = emitter.buildJavaClass("TestP4Direct", javaExpr);
        System.out.println("\n==== P4TypedJavaCodeEmitter Full Class ====");
        System.out.println(fullJava);
      }
    } catch (Exception e) {
      System.out.println("P4 parsing failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
