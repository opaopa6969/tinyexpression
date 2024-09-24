package org.unlaxer.compiler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;
import org.unlaxer.util.Try;

public class MemoryClassLoaderTest {
  
  
  static String code1 ="""
//package sample.v1;//version1. if logic updates then update package.
package v1;
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
      System.out.println(new v1.CheckDigits().check("0123"));
  }
  public static boolean check(String arg){
      return new v1.CheckDigits().check(arg);
  }
}
""";  
  


  @Test
  public void test() throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    
    JavaFileManagerContext javaFileManagerContext = new JavaFileManagerContext();
    
    MemoryClassLoader memoryClassLoader = new MemoryClassLoader(contextClassLoader);
    
    
    try (CompileContext compileContext = new CompileContext(memoryClassLoader,javaFileManagerContext)) {
      ClassName className1 = new ClassName("v1.CheckDigits");
      
      Try<ClassAndByteCode> compile1 = compileContext.compile(className1,code1);
      
      ClassAndByteCode classAndByteCode = compile1.get();
      
      ClassName className2 = new ClassName("v1.ClassUser");
      
      Try<ClassAndByteCode> compile2 = compileContext.compile(className2,code2);
      
      ClassAndByteCode classAndByteCode2 = compile2.get();
      
      Method method = classAndByteCode2.clazz.getMethod("main", String[].class);
      String[] params = null; 
      method.invoke(null, (Object)params);
      
      Method checkMethod = classAndByteCode2.clazz.getMethod("check", String.class);
      boolean isNumber = (boolean) checkMethod.invoke(null, "0123");
      assertTrue(isNumber);
      
      boolean isNumber2 = (boolean) checkMethod.invoke(null, "abcd");
      assertFalse(isNumber2);
      
    } catch (IOException e) {
      e.printStackTrace();
      throw new UncheckedIOException(e);
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
