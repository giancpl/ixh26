package com.example.photoanchor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Canonical reference/1 writer, independently readable by the Python verifier. */
final class PrnuReferenceRecord {
    private PrnuReferenceRecord() {}
    private static String text(String s) {
        if(s==null || s.isEmpty() || s.getBytes(StandardCharsets.UTF_8).length>255)
            throw new IllegalArgumentException("Invalid reference identifier");
        StringBuilder out=new StringBuilder("\"");
        for(int p=0;p<s.length();p++) {
            char c=s.charAt(p);
            switch(c) {
                case '"':out.append("\\\"");break;
                case '\\':out.append("\\\\");break;
                case '\b':out.append("\\b");break;
                case '\f':out.append("\\f");break;
                case '\n':out.append("\\n");break;
                case '\r':out.append("\\r");break;
                case '\t':out.append("\\t");break;
                default:
                    if(c<32)out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
                    else if(Character.isHighSurrogate(c)) {
                        if(p+1>=s.length() || !Character.isLowSurrogate(s.charAt(p+1)))
                            throw new IllegalArgumentException("Invalid Unicode");
                        out.append(c).append(s.charAt(++p));
                    } else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid Unicode");
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }
    static byte[] encode(int w,int h,double[] k,boolean[] m,List<String> frames,
            String camera,String mode,String decoder,Integer sw,Integer sh) {
        if((sw==null)!=(sh==null) || (sw!=null && (sw<w || sh<h || (long)sw*sh>32L*1024*1024)))
            throw new IllegalArgumentException("Invalid source geometry");
        ByteBuffer values=ByteBuffer.allocate(k.length*8).order(ByteOrder.LITTLE_ENDIAN);
        byte[] mask=new byte[m.length];
        for(int p=0;p<k.length;p++){values.putDouble(k[p]);mask[p]=(byte)(m[p]?1:0);}
        StringBuilder out=new StringBuilder("{\"frames\":[");
        for(int i=0;i<frames.size();i++){if(i>0)out.append(',');out.append(text(frames.get(i)));}
        out.append("],\"geometry\":{\"camera\":").append(text(camera))
            .append(",\"decoder\":").append(text(decoder)).append(",\"height\":").append(h)
            .append(",\"mode\":").append(text(mode)).append(",\"source_height\":").append(sh)
            .append(",\"source_width\":").append(sw).append(",\"width\":").append(w)
            .append("},\"mask\":\"").append(Base64.getEncoder().encodeToString(mask))
            .append("\",\"method\":\"").append(PrnuExperiment.METHOD)
            .append("\",\"pattern\":\"").append(Base64.getEncoder().encodeToString(values.array()))
            .append("\",\"schema\":\"fingerprinto/reference/1\"}");
        byte[] raw=out.toString().getBytes(StandardCharsets.UTF_8);
        if(raw.length>4*1024*1024)throw new IllegalArgumentException("Oversized reference record");
        return raw;
    }
}
