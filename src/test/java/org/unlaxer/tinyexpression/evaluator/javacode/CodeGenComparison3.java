package org.unlaxer.tinyexpression.evaluator.javacode;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class CodeGenComparison3 {

  @Test
  public void inspectP4AST() throws Exception {
    String formula = "3*4+2";
    SpecifiedExpressionTypes types = new SpecifiedExpressionTypes(
        ExpressionTypes._float, 
        ExpressionTypes._float
    );
    
    System.out.println("\n==== P4 AST Structure for: " + formula + " ====\n");
    
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Class<?> mapperClass = Class.forName(
        "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, cl);
    
    Method parsePreferred = mapperClass.getMethod("parse", String.class, String.class);
    Object ast = parsePreferred.invoke(null, formula, "BinaryExpr");
    
    if (ast != null) {
      printASTStructure(ast, 0);
    }
  }
  
  private void printASTStructure(Object node, int depth) throws Exception {
    String indent = "  ".repeat(depth);
    String nodeType = node.getClass().getSimpleName();
    System.out.println(indent + nodeType + " {");
    
    // Get all methods that return values (fields)
    for (Method method : node.getClass().getDeclaredMethods()) {
      if (method.getParameterCount() == 0 && 
          !method.getName().equals("clone") &&
          !method.getName().startsWith("hashCode") &&
          !method.getName().startsWith("toString") &&
          !method.getName().startsWith("equals") &&
          !method.getName().startsWith("getClass")) {
        try {
          Object value = method.invoke(node);
          if (value != null) {
            String fieldName = method.getName();
            
            if (value instanceof List<?> list) {
              System.out.println(indent + "  " + fieldName + ": [");
              for (Object item : list) {
                if (item instanceof String || item instanceof Number) {
                  System.out.println(indent + "    " + item);
                } else {
                  printASTStructure(item, depth + 3);
                }
              }
              System.out.println(indent + "  ]");
            } else if (value instanceof String || value instanceof Number) {
              System.out.println(indent + "  " + fieldName + ": " + value);
            } else {
              System.out.println(indent + "  " + fieldName + ":");
              printASTStructure(value, depth + 2);
            }
          }
        } catch (Exception e) {
          // skip
        }
      }
    }
    System.out.println(indent + "}");
  }
}
