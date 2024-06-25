package org.unlaxer.tinyexpression.loader.model;

public abstract class FormulaExportBase implements FormulaExport {

  final String name;
  final String description;
  final float value;


  public FormulaExportBase(String name, String description, float value) {
    super();
    this.name = name;
    this.description = description;
    this.value = value;
  }


  @Override
  public String name() {
    return name;
  }


  @Override
  public String description() {
    return description;
  }


  @Override
  public float value() {
    return value;
  }
}
