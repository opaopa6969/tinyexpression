package org.unlaxer.tinyexpression.loader.model;


import org.unlaxer.tinyexpression.CalculationContext;

import jp.caulis.fraud.model.CheckResult;
import jp.caulis.fraud.model.SiteId;

@V2CustomFunction
public class CalculateRelativeSuspiciousValueParameter {

  public CheckResult checkResult;
  public CalculationContext calculationContext;
  public SiteId siteId;

  @SuppressWarnings("unused")
  private CalculateRelativeSuspiciousValueParameter() {
    
  }
  
  public CalculateRelativeSuspiciousValueParameter(CheckResult checkResult, CalculationContext calculationContext,
      SiteId siteId) {
    super();
    this.checkResult = checkResult;
    this.calculationContext = calculationContext;
    this.siteId = siteId;
  }
}