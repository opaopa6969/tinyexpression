package org.unlaxer.tinyexpression.loader.model;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.TypedToken;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.FormulaInfoBlocksParser;
import org.unlaxer.util.StringUtils;
import org.unlaxer.util.Try;
import org.unlaxer.util.Tuple2;

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
  
  
  public static Try<FormulaInfoList> parse(String text ,
      FormulaInfoAdditionalFields additionalFields, ClassLoader classLoader) {

    try {
      FormulaInfoBlocksParser formulaInfoBlocksParser = new FormulaInfoBlocksParser();
      
      StringSource stringSource = new StringSource(text);
      ParseContext parseContext = new ParseContext(stringSource);
      Parsed parsed = formulaInfoBlocksParser.parse(parseContext);
      TypedToken<FormulaInfoBlocksParser> typedToken = 
          parsed.getRootToken().typed(FormulaInfoBlocksParser.class);
      
      FormulaInfoBlocksParser parser = typedToken.getParser();
      FormulaInfoList extract = parser.extract(typedToken , additionalFields , classLoader);
      return Try.immediatesOf(extract);

    } catch (Throwable e) {
      
      return Try.immediatesOf(e);
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

  public static Try<FormulaInfoList> parse(InputStream binaryStream , FormulaInfoAdditionalFields additionalFields, ClassLoader classLoader) {
      return parse(StringUtils.from(binaryStream, StandardCharsets.UTF_8) , additionalFields, classLoader);
  }
  
}