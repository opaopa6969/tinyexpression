package org.unlaxer.tinyexpression;

import java.util.Optional;

import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

public class Source{

  final String source;
  final FormulaInfo formulaInfo;
  public Source(String source, FormulaInfo formulaInfo) {
    super();
    this.source = source;
    this.formulaInfo = formulaInfo;
  }

  public Source(String source) {
    super();
    this.source = source;
    this.formulaInfo = null;
  }

  public String source() {
    return source;
  }

  public Optional<FormulaInfo> formulaInfo() {
    return Optional.ofNullable(formulaInfo);
  }
}