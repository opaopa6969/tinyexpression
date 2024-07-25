package org.unlaxer.tinyexpression.instances;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

public class ComparatorTest {
  
  public static class Foo<T>{
    
    T t;
    public Foo(T t) {
      super();
      this.t = t;
    }

    public T get() {
      return t;
    }
    
    public String toString() {
      return t.toString();
    }
  }
  
  

  @Test
  public void test() {
    
    
    Comparator<? super Foo<?>> comparator = new Comparator<Foo<?>>() {

      @Override
      public int compare(Foo<?> o1, Foo<?> o2) {
        
        String string1 = o1.get().toString();
        String string2 = o2.get().toString();
        return string1.compareTo(string2);
      }

    };
    {
      List<Foo<?>> list = new ArrayList<>(List.of(
          new Foo<Integer>(1),
          new Foo<String>("a"),
          new Foo<Integer>(0),
          new Foo<String>("9")
          ));
      
      list.sort(comparator);
      list.forEach(System.out::println);
      String collect = list.stream().map(Foo::toString)
        .collect(Collectors.joining());
      assertEquals("019a", collect);
    }
    
    {
      List<Foo<?>> list = new ArrayList<>(List.of(
          new Foo<Integer>(1),
          new Foo<String>("a"),
          new Foo<Integer>(0),
          new Foo<String>("9")
          ));

      String collect = list.stream().sorted(comparator)
        .map(Foo::toString)
        .collect(Collectors.joining());
      
      assertEquals("019a", collect);
      list.sort(comparator);
      list.forEach(System.out::println);
    }
  }
}
