package fk.prof.userapi.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rohit.patiyal on 26/04/17.
 */
public class StackLineParser {
    private static Map<String, String> signToPrimitiveMap = new HashMap<String, String>(){
        {
            put("V","void");
            put("Z","boolean");
            put("B","byte");
            put("C","char");
            put("S","short");
            put("I","int");
            put("J","long");
            put("F","float");
            put("D","double");
        }
    };
    public static String parse(String stackLine, boolean isArray, boolean commaBefore) {
        if(stackLine.matches(" .*") || stackLine.matches("\\(.*") || stackLine.matches("\\).*"))return stackLine.substring(0,1) + parse(stackLine.substring(1), false, false);
        if(stackLine.matches("[VZBCSIJFD].*")){
            return ((commaBefore)? "," :"")+ signToPrimitiveMap.get(stackLine.substring(0,1)) + ((isArray )? "[]" :"") + parse(stackLine.substring(1), false,true);
        }
        if(stackLine.matches("#([^\\s]*) (.*)")) {
            Matcher m = Pattern.compile("#([^\\s]*) (.*)").matcher(stackLine);
            if(m.matches())
                 return "." + m.group(1) + " " + parse(m.group(2), false,true);
        }
        if(stackLine.matches("\\[.*")){
            return parse(stackLine.substring(1), true, commaBefore);
        }
        if(stackLine.matches("L([^;]*);(.*)")) {
            Matcher m = Pattern.compile("L([^;]*);(.*)").matcher(stackLine);
            if(m.matches()) {
                return ((commaBefore)? "," :"") + m.group(1).replaceAll("/", ".") + parse(m.group(2), false,true);
            }
        }
        return stackLine;
    }

    public static String convertSignToJava(String stackLine) {
        return parse(stackLine, false, false);
    }
}
