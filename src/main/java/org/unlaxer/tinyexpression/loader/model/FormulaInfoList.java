package org.unlaxer.tinyexpression.loader.model;

import static jp.caulis.fraud.model.calc.FormulaInfo.logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.TypedToken;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.loader.FormulaInfoBlocksParser;
import org.unlaxer.util.Tuple2;

import jp.caulis.fraud.model.CheckKind;

@V2CustomFunction
public class FormulaInfoList {
  
  List<FormulaInfo> infos;
  
  String output;

  public FormulaInfoList() {
    super();
    this.infos = Collections.emptyList();
    this.output = "";
  }

  public FormulaInfoList(List<FormulaInfo> infos) {
    super();
    this.infos = infos;
    
    output = infos.stream()
      .map(FormulaInfo::output)
      .collect(Collectors.joining("\n"));
  }
  
  public FormulaInfoList add(FormulaInfoList addingInfos) {
    return add(addingInfos.get());
  }
  
  public FormulaInfoList add(List<FormulaInfo> addingInfos) {
    List<FormulaInfo> newList = new ArrayList<>();
    newList.addAll(get());
    
    // keep unique
    Set<String> contents = 
        get().stream().map(FormulaInfo::output).collect(Collectors.toSet());
    
    String contentJoining= contents.stream().collect(Collectors.joining());
    
    for(FormulaInfo adding: addingInfos) {
      String hashByByteCode = adding.hashByByteCode;
      if(false ==contentJoining.contains(hashByByteCode)) {
        newList.add(adding);
      }
    }
    return new FormulaInfoList(newList);
  }
  
  public List<FormulaInfo> get() {
    return infos;
  }

  @Override
  public String toString() {
    return output;
  }
  
//  enum State{
//    oneLine,
//    formula,
//    javaCode,
//  }

//  @Deprecated
//  public static FormulaInfoList parseDeprecated(String text) {
//    return parseDeprecated(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
//  }
//
//  
//  @Deprecated
//  public static FormulaInfoList parseDeprecated(InputStream inputStream) {
//    
//    List<FormulaInfo> infos = new ArrayList<>();
//    
//    State state = State.oneLine;
//    
//    AtomicReference<FormulaInfo> formulaInfo = new AtomicReference<>(new FormulaInfo());
//    
//    try(InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
//        BufferedReader reader = new BufferedReader(inputStreamReader);){
//      String line ;
//      AtomicBoolean hasByteCode = new AtomicBoolean(false);
//      
//      while((line = reader.readLine()) != null) {
//        
//        boolean isEndOfPart = line.startsWith(END_MARK);
//        
//        if(line.startsWith("javaCode:")) {
//          state = State.javaCode;
//          continue;
//        }
//        
//        if(state == State.javaCode) {
//          if(line.startsWith("formula:")) {
//            formulaInfo.get().updateJavaCode();
//            state = State.formula;
//          }else {
//            formulaInfo.get().javaCode.add(line);
//          }
//        }
//        
//        if(line.startsWith("formula:")) {
//          state = State.formula;
//          continue;
//        }
//        if(state == State.formula) {
//          if(isEndOfPart) {
//            state = State.oneLine;
//          }else {
//            formulaInfo.get().formula.add(line);
//            continue;
//          }
//        }
//        
//        
//        if(isEndOfPart) {
//          infos.add(formulaInfo.get());
//          
//          if(false == hasByteCode.get() || formulaInfo.get().needsUpdate()) {
//            formulaInfo.get().updateFormula();
//            formulaInfo.get().updateCalculatorFromFormula();
//          }else {
//            formulaInfo.get().updateFormula();
//            formulaInfo.get().updateCalculatorWithByteCode();
//          }
//          formulaInfo.set(new FormulaInfo());
//
//          continue;
//        }
//        
//        if(state != State.oneLine) {
//          continue;
//        }
//        
//        
//        set(line, "periodStartInclusive", (value)->formulaInfo.get().periodStartInclusive = value);
//        set(line, "periodEndExclusive", (value)->formulaInfo.get().periodEndExclusive = value);
//        set(line, "siteId", (value)->formulaInfo.get().siteId = SiteId.of(value));
//        set(line, "checkKind", (value)->formulaInfo.get().checkKind = new DynamicCheckKind(value));
//        set(line, "hash", (value)->formulaInfo.get().hash = value);
//        set(line, "byteCode", (value)->{
//          formulaInfo.get().byteCodeAsHex = value;
//          formulaInfo.get().byteCode = Unchecked.supplier(()->Hex.decodeHex(value.toCharArray())).get();
//          hasByteCode.set(true);
//        });
//      }
//      
//      return new FormulaInfoList(infos);
//      
//    } catch (IOException e) {
//      logger.error("failed to parse formulaIfno",e);
//      return new FormulaInfoList();
//    }
//  }
  
  
  public static FormulaInfoList parse(String text , ClassLoader classLoader) {

    try {
      FormulaInfoBlocksParser formulaInfoBlocksParser = new FormulaInfoBlocksParser();
      
      StringSource stringSource = new StringSource(text);
      ParseContext parseContext = new ParseContext(stringSource);
      Parsed parsed = formulaInfoBlocksParser.parse(parseContext);
      TypedToken<FormulaInfoBlocksParser> typedToken = 
          parsed.getRootToken().typed(FormulaInfoBlocksParser.class);
      
      FormulaInfoBlocksParser parser = typedToken.getParser();
      FormulaInfoList extract = parser.extract(typedToken , classLoader);
      return extract;

    } catch (Throwable e) {
      e.printStackTrace();
      logger.error("failed to parse formulaIfno",e);
      return new FormulaInfoList();
    }
  }
  
  
  static boolean set(String line , String tag , Consumer<String> valueConsumer){
    if(line.startsWith(tag)) {
      Tuple2<String, Optional<String>> split = split(line);
      split._2.ifPresent(value->valueConsumer.accept(value));
      return true;
    }
    return false;
  }

  static Tuple2<String,Optional<String>> split(String line) {
    
    String[] split = line.split(":" , 2);
    if(split.length <=1) {
      return new Tuple2<String, Optional<String>>(line , Optional.empty());
    }else {
      return new Tuple2<String, Optional<String>>(split[0] , Optional.of(split[1].trim()));
    }
  }
  
  public Stream<String> nameStream(){
    return infos.stream().map(info->info.formulaName);
  }

  public static FormulaInfoList parse(InputStream binaryStream , ClassLoader classLoader) {
    try {
      return parse(IOUtils.toString(binaryStream, StandardCharsets.UTF_8) , classLoader);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
  
}