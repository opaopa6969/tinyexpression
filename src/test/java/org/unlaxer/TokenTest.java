package org.unlaxer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;

import org.junit.Ignore;
import org.junit.Test;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableParser;

public class TokenTest {

  @Test
  @Ignore
  public void test() {

//    // 匿名クラスを使って Function<String, Integer> を定義
//    Function<TypedToken<NumberVariableParser>, Integer> func = new Function<TypedToken<NumberVariableParser>, Integer>() {
//        @Override
//        public Integer apply(TypedToken<NumberVariableParser> s) {
//            return 1;
//        }
//    };
//
//    // Function インターフェースの型情報を取得
//    Type[] genericInterfaces = func.getClass().getGenericInterfaces();
//    for (Type genericInterface : genericInterfaces) {
//        if (genericInterface instanceof ParameterizedType) {
//            ParameterizedType pt = (ParameterizedType) genericInterface;
//            System.out.println("Raw Type: " + pt.getRawType());
//            for (Type typeArg : pt.getActualTypeArguments()) {
//                System.out.println("Type Argument: " + typeArg);
//            }
//        }
//    }

    Function<TypedToken<NumberVariableParser> , String> function = this::out;

    Type[] genericInterfaces = function.getClass().getGenericInterfaces();
    for (Type genericInterface : genericInterfaces) {
        if (genericInterface instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericInterface;
            System.out.println("Raw Type: " + pt.getRawType());
            for (Type typeArg : pt.getActualTypeArguments()) {
                System.out.println("Type Argument: " + typeArg);
            }
        }
    }
  }

  String out(TypedToken<NumberVariableParser> p) {
    return "";
  }
}
