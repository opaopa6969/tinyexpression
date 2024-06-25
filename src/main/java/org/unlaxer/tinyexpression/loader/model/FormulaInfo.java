package org.unlaxer.tinyexpression.loader.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV2;

import jp.caulis.fraud.GradleStructure;
import jp.caulis.fraud.ProjectContext;
import jp.caulis.fraud.SubProjects;
import jp.caulis.fraud.model.CheckKind;
import jp.caulis.fraud.model.SiteId;
import jp.caulis.fraud.util.MultiDateParser;
import jp.caulis.fraud.util.SimpleBuilder;
import jp.caulis.fraud.util.collection.EpochPeriodForNavigable;
import net.openhft.compiler.CachedCompiler;
import net.openhft.compiler.CompilerUtils;

@V2CustomFunction
public class FormulaInfo{
  
  static Logger logger = LoggerFactory.getLogger(FormulaInfo.class);
  
  public String periodStartInclusive;
  public String periodEndExclusive;
  public SiteId siteId;
  public CheckKind checkKind;
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
  
  public FormulaInfo() {
    super();
    extraValueByKey = new LinkedHashMap<>();
    text = new ArrayList<>();
    tags = new LinkedHashSet<>();
  }
  



  public void updateCalculatorFromFormula(ClassLoader classLoader) {
    calculator = new JavaCodeCalculatorV2(
        formulaText, className, classLoader);
    
    this.byteCode = calculator.byteCode();
    
    javaCodeText  = calculator.javaCode();
    byteCodeAsHex = Hex.encodeHexString(byteCode);
    hashByByteCode = DigestUtils.md5Hex(byteCode);
    
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
    hash = DigestUtils.md5Hex(formulaText);
  }
  
  public void updateClassName() {
    className = "Formula_" + checkKind.name(); 
    classNameWithHash = className + "_" + hash.toUpperCase();
  }
  
  public void updateCalculatorWithByteCode(ClassLoader classLoader) {
    
//    byte[] bytes = "Formula_customFuncForDefaultSiteId_2485c6997589b545ea313e711ac24395".getBytes();
//    byte[] bytes2 = classNameWithHash.getBytes();
//    
//    boolean equals = Arrays.equals(bytes, bytes2);
//
//    if(equals == false ) {
//      throw new IllegalArgumentException();
//    }
    try {
      
//      ByteArrayClassLoader byteArrayClassLoader = new ByteArrayClassLoader(classLoader);
//      
////      Class<TokenBaseOperator<CalculationContext, Float>> defineClass = 
////          byteArrayClassLoader.define(new String(classNameWithHash), byteCode);
//      Class<TokenBaseOperator<CalculationContext, Float>> defineClass = 
//          byteArrayClassLoader.define("Formula_customFuncForDefaultSiteId_2485c6997589b545ea313e711ac24395", byteCode);
      
    
      
//      @SuppressWarnings("unchecked")
//      Class<TokenBaseOperator<CalculationContext, Float>> defineClass = 
//          CompilerUtils.defineClass(
//              classLoader, 
//              new String(classNameWithHash),
////              "Formula_customFuncForDefaultSiteId_2485C6997589B545EA313E711AC24395", 
//              byteCode);
//      final String f = classNameWithHash.intern();
//      A a = new A(f);
//      Class<TokenBaseOperator<CalculationContext, Float>> defineClass = CompilerUtils.defineClass(classLoader, a.a, byteCode);
//      Class<TokenBaseOperator<CalculationContext, Float>> defineClass = getCalculationClass(classLoader, classNameWithHash, byteCode);
      calculator = new JavaCodeCalculatorV2(
          formulaText , javaCodeText , classNameWithHash ,byteCode, hashByByteCode,
          Thread.currentThread().getContextClassLoader());
      calculator.setObject(FormulaInfo.class.getSimpleName(), this);
    }catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
  
  public static class DefineClass_ implements java.util.function.Supplier<Class<TokenBaseOperator<CalculationContext, Float>>>{
    @SuppressWarnings("unchecked")
    public Class<TokenBaseOperator<CalculationContext, Float>> get(){
      return CompilerUtils.defineClass(jp.caulis.fraud.model.calc.FormulaInfo.classLoader_.get(), 
          "Formula_customFuncForDefaultSiteId_2485c6997589b545ea313e711ac24395", 
          jp.caulis.fraud.model.calc.FormulaInfo.byteCode_.get());
    }
  }
  
  @SuppressWarnings("unchecked")
  static Class<TokenBaseOperator<CalculationContext, Float>> getCalculationClass(ClassLoader classLoader , String className  , byte[] byteCode){
    
    SimpleBuilder builder = new SimpleBuilder();
    String sourceCode = builder
      .line("  public class DefineClass_"+className+" implements java.util.function.Supplier<Class<org.unlaxer.tinyexpression.TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>>>{")
      .line("    public Class<org.unlaxer.tinyexpression.TokenBaseOperator<org.unlaxer.tinyexpression.CalculationContext, Float>> get(){")
      .line("      return net.openhft.compiler.CompilerUtils.defineClass(jp.caulis.fraud.model.calc.FormulaInfo.classLoader_.get(), ")
      .append("          \"")
      .append(className)
      .line("\", ")
      .line("          jp.caulis.fraud.model.calc.FormulaInfo.byteCode_.get());")
      .line("    }")
      .line("  }")
      .toString();
    
    System.out.println(sourceCode);
    
    classLoader_.set(classLoader);
    byteCode_.set(byteCode);
    
    try {
      File sourceDir = ProjectContext.getPath(SubProjects.api_base, GradleStructure.generatedJava).toFile();
      File classDir = ProjectContext.getPath(SubProjects.api_base, GradleStructure.mainJavaClasss).toFile();
      Class<?> loadFromJava = new CachedCompiler(sourceDir, classDir).loadFromJava( "DefineClass_"+className , sourceCode);
      java.util.function.Supplier<Class<TokenBaseOperator<CalculationContext, Float>>> newInstance = 
          (Supplier<Class<TokenBaseOperator<CalculationContext, Float>>>) loadFromJava.getConstructor().newInstance();
      return newInstance.get();
    } catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }

    
  }
  
  public static class A{
    public final String a;

    public A(String a) {
      super();
      this.a = a;
    }
    
  }
  
  public static ThreadLocal<byte[]> byteCode_ = new ThreadLocal<>();
  public static ThreadLocal<ClassLoader> classLoader_= new ThreadLocal<>();

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
      .line(periodEndExclusive)
      
      .append("siteId:")
      .line(siteId.toString())
      
      .append("checkKind:")
      .line(checkKind.toString())
    
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

  
//  static String toString(List<String> text) {
//    
//    return text.stream().collect(Collectors.joining("\n"));
//  }
//  
//  static String toStringWithTrim(List<String> text) {
//    
//    return text.stream()
//      .filter(line -> (false == line.trim().startsWith("#")))
//      .filter(line -> (false == line.trim().equals("")))
//      .collect(Collectors.joining("\n"));
//  }
//

  public boolean needsUpdate() {
    if(hash == null) {
      return true;
    }
    return false == hash.equals(DigestUtils.md5Hex(formulaText));
  }
  
  
}