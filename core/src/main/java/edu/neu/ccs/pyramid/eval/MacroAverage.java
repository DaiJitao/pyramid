package edu.neu.ccs.pyramid.eval;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.neu.ccs.pyramid.dataset.DataSet;
import edu.neu.ccs.pyramid.dataset.LabelTranslator;
import edu.neu.ccs.pyramid.dataset.MultiLabelClfDataSet;
import edu.neu.ccs.pyramid.multilabel_classification.MultiLabelClassifier;
import edu.neu.ccs.pyramid.util.MathUtil;
import edu.neu.ccs.pyramid.util.PrintUtil;
import org.apache.mahout.math.Vector;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.IntStream;
/**
 * Based on
 * Koyejo, Oluwasanmi O., et al. "Consistent Multilabel Classification."
 * Advances in Neural Information Processing Systems. 2015.
 * convention: 0=TN, 1=TP, 2=FN, 3=FP
 * Created by chengli on 3/24/16.
 */
@JsonSerialize(using = MacroAverage.Serializer.class)
public class MacroAverage implements Serializable {
    private static final long serialVersionUID = 2L;
    private int numClasses;
    private double f1;
    private double overlap;
    private double precision;
    private double recall;
    private double hammingLoss;
    private double binaryAccuracy;
    private double averagePrecision;
    // per class counts
    private int[] labelWiseTP;
    private int[] labelWiseTN;
    private int[] labelWiseFP;
    private int[] labelWiseFN;
    // per class measures
    private double[] labelWisePrecision;
    private double[] labelWiseRecall;
    private double[] labelWiseOverlap;
    private double[] labelWiseF1;
    private double[] labelWiseHammingLoss;
    private double[] labelWiseAccuracy;
    private double[] labelWiseAveragePrecision;

    private LabelTranslator labelTranslator;


    public MacroAverage(MLConfusionMatrix confusionMatrix) {
        this.numClasses = confusionMatrix.getNumClasses();
        int numDataPoints = confusionMatrix.getNumDataPoints();
        DataSet entries = confusionMatrix.getEntries();
        this.labelWiseTP = new int[numClasses];
        this.labelWiseTN = new int[numClasses];
        this.labelWiseFP = new int[numClasses];
        this.labelWiseFN = new int[numClasses];

        this.labelWisePrecision = new double[numClasses];
        this.labelWiseRecall = new double[numClasses];
        this.labelWiseOverlap = new double[numClasses];
        this.labelWiseF1 = new double[numClasses];
        this.labelWiseHammingLoss = new double[numClasses];
        this.labelWiseAccuracy = new double[numClasses];
        this.labelWiseAveragePrecision = new double[numClasses];


        IntStream.range(0,numClasses).parallel().forEach(l->{
            Vector vector = entries.getColumn(l);
            for (Vector.Element element: vector.nonZeroes()){
                double v = element.get();
                if (v==1){
                    labelWiseTP[l] += 1;
                } else if (v==2){
                    labelWiseFN[l] += 1;
                } else if (v==3){
                    labelWiseFP[l] += 1;
                }
            }
            labelWiseTN[l] = numDataPoints - vector.getNumNonZeroElements();

            double tp = ((double) labelWiseTP[l])/numDataPoints;
            double tn = ((double) labelWiseTN[l])/numDataPoints;
            double fp = ((double) labelWiseFP[l])/numDataPoints;
            double fn = ((double) labelWiseFN[l])/numDataPoints;

            labelWisePrecision[l] = Precision.precision(tp,fp);
            labelWiseRecall[l] = Recall.recall(tp,fn);
            labelWiseF1[l] = FMeasure.f1(tp,fp,fn);
            labelWiseOverlap[l] = Overlap.overlap(tp,fp,fn);
            labelWiseHammingLoss[l] = HammingLoss.hammingLoss(tp,tn,
                    fp,fn);
            labelWiseAccuracy[l] = tp+tn;
        });

        precision = Arrays.stream(labelWisePrecision).average().getAsDouble();

        recall = Arrays.stream(labelWiseRecall).average().getAsDouble();

        f1 = Arrays.stream(labelWiseF1).average().getAsDouble();

        overlap = Arrays.stream(labelWiseOverlap).average().getAsDouble();

        hammingLoss = Arrays.stream(labelWiseHammingLoss).average().getAsDouble();

        binaryAccuracy = Arrays.stream(labelWiseAccuracy).average().getAsDouble();

        this.labelTranslator = LabelTranslator.newDefaultLabelTranslator(numClasses);
    }

