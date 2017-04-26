package fk.prof.userapi.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static List<JVMTypeSignatureParser> parsers = new ArrayList<JVMTypeSignatureParser>(){
        {
            add(StackLineParser::parseTerminalType);
            add(StackLineParser::parseMethodName);
            add(StackLineParser::parseArgs);
            add(StackLineParser::parseRet);
        }
    } ;

    public static String convertJVMTypeSignToJava(String text){
        StringBuilder sb = new StringBuilder();
        int textIndex = 0;
        int sbLength = 0;
        text = text.replace('/','.');
        for(JVMTypeSignatureParser parser : parsers){
            int res = parser.parse(sb, text, textIndex);
            if(res == -1){
                return sb.substring(0, sbLength) + text.substring(textIndex);
            }
            textIndex = res;
            sbLength = sb.length();
        }
        return sb.toString();
    }



    @FunctionalInterface
    private interface JVMTypeSignatureParser{
        int parse(StringBuilder sb, String text, int textIndex);
    }

    private static int parseTerminalType(StringBuilder sb, String text, int textIndex){
        if(textIndex >= text.length()) return -1;
        char currChar = text.charAt(textIndex);
        if(currChar == '['){
            int nextIndex = parseTerminalType(sb, text, textIndex + 1 );
            sb.append("[]");
            return nextIndex;
        }else if(currChar == 'L'){
            int endIndex = text.indexOf(';', textIndex);
            if(endIndex == -1) return -1;
            sb.append(text, textIndex+1, endIndex);
            return endIndex + 1;
        }else if(signToPrimitiveMap.containsKey(currChar)){
            sb.append(signToPrimitiveMap.get(currChar));
            return textIndex+1;
        }
        return -1;
    }

    private static int parseArgs(StringBuilder sb, String text, int textIndex) {
        if(textIndex + 1 >= text.length()) return -1;
        if(text.charAt(textIndex) == ' ' && text.charAt(textIndex+1) == '(') {
            sb.append(" (");
            textIndex += 2;
            boolean argsFound = false;
            int i = textIndex;

            while (i < text.length() && text.charAt(i) != ')' && (i = parseTerminalType(sb, text, textIndex)) != -1) {
                sb.append(',');
                textIndex = i;
                argsFound = true;
            }
            if(i >= text.length()) return -1;
            if (text.charAt(textIndex) == ')') {
                textIndex += 1;
                if (argsFound) {
                    sb.setCharAt(sb.length() - 1, ')');
                } else {
                    sb.append(')');
                }
                return textIndex;
            }
        }
        return -1;
    }

    private static int parseMethodName(StringBuilder sb, String text, int textIndex) {
        if(textIndex >= text.length()) return -1;
        if(text.charAt(textIndex) == '#') {
            sb.append(".");
            int endIndex = text.indexOf(" ");
            if(endIndex == -1) return -1;
            sb.append(text, textIndex+1, endIndex);
            return endIndex;
        }
        return -1;
    }

    private static int parseRet(StringBuilder sb, String text, int textIndex) {
        return parseTerminalType(sb, text, textIndex);
    }


}
