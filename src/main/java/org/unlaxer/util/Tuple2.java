package org.unlaxer.util;

public class Tuple2<T,U>{
  public final T _1;
  public final U _2;
  public Tuple2(T t, U u) {
    super();
    this._1 = t;
    this._2 = u;
  }
  
  public U right(){
    
    return _2;
  }
  
  public T left(){
    
    return _1;
  }
  
  public U _2(){
    
    return _2;
  }
  
  public T _1(){
    
    return _1;
  }

  
}