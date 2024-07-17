package org.unlaxer.tinyexpression.loader.model;

public interface Formula {

  public float calc();


  public class ImmediateValueFormula implements Formula {

    final float value;

    public ImmediateValueFormula(float value) {
      super();
      this.value = value;
    }

    @Override
    public float calc() {
      return value;
    }
  }

}
