package org.unlaxer.tinyexpression.loader.model;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.unlaxer.compiler.InstanceAndByteCode;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.evaluator.javacode.ClassNameAndByteCode;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.model.FormulaInfoField.StringsToString;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.util.EpochPeriodForNavigable;
import org.unlaxer.util.MultiDateParser;
import org.unlaxer.util.digest.HEX;
import org.unlaxer.util.digest.MD5;

@V2CustomFunction
public class FormulaInfo{
  
  static Map<String,Function<Object,String>> converterByName = new HashMap<>();
  static Map<String,Field> fieldByName = new HashMap<>();
  
  static {
    Field[] fields2 = FormulaInfo.class.getFields();
    for (Field field : fields2) {
      try {
        FormulaInfoField annotation = field.getAnnotation(FormulaInfoField.class);
        if(annotation == null) {
          continue;
        }
        fieldByName.put(field.getName(),field);
        Class<? extends Function<Object, String>> converterClass = annotation.converter();
        Function<Object, String> converter = converterClass.getDeclaredConstructor().newInstance();
        converterByName.put(field.getName(), converter);
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  public boolean hasField(String fieldName) {
    return extraValueByKey.containsKey(fieldName) || 
        fieldByName.containsKey(fieldName);
  }
  
  public Optional<String> getValue(String fieldName) {
    String value = extraValueByKey.get(fieldName);
    if(value != null) {
      return Optional.of(value);
    }
    Field field = fieldByName.get(fieldName);
    if(field != null) {
      try {
        Object object = field.get(this);
        Function<Object, String> function = converterByName.get(fieldName);
        String apply = function.apply(object);
        return Optional.of(apply);
      } catch (IllegalArgumentException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    return Optional.empty();
  }
  
  
  @FormulaInfoField public String periodStartInclusive;
  @FormulaInfoField public String periodEndExclusive;
  // for multi tenancy
//  public SiteId siteId;
  // for formula name
//  public CheckKind checkKind;
  
  public Optional<String> multiTenancyId = Optional.empty();

  @Nullable
  @FormulaInfoField public String calculatorName;
  
//  @FormulaInfoField public String formulaName;
  
  @Nullable
  @FormulaInfoField public String dependsOn; //
  @Nullable
  @FormulaInfoField public ExpressionType resultType; // default Float
  @Nullable
  @FormulaInfoField public ExpressionType numberType; // default Float
//  @Nullable
//  @FormulaInfoField public String outputTo;  // this field used from org.unlaxer.tinyexpression.instances.TinyExpressionsExecutor.ResultConsumer
  
  @FormulaInfoField public String byteCodeAsHex;
  @FormulaInfoField public String formulaText;
  @FormulaInfoField public String javaCodeText;
  @FormulaInfoField public String hash;
  @FormulaInfoField public String hashByByteCode;
  public byte[] byteCode;
  @FormulaInfoField public String className;
  @FormulaInfoField public String classNameWithHash;
  private Calculator calculator;
  @FormulaInfoField(converter = StringsToString.class) public Collection<String> tags;
  
  @FormulaInfoField public String description;
  public List<ClassNameAndByteCode> classNameAndByteCodeList; 
  public Map<String,String> extraValueByKey;
  public List<String> text;
  private Class<?> calculatorReturningClass;
  private FormulaInfoState state;
  private final CalculatorCreator calculatorCreator;
  
  public enum FormulaInfoState{
    initialized,
    parsed,
    calculatorConstructed,
    ;
    public boolean formulaInfoConstructed() {
      return this == parsed || this == calculatorConstructed;
    }
  }
  
  private final FormulaInfoAdditionalFields additionalFields;
  
  public FormulaInfo(FormulaInfoAdditionalFields additionalFields,
      CalculatorCreator calculatorCreator) {
    super();
    extraValueByKey = new LinkedHashMap<>();
    text = new ArrayList<>();
    tags = new LinkedHashSet<>();
    classNameAndByteCodeList = new ArrayList<>();
    this.additionalFields = additionalFields;
    state = FormulaInfoState.initialized;
    this.calculatorCreator = calculatorCreator;
  }
  
  public void addAdditional(String key , String value) {
    extraValueByKey.put(key, value);
  }
  
  public void updateCalculatorFromFormula(ClassLoader classLoader) {
     
    calculator = calculatorCreator.create(
        formulaText, className, new SpecifiedExpressionTypes(resultType, numberType) , classLoader);
    
    this.byteCode = calculator.byteCode();
    
    javaCodeText  = calculator.javaCode();
    byteCodeAsHex = HEX.encode(byteCode);
    hashByByteCode = MD5.toHex(byteCode);
    
    calculator.setFormulaInfo(this);
    state = FormulaInfoState.calculatorConstructed;
  }
  
  public Class<?> calculatorReturningClass(){
    if(false == state.formulaInfoConstructed()) {
      throw new IllegalStateException();
    }
    if(calculatorReturningClass == null) {
      calculatorReturningClass = calculator.getReturningTypeClassFromImplements();
    }
    return calculatorReturningClass;
  }
  
  public <T> Calculator calculator(Class<T> returningType){
    return (Calculator) calculator;
  }
  
  public Calculator calculator(){
    return calculator;
  }


  public static FormulaInfo get(Calculator calculator) {
    FormulaInfo object = calculator.getObject(FormulaInfo.class.getSimpleName(), FormulaInfo.class);
    return object;
  }

  public static boolean hasTag(Calculator calculator, Collection<String> tags) {
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
    className = "Formula_" + getName()/*checkKind.name()*/; 
    classNameWithHash = className + "_" + hash.toUpperCase();
  }
  
  public void updateCalculatorWithByteCode(ClassLoader classLoader) {
    
    try {
      calculator = calculatorCreator.create(
          formulaText , javaCodeText , classNameWithHash ,
          new SpecifiedExpressionTypes(resultType , numberType),
          byteCode, hashByByteCode,
          classNameAndByteCodeList,
          Thread.currentThread().getContextClassLoader());
      calculator.setObject(FormulaInfo.class.getSimpleName(), this);
      state = FormulaInfoState.calculatorConstructed;
    }catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
  
  public String getName() {
    return additionalFields.getName(this);
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
    
    if(calculatorName != null) {
      builder
        .append("calculatorName")
        .append(":")
        .line(calculatorName);
    }
      
//    builder
//      .append(additionalFields.formulaNameAttributeName())
//      .append(":")
//    
////      .append("checkKind:")
//      .line(formulaName);
    
    if(dependsOn != null) {
      builder
        .append("dependsOn")
        .append(":")
        .line(dependsOn);
    }
    
    if(resultType != null) {
      builder
        .append("resultType")
        .append(":")
        .line(resultType.javaType().getTypeName());
    }
    
    if(numberType != null) {
      builder
        .append("numberType")
        .append(":")
        .line(numberType.javaType().getTypeName());
    }
    
    builder
      .append("hash:")
      .line(hash)
    
      .append("hashByByteCode:")
      .line(hashByByteCode);
    
    if(false ==calculator().instanceAndByteCodeList().isEmpty()) {
      for(InstanceAndByteCode instanceAndByteCode: calculator().instanceAndByteCodeList()) {
        builder
          .append("byteCode_")
          .append(instanceAndByteCode.className())
          .append(":")
          .line(HEX.encode(instanceAndByteCode.byteCode()));
      }
    }
    
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