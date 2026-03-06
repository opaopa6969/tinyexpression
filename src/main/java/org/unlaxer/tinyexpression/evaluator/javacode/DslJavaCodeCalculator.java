package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;
import java.util.Optional;

import org.unlaxer.tinyexpression.Source;

/**
 * Dedicated DSL backend seam.
 * <p>
 * Current runtime behavior intentionally bridges to the legacy JavaCode runtime.
 */
public class DslJavaCodeCalculator extends JavaCodeCalculatorV3 {

  private static final ThreadLocal<ClassLoader> CONSTRUCTION_CLASS_LOADER = new ThreadLocal<>();

  private volatile boolean nativeEmitterUsed;
  private volatile String dslEmitterMode;

  public DslJavaCodeCalculator(Source source, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    super(source, className, specifiedExpressionTypes, captureClassLoader(classLoader));
    CONSTRUCTION_CLASS_LOADER.remove();
  }

  public DslJavaCodeCalculator(Source source, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes, byte[] byteCode, String byteCodeHash,
      List<ClassNameAndByteCode> classNameAndByteCodeList, ClassLoader classLoader) {
    super(source, javaCode, className, specifiedExpressionTypes,
        byteCode, byteCodeHash, classNameAndByteCodeList, classLoader);
    this.nativeEmitterUsed = false;
    this.dslEmitterMode = "legacy-bridge";
  }

  private static ClassLoader captureClassLoader(ClassLoader classLoader) {
    CONSTRUCTION_CLASS_LOADER.set(classLoader);
    return classLoader;
  }

  @Override
  public String createJavaClass(String className, TinyExpressionTokens tinyExpressionToken,
      SpecifiedExpressionTypes specifiedExpressionTypes) {
    ClassLoader classLoader = CONSTRUCTION_CLASS_LOADER.get();
    if (classLoader == null) {
      classLoader = Thread.currentThread().getContextClassLoader();
    }
    Optional<DslGeneratedAstJavaEmitter.EmittedJava> emitted = DslGeneratedAstJavaEmitter.tryEmit(
        className, source(), specifiedExpressionTypes, classLoader);
    if (emitted.isPresent()) {
      this.nativeEmitterUsed = true;
      this.dslEmitterMode = emitted.get().mode();
      return emitted.get().javaCode();
    }
    this.nativeEmitterUsed = false;
    this.dslEmitterMode = "legacy-bridge";
    return super.createJavaClass(className, tinyExpressionToken, specifiedExpressionTypes);
  }

  public boolean nativeEmitterUsed() {
    return nativeEmitterUsed;
  }

  public String dslEmitterMode() {
    return dslEmitterMode;
  }
}
