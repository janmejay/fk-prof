package fk.prof.userapi.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A utility class which parses and converts Java names from their corresponding JVM Types signatures
 - from string of format className#methodName (argTypes)retType
 * Created by rohit.patiyal on 26/04/17.
 */
public class StackLineParser {
    private static Map<Character, String> signToPrimitiveMap = new HashMap<Character, String>(){
        {
            put('V',"void");
            put('Z',"boolean");
            put('B',"byte");
            put('C',"char");
            put('S',"short");
            put('I',"int");
            put('J',"long");
            put('F',"float");
            put('D',"double");
        }
    };

    public static String convertSignToJava(String stackLine){
        if(stackLine.matches("L\\S+;#\\S+ \\(\\S*\\)\\S+")){
            StringBuilder sb = new StringBuilder();
            int index = 0;
            index = parseJavaType(sb, stackLine, index);
            sb.append(".");
            index = parseMethodName(sb, stackLine, index);
            sb.append("-");
            index = parseArgs(sb, stackLine, index);
            sb.append("-");
            parseRet(sb, stackLine, index);
            return sb.toString();
        }else{
            return stackLine;
        }
    }

    private static int parseJavaType(StringBuilder sb, String stackLine, int index) {
        char currChar = stackLine.charAt(index);
        if(currChar == '['){
            int nextIndex = parseJavaType(sb, stackLine, index + 1 );
            sb.append("[]");
            return nextIndex;
        }else if(currChar == 'L'){
            int endIndex = stackLine.indexOf(';', index);
            int oldLength = sb.length();
            sb.append(stackLine, index+1, endIndex);
            int newLength = sb.length();
            for(int i=oldLength; i<newLength; i++){
                if(sb.charAt(i) == '/'){
                    sb.setCharAt(i,'.');
                }
            }
            return endIndex + 1;
        }else if(signToPrimitiveMap.containsKey(currChar)){
            sb.append(signToPrimitiveMap.get(currChar));
        }
        return index+1;
    }

    private static int parseArgs(StringBuilder sb, String stackLine, int index) {
        if(stackLine.charAt(index) == '(') {
            sb.append('(');
            index++;
        }
        boolean firstArg = true;
        while(stackLine.charAt(index) != ')') {
            if(firstArg) {
                firstArg = false;
            }else{
                sb.append(',');
            }
            index = parseJavaType(sb, stackLine, index );
        }
        sb.append(')');
        return index + 1;
    }

    private static int parseRet(StringBuilder sb, String stackLine, int index) {
        return parseJavaType(sb,stackLine, index);
    }

    private static int parseMethodName(StringBuilder sb, String stackLine, int index) {
        if(stackLine.charAt(index) == '#') {
            int endIndex = stackLine.indexOf(" ");
            sb.append(stackLine, index+1, endIndex);
            return endIndex + 1;
        }else{
            return index+1;
        }
    }



}
