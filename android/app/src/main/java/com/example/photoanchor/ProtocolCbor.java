package com.example.photoanchor;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Restricted deterministic CBOR parser. No maps, floats, tags or booleans. */
final class ProtocolCbor {
    private final ByteBuffer input;
    private ProtocolCbor(byte[] raw) {
        if(raw==null || raw.length>262144)throw new IllegalArgumentException("Oversized receipt");
        input=ByteBuffer.wrap(raw);
    }
    static Object decode(byte[] raw) {
        ProtocolCbor r=new ProtocolCbor(raw);
        Object result=r.item(0);
        if(r.input.hasRemaining())throw new IllegalArgumentException("Trailing CBOR");
        return result;
    }
    private int octet() {
        if(!input.hasRemaining())throw new IllegalArgumentException("Truncated CBOR");
        return input.get()&255;
    }
    private Object item(int depth) {
        if(depth>2)throw new IllegalArgumentException("Excessive CBOR nesting");
        int first=octet();
        if(first==246)return null;
        int major=first>>5,info=first&31;
        if(major>4 || info>27)throw new IllegalArgumentException("Unsupported CBOR");
        long n=info;
        if(info>=24) {
            int count=1<<(info-24);n=0;
            for(int i=0;i<count;i++)n=(n<<8)|octet();
            long minimum=count==1 ? 24 : 1L<<(8*(count/2));
            if(n<0 || n<minimum)throw new IllegalArgumentException("Nonminimal/oversized CBOR integer");
        }
        if(major==0)return n;
        if(major==1)return -1-n;
        if(n>input.remaining())throw new IllegalArgumentException("Truncated CBOR field");
        int size=(int)n;
        if(major==4) {
            Object[] items=new Object[size];
            for(int i=0;i<size;i++)items[i]=item(depth+1);
            return items;
        }
        byte[] raw=new byte[size];input.get(raw);
        if(major==2)return raw;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        } catch(java.nio.charset.CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid CBOR UTF-8",e);
        }
    }
}
