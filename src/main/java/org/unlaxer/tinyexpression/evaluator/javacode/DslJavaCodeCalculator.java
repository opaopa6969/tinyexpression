package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;
import java.util.Optional;

import org.unlaxer.tinyexpression.Source;

/**
 * Java-code backend driven exclusively by the generated P4 AST emitter.
 * Unsupported syntax fails explicitly instead of switching to the handwritten
 * {@link JavaCodeCalculatorV3} generator.
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
    this.dslEmitterMode = "precompiled-bytecode";
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
    this.dslEmitterMode = "unsupported";
    throw new UnsupportedOperationException(
        "Generated DSL Java emitter cannot emit formula: " + source().source());
  }

  public boolean nativeEmitterUsed() {
    return nativeEmitterUsed;
  }

  public String dslEmitterMode() {
    return dslEmitterMode;
  }
}
