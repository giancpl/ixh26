package com.example.photoanchor;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.json.*;

/** Bounded attachment JSON; reject duplicates rather than silently overwriting. */
public final class StrictJson {
    private StrictJson() {}
    public static JSONObject parseObject(byte[] raw) throws Exception { return parseObject(raw,1024*1024); }
    public static JSONObject parseObject(byte[] raw,int limit) throws Exception {
        if(limit<1||limit>4*1024*1024||raw==null||raw.length<1||raw.length>limit)throw new IllegalArgumentException("JSON size");
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        source(text);
        JSONTokener t=new JSONTokener(text);Object result=value(t,0);
        if(!(result instanceof JSONObject)||t.nextClean()!=0)throw new IllegalArgumentException("JSON object required");
        return (JSONObject)result;
    }
    private static Object value(JSONTokener t,int depth)throws Exception {
        if(depth>16)throw new IllegalArgumentException("JSON depth");
        char c=t.nextClean();
        if(c=='{') {
            JSONObject out=new JSONObject();HashSet<String> keys=new HashSet<>();
            c=t.nextClean();if(c=='}')return out;t.back();
            while(true) {
                if(t.nextClean()!='"')throw new IllegalArgumentException("JSON key");t.back();
                Object key=t.nextValue();if(key instanceof String)text((String)key);
                if(!(key instanceof String)||!keys.add((String)key)||t.nextClean()!=':')throw new IllegalArgumentException("Duplicate/key");
                out.put((String)key,value(t,depth+1));c=t.nextClean();if(c=='}')return out;
                if(c!=',')throw new IllegalArgumentException("JSON separator");
            }
        }
        if(c=='[') {
            JSONArray out=new JSONArray();c=t.nextClean();if(c==']')return out;t.back();
            while(true){out.put(value(t,depth+1));c=t.nextClean();if(c==']')return out;if(c!=',')throw new IllegalArgumentException("JSON separator");}
        }
        if(c==0)throw new IllegalArgumentException("Missing JSON value");
        t.back();Object out=t.nextValue();
        if(out instanceof Double&&!Double.isFinite((Double)out))throw new IllegalArgumentException("Nonfinite");
        if(out instanceof String&&c!='"')throw new IllegalArgumentException("Unquoted JSON value");
        if(out instanceof String)text((String)out);
        return out;
    }
    private static void source(String s){
        boolean quoted=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(quoted){
                if(c=='"'){quoted=false;continue;}
                if(c<32)throw new IllegalArgumentException("Unescaped JSON control");
                if(c=='\\'){
                    if(++i>=s.length())throw new IllegalArgumentException("JSON escape");
                    char e=s.charAt(i);
                    if(e=='u'){
                        if(i+4>=s.length()||!s.substring(i+1,i+5).matches("[0-9a-fA-F]{4}"))throw new IllegalArgumentException("Unicode escape");
                        i+=4;
                    }else if("\"\\/bfnrt".indexOf(e)<0)throw new IllegalArgumentException("JSON escape");
                }
                continue;
            }
            if(c=='"'){quoted=true;continue;}
            if(c==' '||c=='\t'||c=='\r'||c=='\n'||"{}[],:".indexOf(c)>=0)continue;
            if(c=='-'||(c>='0'&&c<='9')){
                int start=i;
                while(i+1<s.length()&&"0123456789+-.eE".indexOf(s.charAt(i+1))>=0)i++;
                String n=s.substring(start,i+1);
                if(n.length()>1024||!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw new IllegalArgumentException("JSON number");
                continue;
            }
            String literal=s.startsWith("true",i)?"true":s.startsWith("false",i)?"false":s.startsWith("null",i)?"null":null;
            if(literal==null)throw new IllegalArgumentException("JSON token");
            i+=literal.length()-1;
        }
        if(quoted)throw new IllegalArgumentException("Unterminated JSON string");
    }
    private static void text(String value){
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))throw new IllegalArgumentException("Invalid Unicode");
            }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid Unicode");
        }
    }
}
