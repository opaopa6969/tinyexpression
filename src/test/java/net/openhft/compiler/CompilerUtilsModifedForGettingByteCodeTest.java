package net.openhft.compiler;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

import org.junit.Test;

import net.openhft.compiler.CachedCompilerModifiedForByteCodeGetting.CompileResult;

public class CompilerUtilsModifedForGettingByteCodeTest {

  @Test
  public void test() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
    String a= "public class Test_A implements java.util.function.Supplier<Integer>{public Integer get() {return 10;}}";
    String b= "public class Test_A implements java.util.function.Supplier<Integer>{public Integer get() {return 20;}}";
    String c= "public class Test_A implements java.util.function.Supplier<Integer>{public Integer get() {return 30;}}";

    {
      @SuppressWarnings("unchecked")
      CompileResult<Supplier<Integer>> loadFromJava = 
      (CompileResult<Supplier<Integer>>) CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(
          "Test_A", a, Thread.currentThread().getContextClassLoader());
      
      Supplier<Integer> newInstance = loadFromJava.loadedClass.getDeclaredConstructor().newInstance();
      
      Integer integer = newInstance.get();
      assertEquals(10, integer.intValue());
    }
    {
      ClassLoader classLoader = new ClassLoader(Thread.currentThread().getContextClassLoader()) {
      };
      @SuppressWarnings("unchecked")
      CompileResult<Supplier<Integer>> loadFromJava = 
      (CompileResult<Supplier<Integer>>) CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(
          "Test_A", b, classLoader);
      
      Supplier<Integer> newInstance = loadFromJava.loadedClass.getDeclaredConstructor().newInstance();
      
      Integer integer = newInstance.get();
      assertEquals(20, integer.intValue());
    }
    {
      ClassLoader classLoader = new ClassLoader(Thread.currentThread().getContextClassLoader()) {
      };
      @SuppressWarnings("unchecked")
      CompileResult<Supplier<Integer>> loadFromJava = 
      (CompileResult<Supplier<Integer>>) CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(
          "Test_A", c, classLoader);
      
      Supplier<Integer> newInstance = loadFromJava.loadedClass.getDeclaredConstructor().newInstance();
      
      Integer integer = newInstance.get();
      assertEquals(30, integer.intValue());
    }
    
  }
  

}
