package com.example.invoice.evaluation;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;

/** Corpus-level metrics: unknown denominators remain null; no confidence is accepted. */
public final class EvaluationMetrics {
    private EvaluationMetrics() {}
    public record FieldScore(long expected, long correct, long missing, long incorrect, Double accuracy,
            Double missingRate, Double incorrectRate) {}
    public record ClassScore(long support, long predicted, long truePositive, Double precision,
            Double recall, Double f1) {}
    public record Score(int samples, int succeeded, int failed, Double successRate, Double failureRate,
            Double averageProcessingMs, int timedSamples, Double visionFallbackRate, int fallbackObserved,
            Double retryRate, int attemptsObserved, Double cer, long characterEdits, long referenceCharacters,
            int ocrSamples, Double wer, long wordEdits, long referenceWords,
            Map<String, FieldScore> fields, Map<String, FieldScore> ocrFields,
            FieldScore itemCells, long extraItemRows, long extraFields, Double classificationAccuracy,
            int classificationSamples, Map<String, ClassScore> classes, Double macroPrecision,
            Double macroRecall, Double macroF1, Map<String, Map<String, Long>> confusionMatrix,
            Map<String, Long> failuresByCode) {}

    public static Score calculate(List<EvaluationData.Sample> samples, List<EvaluationData.Result> results) {
        Map<String, EvaluationData.Result> byId = new HashMap<>();
        results.forEach(r -> byId.put(r.sampleId(), r));
        var fieldCounts = new TreeMap<String, long[]>();
        var ocrFieldCounts = new TreeMap<String, long[]>();
        var confusion = new TreeMap<String, Map<String, Long>>();
        var failures = new TreeMap<String, Long>();
        long chars=0, charEdits=0, words=0, wordEdits=0, itemExpected=0, itemCorrect=0, itemMissing=0,
                itemIncorrect=0, extraRows=0, extraFields=0, totalMs=0;
        int succeeded=0, failed=0, timed=0, fallback=0, fallbackObserved=0, retried=0, attemptsObserved=0,
                ocrSamples=0, classSamples=0, classCorrect=0;
        for (var sample : samples) {
            var result = byId.get(sample.id());
            if (result == null) continue; // Service reports coverage; incomplete runs cannot masquerade as full runs.
            if ("SUCCESS".equals(result.outcome())) succeeded++;
            else { failed++; failures.merge(result.failureCode(), 1L, Long::sum); }
            if (result.durationMs()!=null) { timed++; totalMs+=result.durationMs(); }
            if (result.visionFallback()!=null) { fallbackObserved++; if(result.visionFallback()) fallback++; }
            if (result.attemptCount()!=null) { attemptsObserved++; if(result.attemptCount()>1) retried++; }
            if (sample.ocrText()!=null) {
                String reference=normalize(sample.ocrText());
                String actual=normalize(result.ocrText());
                var referenceChars=reference.codePoints().boxed().toList();
                var actualChars=actual.codePoints().boxed().toList();
                chars+=referenceChars.size(); charEdits+=distance(referenceChars, actualChars);
                var referenceWords=tokens(reference); var actualWords=tokens(actual);
                words+=referenceWords.size(); wordEdits+=distance(referenceWords, actualWords); ocrSamples++;
            }
            countFields(sample.fields(), result.fields(), fieldCounts);
            countFields(sample.ocrFields(), result.ocrFields(), ocrFieldCounts);
            if(sample.fields()!=null && result.fields()!=null)
                extraFields+=result.fields().keySet().stream().filter(k->!sample.fields().containsKey(k)).count();
            if(sample.items()!=null) {
                var actual=result.items()==null ? List.<Map<String,String>>of() : result.items();
                extraRows+=Math.max(0,actual.size()-sample.items().size());
                for(int i=0;i<sample.items().size();i++) for(var cell:sample.items().get(i).entrySet()) {
                    itemExpected++;
                    String value=i<actual.size()?actual.get(i).get(cell.getKey()):null;
                    if(value==null||value.isBlank()) itemMissing++;
                    else if(normalize(cell.getValue()).equals(normalize(value))) itemCorrect++;
                    else itemIncorrect++;
                }
            }
            if(sample.documentType()!=null) {
                classSamples++;
                String predicted=result.predictedType()==null||result.predictedType().isBlank()
                        ? "__NO_PREDICTION__" : result.predictedType();
                if(sample.documentType().equals(predicted)) classCorrect++;
                confusion.computeIfAbsent(sample.documentType(),k->new TreeMap<>()).merge(predicted,1L,Long::sum);
            }
        }
        Set<String> labels=new TreeSet<>(confusion.keySet());
        confusion.values().forEach(row->labels.addAll(row.keySet())); labels.remove("__NO_PREDICTION__");
        var classes=new TreeMap<String,ClassScore>();
        for(String label:labels) {
            long support=confusion.getOrDefault(label,Map.of()).values().stream().mapToLong(Long::longValue).sum();
            long predicted=confusion.values().stream().mapToLong(row->row.getOrDefault(label,0L)).sum();
            long tp=confusion.getOrDefault(label,Map.of()).getOrDefault(label,0L);
            // Zero division for individual labels is defined as zero, never an invented successful score.
            double p=predicted==0?0:(double)tp/predicted, r=support==0?0:(double)tp/support;
            classes.put(label,new ClassScore(support,predicted,tp,p,r,p+r==0?0:2*p*r/(p+r)));
        }
        return new Score(results.size(),succeeded,failed,ratio(succeeded,results.size()),ratio(failed,results.size()),
                ratio(totalMs,timed),timed,ratio(fallback,fallbackObserved),fallbackObserved,ratio(retried,attemptsObserved),
                attemptsObserved,ratio(charEdits,chars),charEdits,chars,ocrSamples,ratio(wordEdits,words),wordEdits,words,
                scores(fieldCounts),scores(ocrFieldCounts),field(itemExpected,itemCorrect,itemMissing,itemIncorrect),
                extraRows,extraFields,ratio(classCorrect,classSamples),classSamples,classes,
                average(classes,ClassScore::precision),average(classes,ClassScore::recall),average(classes,ClassScore::f1),
                confusion,failures);
    }
    static String normalize(String value) {
        return value==null?"":Normalizer.normalize(value,Normalizer.Form.NFC).strip().replaceAll("\\s+"," ");
    }
    private static List<String> tokens(String s) { return s.isEmpty()?List.of():List.of(s.split(" ")); }
    static <T> int distance(List<T> a,List<T> b) {
        // Two rows, O(min(m,n)) memory; preserves Vietnamese code points, punctuation and case.
        if(a.size()<b.size())return distance(b,a);
        int[] previous=new int[b.size()+1]; for(int j=0;j<=b.size();j++)previous[j]=j;
        for(int i=1;i<=a.size();i++) { int[] current=new int[b.size()+1]; current[0]=i;
            for(int j=1;j<=b.size();j++)current[j]=Math.min(Math.min(previous[j]+1,current[j-1]+1),
                    previous[j-1]+(Objects.equals(a.get(i-1),b.get(j-1))?0:1)); previous=current; }
        return previous[b.size()];
    }
    private static void countFields(Map<String,String> reference,Map<String,String> actual,Map<String,long[]> counts) {
        if(reference==null)return;
        for(var entry:reference.entrySet()) {
            var count=counts.computeIfAbsent(entry.getKey(),k->new long[4]); count[0]++;
            String value=actual==null?null:actual.get(entry.getKey());
            if(value==null||value.isBlank())count[2]++;
            else if(normalize(entry.getValue()).equals(normalize(value)))count[1]++; else count[3]++;
        }
    }
    private static Map<String,FieldScore> scores(Map<String,long[]> counts) {
        var scores=new TreeMap<String,FieldScore>(); counts.forEach((key,c)->scores.put(key,field(c[0],c[1],c[2],c[3])));return scores;
    }
    private static FieldScore field(long n,long correct,long missing,long incorrect) {
        return new FieldScore(n,correct,missing,incorrect,ratio(correct,n),ratio(missing,n),ratio(incorrect,n));
    }
    private static Double ratio(long n,long d) { return d==0?null:(double)n/d; }
    private static Double average(Map<String,ClassScore> scores,Function<ClassScore,Double> f) {
        return scores.isEmpty()?null:scores.values().stream().mapToDouble(f::apply).average().orElseThrow();
    }
}