    public void updateAveragePrecision(MultiLabelClassifier.ClassProbEstimator classifier, MultiLabelClfDataSet dataSet){
        this.labelWiseAveragePrecision = new double[dataSet.getNumClasses()];
        IntStream.range(0,dataSet.getNumClasses()).forEach(k->labelWiseAveragePrecision[k]=AveragePrecision.labelAveragePrecision(classifier,dataSet,k));
        this.averagePrecision = MathUtil.arraySum(labelWiseAveragePrecision)/labelWiseAveragePrecision.length;
    }

    public void setLabelTranslator(LabelTranslator labelTranslator) {
        this.labelTranslator = labelTranslator;
    }

    public double getF1() {
        return f1;
    }

    public double getOverlap() {
        return overlap;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getHammingLoss() {
        return hammingLoss;
    }

    public int[] getLabelWiseTP() {
        return labelWiseTP;
    }

    public int[] getLabelWiseTN() {
        return labelWiseTN;
    }

    public int[] getLabelWiseFP() {
        return labelWiseFP;
    }

    public int[] getLabelWiseFN() {
        return labelWiseFN;
    }

    public double[] getLabelWisePrecision() {
        return labelWisePrecision;
    }

    public double[] getLabelWiseRecall() {
        return labelWiseRecall;
    }

    public double[] getLabelWiseOverlap() {
        return labelWiseOverlap;
    }

    public double[] getLabelWiseF1() {
        return labelWiseF1;
    }

    public double[] getLabelWiseHammingLoss() {
        return labelWiseHammingLoss;
    }

    public double getBinaryAccuracy() {
        return binaryAccuracy;
    }

    public double[] getLabelWiseAccuracy() {
        return labelWiseAccuracy;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("label Jaccard Index = ").append(overlap).append("\n");
        sb.append("label Hamming loss = ").append(hammingLoss).append("\n");
        sb.append("label F1 = ").append(f1).append("\n");
        sb.append("label precision = ").append(precision).append("\n");
        sb.append("label recall = ").append(recall).append("\n");
        sb.append("label binary accuracy = ").append(binaryAccuracy).append("\n");

        return sb.toString();
    }



    public String printDetail() {
        final StringBuilder sb = new StringBuilder();
        sb.append("f1=").append(f1);
        sb.append(", overlap=").append(overlap);
        sb.append(", precision=").append(precision);
        sb.append(", recall=").append(recall);
        sb.append(", hammingLoss=").append(hammingLoss);
        sb.append(", binaryAccuracy=").append(binaryAccuracy);
        sb.append(", labelWisePrecision=").append(PrintUtil.printWithIndex(labelWisePrecision));
        sb.append(", labelWiseRecall=").append(PrintUtil.printWithIndex(labelWiseRecall));
        sb.append(", labelWiseOverlap=").append(PrintUtil.printWithIndex(labelWiseOverlap));
        sb.append(", labelWiseF1=").append(PrintUtil.printWithIndex(labelWiseF1));
        sb.append(", labelWiseHammingLoss=").append(PrintUtil.printWithIndex(labelWiseHammingLoss));
        sb.append(", labelWiseAccuracy=").append(PrintUtil.printWithIndex(labelWiseAccuracy));
        sb.append(", labelWiseTP=").append(PrintUtil.printWithIndex(labelWiseTP));
        sb.append(", labelWiseTN=").append(PrintUtil.printWithIndex(labelWiseTN));
        sb.append(", labelWiseFP=").append(PrintUtil.printWithIndex(labelWiseFP));
        sb.append(", labelWiseFN=").append(PrintUtil.printWithIndex(labelWiseFN));
        return sb.toString();
    }

    public static class Serializer extends JsonSerializer<MacroAverage> {
        @Override
        public void serialize(MacroAverage macroAverage, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {

            jsonGenerator.writeStartArray();
            for (int k=0;k<macroAverage.numClasses;k++){
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("label", macroAverage.labelTranslator.toExtLabel(k));
                jsonGenerator.writeNumberField("TP",macroAverage.labelWiseTP[k]);
                jsonGenerator.writeNumberField("TN",macroAverage.labelWiseTN[k]);
                jsonGenerator.writeNumberField("FP",macroAverage.labelWiseFP[k]);
                jsonGenerator.writeNumberField("FN",macroAverage.labelWiseFN[k]);
                jsonGenerator.writeNumberField("precision",macroAverage.labelWisePrecision[k]);
                jsonGenerator.writeNumberField("recall",macroAverage.labelWiseRecall[k]);
                jsonGenerator.writeNumberField("f1",macroAverage.labelWiseF1[k]);
                jsonGenerator.writeNumberField("accuracy",macroAverage.labelWiseAccuracy[k]);
                if (macroAverage.labelWiseAveragePrecision!=null){
                    jsonGenerator.writeNumberField("average precision",macroAverage.labelWiseAveragePrecision[k]);
                }
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
        }
    }
}
