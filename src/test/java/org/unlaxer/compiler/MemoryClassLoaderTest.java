package org.unlaxer.compiler;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;

public class MemoryClassLoaderTest {
  
  
  static String code1 ="""
//package sample.v1;//version1. if logic updates then update package.
import org.unlaxer.tinyexpression.CalculationContext;

public class CheckDigits{
  public boolean check(CalculationContext calculationContext,String target){
    return target.matches("\\d+");
  }
}
""";

  @Test
  public void test() {
    
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    
    MemoryClassLoader memoryClassLoader = new MemoryClassLoader(contextClassLoader);
    
    
    CompileContext compileContext = new CompileContext(memoryClassLoader);
    
    
//    compileContext.compile(className,code);
  }
  
  public static void main() {
    CalculationContext context = CalculationContext.newConcurrentContext();
    new CheckDigits().check(context,"test");
  }
  
  public static class CheckDigits{
    
    public boolean check(CalculationContext calculationContext,String target){
      return target.matches("\\d+");
    }
  }

}
