package org.unlaxer.util;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.unlaxer.util.function.Unchecked.ThrowingSupplier;

public class Try<R> extends Either<Throwable, R> {

  public final Optional<Throwable> throwable;

  public final Optional<Consumer<Throwable>> throwableConsumer;

  public Try(Throwable left, R right) {
    super(left, right);
    throwable = super.left;
    throwableConsumer = Optional.empty();
  }

  public Try(Throwable left, R right, Consumer<Throwable> throwableConsumer) {
    super(left, right);
    throwable = super.left;
    this.throwableConsumer = Optional.of(throwableConsumer);
  }

//  public Try(Throwable left, Supplier<R> right) {
//    super(left, right);
//    throwable = super.left;
//    throwableConsumer = Optional.empty();
//  }


  /**
   * right value is {@link Supplier#get()} 1 times.
   * 
   * @param supplier
   * @return Try
   */
  public static <R> Try<R> resultOf(ThrowingSupplier<R> supplier) {
    try {
      R r = supplier.get();
      return ofNullable(r);
    } catch (Throwable e) {
      return immediatesOf(e);
    }
  }

  /**
   * right value is {@link Supplier#get()} 1 times.
   * 
   * @param supplier
   * @return Try
   */
  public static <R> Try<R> resultOf(ThrowingSupplier<R> supplier,
      Consumer<Throwable> throwableConsumer) {
    try {
      R r = supplier.get();
      return ofNullable(r);
    } catch (Throwable e) {
      throwableConsumer.accept(e);
      return immediatesOf(e);
    }
  }

//  /**
//   * right value is supplier. right value evaluates by call {@link Try#right()}
//   * 
//   * @param supplier
//   * @return Try<R>
//   */
//  public static <R> Try<R> supplierOf(Supplier<R> supplier) {
//    try {
//      return immediatesOf(supplier);
//    } catch (Throwable e) {
//      return immediatesOf(e);
//    }
//  }


  public static <R> Try<R> immediatesOf(R right) {
    if (right == null) {
      throw new IllegalArgumentException("must be not null");
    }
    return new Try<R>(null, right);
  }

  public static <R> Try<R> ofNullable(R right) {
    return new Try<R>(null, right);
  }

  public static <R> Try<R> ofNullable(R right, Consumer<Throwable> throwableConsuer) {
    return new Try<R>(null, right, throwableConsuer);
  }

//  public static <R> Try<R> immediatesOf(Supplier<R> right) {
//    if (right == null) {
//      throw new IllegalArgumentException("must be not null");
//    }
//    return new Try<R>(null, right);
//  }
//
//  public static <R> Try<R> ofNullable(Supplier<R> right) {
//    return new Try<R>(null, right);
//  }


  public static <R> Try<R> immediatesOf(Throwable left) {
    if (left == null) {
      throw new IllegalArgumentException("must be not null");
    }
    return new Try<R>(left, (R) null);
  }

//  public static void require(Try<?>... required) throws ContentNotFoundException {
//    List<Throwable> failed = Arrays.stream(required).filter(t -> !t.isPresent())
//        .map(t -> t.throwable.orElseThrow()).collect(Collectors.toList());
//    if (!failed.isEmpty()) {
//      StringBuilder sb = new StringBuilder("required, but failed:");
//      failed.stream().filter(t -> StringUtils.isNotEmpty(t.getMessage()))
//          .forEach(e -> sb.append("\n").append(e.getMessage()));
//      throw new ContentNotFoundException(sb.toString(), failed.get(0));
//    }
//  }

  public static <T, U> Try<Tuple2<T, U>> zip(Try<T> first, Try<U> second) {
    if (first.right().isPresent() && second.right().isPresent()) {
      return Try.immediatesOf(new Tuple2<>(first.get(), second.get()));
    } else if (first.throwable.isPresent()) {
      return Try.immediatesOf(first.throwable.get());
    } else {
      return Try.immediatesOf(second.throwable.orElseThrow());
    }
  }
  
  public Optional<R> right(){
    return right;
  }
  
  public void throwIfMatch(Function<Throwable, ? extends RuntimeException> throwableMapper) {
    throwable.map(throwableMapper).ifPresent(x -> {
      throw x;
    });
  }

  public void throwIfMatch() {
    throwable.map(RuntimeException::new).ifPresent(x -> {
      throw x;
    });
  }

  public Try<R> fallback(ThrowingSupplier<R> supplier) {
    if (left.isPresent()) {
      return resultOf(supplier);
    }
    return this;
  }

  public Optional<R> filter(Predicate<? super R> arg0) {
    return right().filter(arg0);
  }

  public <U> Optional<U> flatMapOptional(
      Function<? super R, ? extends Optional<? extends U>> arg0) {
    return right().flatMap(arg0);
  }

  public R get() {
    return right().orElseThrow(() -> new RuntimeException(throwable.get()));
  }


  public void ifPresent(Consumer<? super R> arg0) {
    right().ifPresent(arg0);
  }

  public void ifPresentOrElse(Consumer<? super R> arg0, Runnable arg1) {
    right().ifPresentOrElse(arg0, arg1);
  }

  public boolean fold(Consumer<Throwable> leftConsumer, Consumer<? super R> rightConsumer) {
    if (isPresent()) {
      rightConsumer.accept(right.get());
//    } else if (rightSupplier.isPresent()) {
//      R r;
//      try {
//        r = rightSupplier.get().get();
//      } catch (Throwable t) {
//        leftConsumer.accept(t);
//        return false;
//      }
//      rightConsumer.accept(r);
    } else if (throwable.isPresent()) {
      leftConsumer.accept(left.get());
      return false;
    }
    return true;
  }

  public boolean isPresent() {
    return right().isPresent();
  }

  public <U> Optional<U> mapOptional(Function<? super R, ? extends U> arg0) {
    return right().map(arg0);
  }

  public Optional<R> or(Supplier<? extends Optional<? extends R>> arg0) {
    return right().or(arg0);
  }

  public R orElse(R arg0) {
    return right().orElse(arg0);
  }

  public R orElseGet(Supplier<? extends R> arg0) {
    return right().orElseGet(arg0);
  }

  public R orElseThrow() {
    return right().orElseThrow();
  }

  public <X extends Throwable> R orElseThrow(Supplier<? extends X> arg0) throws X {
    return right().orElseThrow(arg0);
  }

  public Stream<R> stream() {
    return right().stream();
  }

  public <V> Try<V> map(Function<R, ? extends V> mapping) {
    return new Try<V>(left.orElse(null), right().map(mapping).orElse(null));
  }

  public <V> Try<V> flatMap(Function<R, Try<V>> mapping) {
    return right().map(mapping).orElse(immediatesOf(left.orElseThrow()));
  }

}
