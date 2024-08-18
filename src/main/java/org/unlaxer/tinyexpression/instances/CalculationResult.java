package org.unlaxer.tinyexpression.instances;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.unlaxer.tinyexpression.Calculator;

public class CalculationResult{
  
  public final Calculator<?> calculator;
  public final Object result;
  private final Throwable throwable;
  public CalculationResult(Calculator<?> calculator, Object result, @Nullable Throwable throwable) {
    super();
    this.calculator = calculator;
    this.result = result;
    this.throwable = throwable;
  }
  public Optional<Throwable> error(){
    return Optional.ofNullable(throwable);
  }
}