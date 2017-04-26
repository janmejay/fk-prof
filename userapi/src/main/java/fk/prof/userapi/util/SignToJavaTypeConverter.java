package fk.prof.userapi.util;

import javafx.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class which extracts Java names from their corresponding JVM Types signatures
 * from string of format className#methodName (argTypes)retType
 * Created by rohit.patiyal on 25/04/17.
 */
public class SignToJavaTypeConverter {
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
    private static Pattern classAndMethodNamePattern = Pattern.compile("(\\S*)#(\\S*)");
    private static Pattern fullyQualifiedClassPattern = Pattern.compile("L([^; ]*);(.*)");
    private static Pattern arrayWithTypePattern = Pattern.compile("\\[.(\\S*)");
    private static Pattern classMethodWithArgAndRetPattern = Pattern.compile("(\\S*) \\((\\S*)\\)(\\S*)");

    public static String getMethod(String classMethodWithArgAndRet){
        String methodName = "";
        Matcher m = classMethodWithArgAndRetPattern.matcher(classMethodWithArgAndRet);
        if(m.matches()){
            String classAndMethodName = m.group(1);
            m = classAndMethodNamePattern.matcher(classAndMethodName);
            if(m.matches()){
               methodName = m.group(2);
            }
        }
        return methodName;
    }

    public static String getMethodClass(String classMethodWithArgAndRet){
        String className = "";
        Matcher m = classMethodWithArgAndRetPattern.matcher(classMethodWithArgAndRet);
        if(m.matches()){
            String classAndMethodName = m.group(1);
            m = classAndMethodNamePattern.matcher(classAndMethodName);
            if(m.matches()){
                className = m.group(1);
                m = fullyQualifiedClassPattern.matcher(className);
                if(m.matches()){
                    className = m.group(1);
                }
                className = className.replaceAll("/",".");
            }
        }
        return className;
    }

    public static List<String> getArgTypeList(String classMethodWithArgAndRet) {
        List<String> argTypeList = new ArrayList<>();
        Matcher m = classMethodWithArgAndRetPattern.matcher(classMethodWithArgAndRet);
        if (m.matches()) {
            String argTypes = m.group(2);
            while(argTypes.length() >0){
                m = arrayWithTypePattern.matcher(argTypes);
                String arg = "";
                String arrayOrNot = "";
                if(m.matches()){
                    arrayOrNot = "[]";
                    argTypes = argTypes.substring(1);
                }
                Pair<String, String> pair = getNextArg(argTypes);
                arg = pair.getKey();
                argTypes = pair.getValue();
                argTypeList.add(arg + arrayOrNot);
            }
        }
        return argTypeList;
    }

    public static String getRetType(String classMethodWithArgAndRet) {
        String retType = "";
        Matcher m = classMethodWithArgAndRetPattern.matcher(classMethodWithArgAndRet);
        if (m.matches()) {
            retType = m.group(3);
            m = arrayWithTypePattern.matcher(retType);
            String arrayOrNot = "";
            if (m.find()) {
                arrayOrNot = "[]";
                retType = retType.substring(1);
            }
            Pair<String, String> pair = getNextArg(retType);
            retType = pair.getKey() + arrayOrNot;
        }
        return retType;
    }
    private static Pair<String,String> getNextArg(String argTypes) {
        String arg = "";
        String newArgTypes = "";
        String nextChar = argTypes.substring(0,1);
        if(nextChar.equals("L")){
            Matcher m = fullyQualifiedClassPattern.matcher(argTypes);
            if(m.matches()){
                arg = m.group(1);
                arg = arg.replaceAll("/",".");
                newArgTypes = m.group(2);
            }else{
                newArgTypes = "";
            }
        }else{
            arg = signToPrimitiveMap.get(argTypes.substring(0,1));
            newArgTypes = argTypes.substring(1);
        }
        return new Pair<>(arg, newArgTypes);
    }
}
