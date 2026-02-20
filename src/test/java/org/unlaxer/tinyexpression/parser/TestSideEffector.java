package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.tinyexpression.CalculationContext;

public class TestSideEffector {


  public float setBlackList(CalculationContext calculationContext, float originalReturning) {


    Optional<String> userHash = calculationContext.getObject("userHash", String.class);

    // side effects. if you need persists blacklist, then put blackListDao.set(userHash).
    userHash.map(x -> "set blackList :").ifPresent(System.out::println);


    // modify value if you needed.
    return originalReturning * 2;
  }


  public float setBlackList(CalculationContext calculationContext, float originalReturning,
      boolean isWitchingHour) {


    Optional<String> userHash = calculationContext.getObject("userHash", String.class);

    // side effects. if you need persists blacklist, then put blackListDao.set(userHash).
    userHash.map(x -> "set blackList :").ifPresent(System.out::println);

    return isWitchingHour ? originalReturning * 2 : originalReturning;
  }

  public String setBlackList(CalculationContext calculationContext, String originalReturning) {


    Optional<String> userHash = calculationContext.getObject("userHash", String.class);

    // side effects. if you need persists blacklist, then put blackListDao.set(userHash).
    userHash.map(x -> "set blackList :").ifPresent(System.out::println);


    // modify value if you needed.
    return originalReturning + ": hoge";
  }
  
  public float booleanToFloatMethod(CalculationContext calculationContext, boolean inputValue) {
    
    return inputValue ? 69f:6969f;
  }
  
  public float salary(CalculationContext calculationContext, float averageSalary , String name) {
    
    return name.contains("Dr.") ? averageSalary * 2 : averageSalary;
  }
  
  /**
   * 
   * @param calculationContext
   * @param date (yyyy/MM/dd)
   * @return
   */
  public boolean beforeSupecifiedDate(CalculationContext calculationContext, String date) {
    
    return true;
  }
  
  public float getAge(CalculationContext calculationContext, String date) {
    return 0;
  }
  
  public String getYear(CalculationContext calculationContext, String date) {
    String[] split = date.split("/");
    return split[0];
  }


}
