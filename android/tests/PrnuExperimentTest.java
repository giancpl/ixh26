package com.example.photoanchor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PrnuExperimentTest {
    static double[] a(String s) {
        String[] parts=s.split(",");double[] result=new double[parts.length];
        for(int i=0;i<parts.length;i++) result[i]=Double.parseDouble(parts[i]);
        return result;
    }
    static void close(double a,double b) {
        if(Math.abs(a-b)>1e-10*Math.max(1,Math.abs(a)))throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args) throws Exception {
        List<String> lines=Files.readAllLines(Path.of(args[0]));
        PrnuExperiment.Estimator e=new PrnuExperiment.Estimator(32,32);
        PrnuExperiment.Reference ref=null; int checks=0;
        for(String line:lines) {
            String[] x=line.split("\t");
            switch(x[0]) {
                case "f": e.add(a(x[2]),x[1]);break;
                case "k": ref=e.finish();double[] expected=a(x[1]),got=ref.pattern();
                    for(int i=0;i<got.length;i++){close(expected[i],got[i]);checks++;}break;
                case "q":
                    PrnuExperiment.Score score=PrnuExperiment.score(a(x[3]),ref,x[1]);
                    if(!score.reason().equals(x[2]))throw new AssertionError(score.reason());
                    if(!x[4].equals("-"))close(Double.parseDouble(x[4]),score.value());
                    else if(score.value()!=null)throw new AssertionError("expected no score");
                    checks++;break;
                default:throw new AssertionError("unknown row");
            }
        }
        System.out.println("PRNU: "+checks+" Python/Java numeric comparisons passed (experimental only)");
    }
}
