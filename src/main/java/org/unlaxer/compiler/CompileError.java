package org.unlaxer.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CompileError extends RuntimeException {
  
  public enum ComileErrorContextKey{
    calculatorList,
  }
  
  
  
  public final Map<String,Object> objectByKey = new HashMap<>();

  public CompileError() {
    super();
  }
  
  public CompileError(Consumer<Map<String,Object>> mapConsumer) {
    super();
    mapConsumer.accept(objectByKey);
  }
  
  public CompileError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }


  public CompileError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace , Consumer<Map<String,Object>> mapConsumer) {
    super(message, cause, enableSuppression, writableStackTrace);
    mapConsumer.accept(objectByKey);
  }

  public CompileError(String message, Throwable cause) {
    super(message, cause);
  }

  public CompileError(String message) {
    super(message);
  }

  public CompileError(Throwable cause) {
    super(cause);
  }
  
  public CompileError(String message, Throwable cause , Consumer<Map<String,Object>> mapConsumer) {
    super(message, cause);
    mapConsumer.accept(objectByKey);
  }

  public CompileError(String message,Consumer<Map<String,Object>> mapConsumer) {
    super(message);
    mapConsumer.accept(objectByKey);
  }

  public CompileError(Throwable cause,Consumer<Map<String,Object>> mapConsumer) {
    super(cause);
    mapConsumer.accept(objectByKey);
  }
}