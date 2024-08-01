package org.unlaxer.compiler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.junit.Test;
import org.unlaxer.util.Try;

public class MemoryClassLoaderTest {
  
  
  static String code1 ="""
//package sample.v1;//version1. if logic updates then update package.
public class CheckDigits{
  public boolean check(String target){
    return target.matches("\\\\d+");
  }
}
""";
  
  static String code2 ="""
package v1;

public class ClassUser{
  public static void main(String[] args){
      System.out.println(new CheckDigits().check("0123"));
  }
}
""";  
  


  @Test
  public void test() throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    
    MemoryClassLoader memoryClassLoader = new MemoryClassLoader(contextClassLoader);
    
    
    try (CompileContext compileContext = new CompileContext(memoryClassLoader)) {
      ClassName className1 = new ClassName("CheckDigits");
      
      Try<ClassAndByteCode> compile1 = compileContext.compile(className1,code1);
      
      ClassAndByteCode classAndByteCode = compile1.get();
      
      ClassName className2 = new ClassName("v1.ClassUser");
      
      Try<ClassAndByteCode> compile2 = compileContext.compile(className2,code2);
      
      ClassAndByteCode classAndByteCode2 = compile2.get();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
  }
  
  
  public static class ClassUser1{
    public static void main(String[] args){
        System.out.println(new CheckDigits1().check("0123"));
    }
  }
  
  public static class CheckDigits1{
    public boolean check(String target){
      return target.matches("\\d+");
    }
  }


}
