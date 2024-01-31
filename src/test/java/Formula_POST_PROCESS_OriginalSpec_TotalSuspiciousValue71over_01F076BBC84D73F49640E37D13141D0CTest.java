import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;

public class Formula_POST_PROCESS_OriginalSpec_TotalSuspiciousValue71over_01F076BBC84D73F49640E37D13141D0CTest {

  @Test
  public void test() {
    
    var formula_= new Formula_POST_PROCESS_OriginalSpec_TotalSuspiciousValue71over_01F076BBC84D73F49640E37D13141D0C();
    CalculationContext newContext = CalculationContext.newContext();
  
    {
      Float apply = formula_.apply(newContext, null);
      System.out.println(apply);
    }
    {
      newContext.set("default_totalSuspiciousValue", 70);
      Float apply = formula_.apply(newContext, null);
      System.out.println(apply);
      
    }
    
    {
      newContext.set("default_totalSuspiciousValue", 71);
      Float apply = formula_.apply(newContext, null);
      System.out.println(apply);
      
    }
  }

}
