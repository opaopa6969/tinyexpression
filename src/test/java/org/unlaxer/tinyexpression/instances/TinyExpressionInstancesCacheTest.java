package org.unlaxer.tinyexpression.instances;

import static org.junit.Assert.fail;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.junit.Test;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.FormulaInfoBlocksParser;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.loader.model.FormulaInfoList;
import org.unlaxer.util.Try;

public class TinyExpressionInstancesCacheTest {
  
  
  public static class FileBaseTinyExpressionInstancesCache implements TinyExpressionInstancesCache{
    
    public static final String FILENAME = "formulaInfo.txt"; 
    static FormulaInfoBlocksParser formulaInfoBlocksParser = new FormulaInfoBlocksParser();
    
    Path rootFolder;]
    FormulaInfoAdditionalFields formulaInfoAdditionalFields;
    Map<TenantID,List<Calculator<?>>> calculatorsByTenantId = new ConcurrentHashMap<>();
    
    
    public FileBaseTinyExpressionInstancesCache(Path rootFolder , 
        FormulaInfoAdditionalFields formulaInfoAdditionalFields) {
      super();
      this.rootFolder = rootFolder;
      this.formulaInfoAdditionalFields = formulaInfoAdditionalFields;
    }

    public  <T extends Comparable<? super Calculator<T>>> List<Calculator<? extends T>> get1(TenantID tenantID, Comparator<Calculator<T>> comparator , ClassLoader classLoader) {
      
      List<Calculator<?>> computeIfAbsent = 
          calculatorsByTenantId.computeIfAbsent(tenantID, 
              tenantId->{
                Path resolve = rootFolder.resolve(tenantId.asString()).resolve(FILENAME);
                try(InputStream inputStream = Files.newInputStream(resolve);){
                  Try<FormulaInfoList> parse = 
                      FormulaInfoList.parse(inputStream, formulaInfoAdditionalFields, classLoader);
                  
                  parse.throwIfMatch();
                  List<Calculator<? super T>> list = parse.right().get().get().stream()
                    .map(FormulaInfo::calculator)
                    .sorted(comparator)
                    .toList();
                  return list;
                  
                }
                
              
              }
          );
      return computeIfAbsent;
        
    }

    @Override
    public boolean clearCache(TenantID tenantID) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public List<Calculator<?>> get(TenantID tenantID, Comparator<Calculator<?>> comparator,
        Predicate<Calculator<?>> passFilter) {
      // TODO Auto-generated method stub
      return null;
    }
    
  }

  @Test
  public void test() {
    fail("Not yet implemented");
  }

}
