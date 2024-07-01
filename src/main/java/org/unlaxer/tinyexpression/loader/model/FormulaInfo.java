package org.unlaxer.tinyexpression.loader.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV2;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.util.EpochPeriodForNavigable;
import org.unlaxer.util.MultiDateParser;
import org.unlaxer.util.digest.HEX;
import org.unlaxer.util.digest.MD5;

@V2CustomFunction
public class FormulaInfo{
  
  public String periodStartInclusive;
  public String periodEndExclusive;
  // for multi tenancy
//  public SiteId siteId;
  // for formula name
//  public CheckKind checkKind;
  
  public Optional<String> multiTenancyId = Optional.empty();

  public String formulaName;
  
  public String byteCodeAsHex;
  public String formulaText;
  public String javaCodeText;
  public String hash;
  public String hashByByteCode;
  public byte[] byteCode;
  public String className;
  public String classNameWithHash;
  public Calculator<Float> calculator;
  public Collection<String> tags;
  public String description;
  public Map<String,String> extraValueByKey;
  public List<String> text;
  
  private final FormulaInfoAdditionalFields additionalFields;
  
  public FormulaInfo(FormulaInfoAdditionalFields additionalFields) {
    super();
    extraValueByKey = new LinkedHashMap<>();
    text = new ArrayList<>();
    tags = new LinkedHashSet<>();
    this.additionalFields = additionalFields; 
  }
  



  public void updateCalculatorFromFormula(ClassLoader classLoader) {
    calculator = new JavaCodeCalculatorV2(
        formulaText, className, classLoader);
    
    this.byteCode = calculator.byteCode();
    
    javaCodeText  = calculator.javaCode();
    byteCodeAsHex = HEX.encode(byteCode);
    hashByByteCode = MD5.toHex(byteCode);
    
    calculator.setObject(FormulaInfo.class.getSimpleName(), this);
  }
  

  public static FormulaInfo get(Calculator<?> calculator) {
    FormulaInfo object = calculator.getObject(FormulaInfo.class.getSimpleName(), FormulaInfo.class);
    return object;
  }

  public static boolean hasTag(Calculator<?> calculator, Collection<String> tags) {
    FormulaInfo formulaInfo = get(calculator);
    for (String tag : tags) {
      if (formulaInfo.tags.contains(tag)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasTag(Collection<String> targetTags) {
    for (String tag : targetTags) {
      if (tags.contains(tag)) {
        return true;
      }
    }
    return false;
  }
  
  public void updateFormula(){
    updateHash();
    updateClassName();
  }
  
  public void updateHash() {
    hash = MD5.toHex(formulaText);
  }
  
  public void updateClassName() {
    className = "Formula_" + formulaName/*checkKind.name()*/; 
    classNameWithHash = className + "_" + hash.toUpperCase();
  }
  
  public void updateCalculatorWithByteCode(ClassLoader classLoader) {
    
    try {
      calculator = new JavaCodeCalculatorV2(
          formulaText , javaCodeText , classNameWithHash ,byteCode, hashByByteCode,
          Thread.currentThread().getContextClassLoader());
      calculator.setObject(FormulaInfo.class.getSimpleName(), this);
    }catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
  
  public enum TagKind {

    NORMAL;

    public static String outputTags(Collection<String> tags) {
      if (tags.isEmpty()) {
        return TagKind.NORMAL.toString();
      } else {
        return tags.stream().collect(Collectors.joining(","));
      }
    }

  };
  
  public static String END_MARK="---END_OF_PART---";
  
  public String output() {
    
    SimpleBuilder builder = new SimpleBuilder();
    
    extraValueByKey.forEach((key,value)->{
      builder
        .append(key)
        .append(":")
        .line(value);
    });
    
    builder
      .append("tags:")
      .line(TagKind.outputTags(tags))
      .append("description:")
      .line(description)
      .append("periodStartInclusive:")
      .line(periodStartInclusive)
      
      .append("periodEndExclusive:")
      .line(periodEndExclusive);
      
    if(multiTenancyId.isPresent()) {
//        builder.append("siteId:")
      builder
        .append(additionalFields.multiTenancyAttributeName().get())
        .append(":")
        .line(multiTenancyId.get());
    }
      
    builder
      .append(additionalFields.formulaNameAttributeName())
      .append(":")
    
//      .append("checkKind:")
      .line(formulaName)
    
      .append("hash:")
      .line(hash)
    
      .append("hashByByteCode:")
      .line(hashByByteCode);
    
   if(byteCodeAsHex != null) {
      builder
        .append("byteCode:")
        .line(byteCodeAsHex);
    }
      
    if(javaCodeText != null) {
      builder
        .line("javaCode:")
        .line(javaCodeText);
    }
    
    builder
      .line("formula:")
      .line(formulaText);

    builder
      .line(END_MARK);
    
    
    return builder.toString();
  }
  
  
  public EpochPeriodForNavigable getPeriodNavigable() {
    
    return new EpochPeriodForNavigable(
      MultiDateParser.toEpochMilli(periodStartInclusive , Optional.empty()).orElse(0L),
      MultiDateParser.toEpochMilli(periodEndExclusive, Optional.empty()).orElse(Long.MAX_VALUE)
    );
  }
  
  
  @Override
  public String toString() {
    return output();
  }

  public boolean needsUpdate() {
    if(hash == null) {
      return true;
    }
    return false == hash.equals(MD5.toHex(formulaText));
  }
  
  
}