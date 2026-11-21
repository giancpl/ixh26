package com.example.photoanchor;

import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** The authority SPKI must be pinned outside the HTTP response/bundle. */
public final class AuthorityRecords {
    private AuthorityRecords(){}
    public static JSONObject verify(byte[] envelope,byte[] authority,String domain)throws Exception {
        JSONObject e=StrictJson.parseObject(envelope);
        if(e.length()!=2)throw new IllegalArgumentException("Authority envelope");
        byte[] body=e.getString("body").getBytes(StandardCharsets.UTF_8);
        byte[] root=ReceiptV1.protocolHash(domain,ReceiptV1.hash(body));
        InstallationSignature.verify(authority,root,unhex(e.getString("signature")));
        return StrictJson.parseObject(body);
    }
    public static String hex(byte[] value){StringBuilder s=new StringBuilder();for(byte b:value)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    public static byte[] unhex(String text){
        if(text==null||text.length()%2!=0||!text.matches("[0-9a-f]+"))throw new IllegalArgumentException("Hex");
        byte[] out=new byte[text.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return out;
    }
}
