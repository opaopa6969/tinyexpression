package org.unlaxer.tinyexpression.instances;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.FormulaInfoBlocksParser;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.loader.model.FormulaInfoList;
import org.unlaxer.util.Try;

public class TinyExpressionInstancesCacheTest {
  
  
  /**
   * 指定されたroot pathからtenantID毎にDirectoryを掘って中にformulaInfo.txtというfileを置いて
   * instanceを生成管理するTinyExpressionInstancesCacheの実装です
   *
   */
  public static class FileBaseTinyExpressionInstancesCache implements TinyExpressionInstancesCache{
    
    public static final String FILENAME = "formulaInfo.txt"; 
    static FormulaInfoBlocksParser formulaInfoBlocksParser = new FormulaInfoBlocksParser();
    
    Path rootFolder;
    FormulaInfoAdditionalFields formulaInfoAdditionalFields;
    Map<TenantID,List<Calculator<?>>> calculatorsByTenantId = new ConcurrentHashMap<>();
    
    
    public FileBaseTinyExpressionInstancesCache(Path rootFolder , 
        FormulaInfoAdditionalFields formulaInfoAdditionalFields) {
      super();
      this.rootFolder = rootFolder;
      this.formulaInfoAdditionalFields = formulaInfoAdditionalFields;
    }

    @Override
    public boolean clearCache(TenantID tenantID) {
      calculatorsByTenantId.remove(tenantID);
      return true;
    }


    @Override
    public List<Calculator<?>> get(TenantID tenantID, Comparator<Calculator<?>> comparator, ClassLoader classLoader) {
      return get(tenantID, comparator, x->true , classLoader);
    }

    @Override
    public List<Calculator<?>> get(TenantID tenantID, Comparator<Calculator<?>> comparator,
        Predicate<Calculator<?>> passFilter, ClassLoader classLoader) {
      
        List<Calculator<?>> matchedCalculators = 
            calculatorsByTenantId.computeIfAbsent(tenantID, 
                tenantId->{
                  Path resolve = rootFolder.resolve(tenantId.asString()).resolve(FILENAME);
                  try(InputStream inputStream = Files.newInputStream(resolve);){
                    Try<FormulaInfoList> parse = 
                        FormulaInfoList.parse(inputStream, formulaInfoAdditionalFields, classLoader);
                    
                    parse.throwIfMatch();
                    List<Calculator<?>> list = parse.right().get().get().stream()
                      .map(FormulaInfo::calculator)
                      .filter(passFilter)
                      .sorted(comparator)
                      .collect(Collectors.toList());
                    return list; 
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                }
            );
        return matchedCalculators;
      }
  }
}
