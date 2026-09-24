package org.unlaxer.tinyexpression.p4.ubnfc;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;

/**
 * Builds the few tinyexpression AST records whose declared component types depend on which
 * unlaxer-dsl generator produced them.
 *
 * <p>tinyexpression generates its AST at build time with unlaxer-dsl, and is built both against
 * the published 3.0.15 jar and against the pinned development generator (CI
 * {@code mapper-compatibility (published|source)}). They differ in exactly these components:
 *
 * <table>
 *   <caption>generator-dependent components</caption>
 *   <tr><th>component</th><th>published 3.0.15</th><th>development generator</th></tr>
 *   <tr><td>{@code External*InvocationExpr.className}</td><td>{@code QualifiedNameExpr} (null when absent)</td>
 *       <td>{@code Optional<QualifiedNameExpr>}</td></tr>
 *   <tr><td>{@code SliceExpr.start/end/step}</td><td>{@code String} (stripped source text, "" when absent)</td>
 *       <td>{@code Optional<Object>} (the index expression)</td></tr>
 * </table>
 *
 * <p>{@link UbnfcAstConverter} passes {@link OptionalNode} / {@link SliceIndex} for those and this
 * class adapts them to the canonical constructor's parameter types found at run time.
 */
final class VariantShapes {

  /** A component that is {@code Optional<X>} or a nullable {@code X}. */
  record OptionalNode(Optional<Object> value) {}

  /** A slice index that is {@code Optional<Object>} (node) or {@code String} (source text). */
  record SliceIndex(Optional<Object> node, Optional<String> text) {}

  private static final ClassValue<Constructor<?>> CANONICAL = new ClassValue<>() {
    @Override
    protected Constructor<?> computeValue(Class<?> type) {
      Class<?>[] parameters = Arrays.stream(type.getRecordComponents())
          .map(RecordComponent::getType)
          .toArray(Class<?>[]::new);
      try {
        return type.getDeclaredConstructor(parameters);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("no canonical constructor: " + type.getName(), e);
      }
    }
  };

  private VariantShapes() {}

  static Object construct(Class<?> recordType, Object... args) {
    Constructor<?> constructor = CANONICAL.get(recordType);
    Class<?>[] parameters = constructor.getParameterTypes();
    if (parameters.length != args.length) {
      throw new IllegalStateException(recordType.getName() + " has " + parameters.length
          + " components, converter passed " + args.length);
    }
    Object[] actual = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      actual[i] = adapt(recordType, parameters[i], args[i]);
    }
    try {
      return constructor.newInstance(actual);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Object adapt(Class<?> recordType, Class<?> parameter, Object arg) {
    if (arg instanceof OptionalNode optional) {
      return parameter == Optional.class ? optional.value() : optional.value().orElse(null);
    }
    if (arg instanceof SliceIndex index) {
      if (parameter == Optional.class) {
        return index.node();
      }
      if (parameter == String.class) {
        return index.text().orElse("");
      }
      throw new IllegalStateException("unexpected slice index type " + parameter.getName()
          + " in " + recordType.getName());
    }
    return arg;
  }
}
