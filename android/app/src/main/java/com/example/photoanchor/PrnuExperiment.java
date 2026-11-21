package com.example.photoanchor;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;

/** Binary64 numeric trial on decoded green crops. No decoder or trust claims. */
public final class PrnuExperiment {
    public static final String METHOD = "fingerprinto/prnu-haar-green/1";
    private PrnuExperiment() {}
    private static void dimensions(int w, int h) {
        if (w < 16 || h < 16 || w > 512 || h > 512 || w % 2 != 0 || h % 2 != 0)
            throw new IllegalArgumentException("Unsupported crop");
    }
    private static double shrink(double x) { return Math.copySign(Math.max(Math.abs(x)-3, 0), x); }
    private static void values(double[] a, int w, int h) {
        dimensions(w, h);
        if (a.length != w*h) throw new IllegalArgumentException("Geometry mismatch");
        for (double x : a) if (!Double.isFinite(x) || x < 0 || x > 255)
            throw new IllegalArgumentException("Invalid intensity");
    }
    private static double[] center(double[] a, boolean[] m, int w, int h) {
        double[] out = a.clone();
        for (int y=0; y<h; y++) {
            double sum=0; int count=0;
            for (int x=0; x<w; x++) if (m[y*w+x]) { sum+=out[y*w+x]; count++; }
            double mean=count==0 ? 0 : sum/count;
            for (int x=0; x<w; x++) if (m[y*w+x]) out[y*w+x]-=mean;
        }
        for (int x=0; x<w; x++) {
            double sum=0; int count=0;
            for (int y=0; y<h; y++) if (m[y*w+x]) { sum+=out[y*w+x]; count++; }
            double mean=count==0 ? 0 : sum/count;
            for (int y=0; y<h; y++) if (m[y*w+x]) out[y*w+x]-=mean;
        }
        for (int p=0; p<out.length; p++) if (!m[p]) out[p]=0;
        return out;
    }
    private static boolean[] mask(double[] i) {
        boolean[] m=new boolean[i.length];
        for (int p=0; p<i.length; p++) m[p]=i[p]>=8 && i[p]<=247;
        return m;
    }
    public static double[] residual(double[] i, int w, int h) {
        values(i,w,h);
        double[] out=new double[i.length];
        for (int y=0;y<h;y+=2) for (int x=0;x<w;x+=2) {
            int p=y*w+x;
            double a=i[p],b=i[p+1],c=i[p+w],d=i[p+w+1];
            double ll=(a+b+c+d)/2,lh=shrink((a-b+c-d)/2),
                hl=shrink((a+b-c-d)/2),hh=shrink((a-b-c+d)/2);
            out[p]=a-(ll+lh+hl+hh)/2; out[p+1]=b-(ll-lh+hl-hh)/2;
            out[p+w]=c-(ll+lh-hl-hh)/2; out[p+w+1]=d-(ll-lh-hl+hh)/2;
        }
        return center(out,mask(i),w,h);
    }
    public static final class Estimator {
        private final int w,h;
        private final double[] numerator,denominator;
        private final int[] counts;
        private final Set<String> frames=new LinkedHashSet<>();
        public Estimator(int w,int h) {
            dimensions(w,h);this.w=w;this.h=h;
            numerator=new double[w*h];denominator=new double[w*h];counts=new int[w*h];
        }
        public void add(double[] i,String assetSha256) {
            if (assetSha256==null || !assetSha256.matches("[0-9a-f]{64}")
                    || frames.contains(assetSha256) || frames.size()>=10000)
                throw new IllegalArgumentException("Duplicate/invalid enrollment frame");
            double[] r=residual(i,w,h); boolean[] m=mask(i); int n=0;
            for (boolean v:m) if(v)n++;
            if ((double)n/m.length<0.75) throw new IllegalArgumentException("Low quality");
            for (int p=0;p<i.length;p++) if(m[p]) {
                numerator[p]+=i[p]*r[p];denominator[p]+=i[p]*i[p];counts[p]++;
            }
            frames.add(assetSha256);
        }
        public Reference finish() {
            if(frames.size()<2)throw new IllegalArgumentException("Insufficient frames");
            int minimum=Math.max(2,(int)Math.ceil(frames.size()*0.8)),n=0;
            boolean[] m=new boolean[w*h];double[] k=new double[w*h];
            for(int p=0;p<k.length;p++) {
                m[p]=counts[p]>=minimum && denominator[p]>0;
                if(m[p]){k[p]=numerator[p]/denominator[p];n++;}
            }
            if((double)n/k.length<0.75)throw new IllegalArgumentException("Insufficient support");
            return new Reference(w,h,center(k,m,w,h),m,frames);
        }
    }
    public static final class Reference {
        private final int w,h;
        private final double[] pattern;
        private final boolean[] mask;
        private final Set<String> frames;
        private final List<String> orderedFrames;
        private Reference(int w,int h,double[] k,boolean[] m,Set<String> frames) {
            this.w=w;this.h=h;pattern=k.clone();mask=m.clone();this.frames=Set.copyOf(frames);
            orderedFrames=List.copyOf(frames);
        }
        public double[] pattern() {return pattern.clone();}
        /** Exact-byte record for hashing/private persistence; no physical trust. */
        public byte[] record(String camera,String mode,String decoder,Integer sourceWidth,Integer sourceHeight) {
            return PrnuReferenceRecord.encode(w,h,pattern,mask,orderedFrames,camera,mode,decoder,sourceWidth,sourceHeight);
        }
    }
    public record Score(Double value,String reason,double validFraction,Double r0) {}
    public static Score score(double[] i,Reference ref,String assetSha256) {
        if(assetSha256==null || !assetSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid query digest");
        if(ref.frames.contains(assetSha256))return new Score(null,"query_used_for_enrollment",0,null);
        int w=ref.w,h=ref.h;
        double[] r=residual(i,w,h),pred=new double[i.length];boolean[] valid=mask(i);
        for(int p=0;p<i.length;p++)pred[p]=i[p]*ref.pattern[p];
        double lowest=1,r0=0,off=0;int offCount=0;
        for(int dy=-3;dy<=3;dy++)for(int dx=-3;dx<=3;dx++) {
            double sa=0,sb=0;int n=0;
            for(int y=3;y<h-3;y++)for(int x=3;x<w-3;x++) {
                int p=y*w+x,q=(y+dy)*w+x+dx;
                if(valid[p]&&valid[q]&&ref.mask[p]&&ref.mask[q]) {sa+=r[p];sb+=pred[q];n++;}
            }
            double fraction=(double)n/((h-6)*(w-6));lowest=Math.min(lowest,fraction);
            if(fraction<0.75)return new Score(null,"insufficient_valid_area",lowest,null);
            double ma=sa/n,mb=sb/n,cross=0,aa=0,bb=0;
            for(int y=3;y<h-3;y++)for(int x=3;x<w-3;x++) {
                int p=y*w+x,q=(y+dy)*w+x+dx;
                if(valid[p]&&valid[q]&&ref.mask[p]&&ref.mask[q]) {
                    double av=r[p]-ma,bv=pred[q]-mb;cross+=av*bv;aa+=av*av;bb+=bv*bv;
                }
            }
            if(aa<=0||bb<=0)return new Score(null,"zero_energy",lowest,null);
            double corr=cross/Math.sqrt(aa*bb);
            if(dx==0&&dy==0)r0=corr;
            if(Math.abs(dx)>1||Math.abs(dy)>1){off+=corr*corr;offCount++;}
        }
        double energy=off/offCount;
        if(energy<=0)return new Score(null,"zero_off_peak_energy",lowest,r0);
        double score=Math.copySign(r0*r0/energy,r0);
        if(!Double.isFinite(score))return new Score(null,"nonfinite_score",lowest,r0);
        return new Score(score,"experimental_score",lowest,r0);
    }
}
