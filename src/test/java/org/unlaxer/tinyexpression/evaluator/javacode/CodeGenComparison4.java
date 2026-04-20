package org.unlaxer.tinyexpression.evaluator.javacode;

import java.lang.reflect.Method;
import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;

public class CodeGenComparison4 {

  @Test
  public void compareTreeStructures() throws Exception {
    String formula = "3*4+2";
    
    System.out.println("\n==== Hand-Written Backend: Token Tree ====\n");
    System.out.println("Formula: " + formula);
    
    // Parse using legacy FormulaParser
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    ParseContext parseContext = new ParseContext(StringSource.createRootSource(formula));
    Parsed parsed = tinyExpressionParser.parse(parseContext);
    Token rootToken = parsed.getRootToken(true);
    rootToken = OperatorOperandTreeCreator.SINGLETON.apply(rootToken);
    
    SpecifiedExpressionTypes types = new SpecifiedExpressionTypes(
        ExpressionTypes._float, 
        ExpressionTypes._float
    );
    TinyExpressionTokens tinyTokens = new TinyExpressionTokens(rootToken, types);
    
    // Print the expression token tree
    Token expressionToken = tinyTokens.expressionToken;
    System.out.println("\nExpression Token Tree:");
    printTokenTree(expressionToken, 0);
    
    System.out.println("\n\n==== P4-Typed Backend: AST Structure ====\n");
    System.out.println("Formula: " + formula);
    
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Class<?> mapperClass = Class.forName(
        "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, cl);
    
    Method parsePreferred = mapperClass.getMethod("parse", String.class, String.class);
    Object ast = parsePreferred.invoke(null, formula, "BinaryExpr");
    
    System.out.println("\nP4 BinaryExpr AST Structure:");
    printP4ASTStructure(ast, 0);
  }
  
  private void printTokenTree(Token token, int depth) {
    String indent = "  ".repeat(depth);
    String parserName = token.parser != null ? token.parser.getClass().getSimpleName() : "null";
    String tokenStr = token.tokenString.orElse("");
    System.out.println(indent + parserName + (tokenStr.isEmpty() ? "" : " [" + tokenStr + "]"));
    
    for (Token child : token.filteredChildren) {
      printTokenTree(child, depth + 1);
    }
  }
  
  private void printP4ASTStructure(Object node, int depth) throws Exception {
    String indent = "  ".repeat(depth);
    String nodeType = node.getClass().getSimpleName();
    System.out.println(indent + nodeType);
    
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
            
            if (value instanceof java.util.List<?> list) {
              if (!list.isEmpty()) {
                System.out.println(indent + "  " + fieldName + ": [");
                for (Object item : list) {
                  if (item instanceof String || item instanceof Number) {
                    System.out.println(indent + "    " + item);
                  } else {
                    printP4ASTStructure(item, depth + 3);
                  }
                }
                System.out.println(indent + "  ]");
              }
            } else if (value instanceof String || value instanceof Number) {
              System.out.println(indent + "  " + fieldName + ": " + value);
            } else {
              System.out.println(indent + "  " + fieldName + ":");
              printP4ASTStructure(value, depth + 2);
            }
          }
        } catch (Exception e) {
          // skip
        }
      }
    }
  }
}
