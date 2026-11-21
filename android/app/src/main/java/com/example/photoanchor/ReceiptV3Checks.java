package com.example.photoanchor;
import java.util.Arrays;
public final class ReceiptV3Checks {
private ReceiptV3Checks(){}
    public static void validateAttachments(byte[] receipt,byte[] policy,byte[] report)throws Exception {
        Object[] fields=(Object[])ProtocolCbor.decode(receipt);
        if(fields.length!=12||!Long.valueOf(3).equals(fields[0]))fail();
        Object[] evidence=(Object[])fields[10];
        if(!Arrays.equals(ReceiptV1.hash(policy),(byte[])evidence[5])
            ||!Arrays.equals(ReceiptV1.hash(report),(byte[])evidence[6]))fail();
        org.json.JSONObject policyObject=StrictJson.parseObject(policy);
        if(!"fingerprinto/policy-v3/1".equals(policyObject.getString("schema")))fail();
        org.json.JSONObject obj=StrictJson.parseObject(report);
        if(!"fingerprinto/report-v3/1".equals(obj.getString("schema")))fail();
        String[] status={"unavailable","inconclusive","pass","fail"};
        String[] names={"manufacturer","prnu"};
        for(int i=0;i<2;i++) {
            org.json.JSONObject finding=obj.getJSONObject(names[i]);
            if(!status[((Long)evidence[2+i]).intValue()].equals(finding.getString("status"))
                ||finding.getString("reason").isEmpty())fail();
        }
        if(!AuthorityRecords.hex((byte[])evidence[5]).equals(obj.getString("policyHash")))fail();
        if(evidence[4]==null){if(!obj.has("referenceHash")||!obj.isNull("referenceHash"))fail();}
        else if(!AuthorityRecords.hex((byte[])evidence[4]).equals(obj.getString("referenceHash")))fail();
    }

private static void fail(){throw new IllegalArgumentException("Invalid v3 attachments");}
}
