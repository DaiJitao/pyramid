package edu.neu.ccs.pyramid.experiment;

import edu.neu.ccs.pyramid.configuration.Config;
import edu.neu.ccs.pyramid.dataset.DataSetType;
import edu.neu.ccs.pyramid.dataset.MultiLabel;
import edu.neu.ccs.pyramid.dataset.MultiLabelClfDataSet;
import edu.neu.ccs.pyramid.dataset.TRECFormat;
import edu.neu.ccs.pyramid.eval.Accuracy;
import edu.neu.ccs.pyramid.eval.Overlap;
import edu.neu.ccs.pyramid.multilabel_classification.bmm_variant.BMMClassifier;
import edu.neu.ccs.pyramid.multilabel_classification.bmm_variant.BMMInitializer;
import edu.neu.ccs.pyramid.multilabel_classification.bmm_variant.BMMOptimizer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;


/**
 * Created by Rainicy on 10/24/15.
 */
public class Exp211 {

    public static BMMClassifier loadBMM(Config config, MultiLabelClfDataSet trainSet, MultiLabelClfDataSet testSet) throws Exception{
        int numClusters = config.getInt("numClusters");
        double softmaxVariance = config.getDouble("softmaxVariance");
        double logitVariance = config.getDouble("logitVariance");
        int numSamples = config.getInt("numSamples");

        String output = config.getString("output");
        String modelName = config.getString("modelName");


        BMMClassifier bmmClassifier;
        if (config.getBoolean("train.warmStart")) {
            bmmClassifier = BMMClassifier.deserialize(new File(output, modelName));
            bmmClassifier.setAllowEmpty(config.getBoolean("allowEmpty"));
            bmmClassifier.setPredictMode(config.getString("predictMode"));
        } else {
            bmmClassifier = BMMClassifier.getBuilder()
                    .setNumClasses(trainSet.getNumClasses())
                    .setNumFeatures(trainSet.getNumFeatures())
                    .setNumClusters(numClusters)
                    .setBinaryClassifierType("lr")
                    .setMultiClassClassifierType("lr")
                    .build();

            bmmClassifier.setNumSample(numSamples);
            bmmClassifier.setAllowEmpty(config.getBoolean("allowEmpty"));
            bmmClassifier.setPredictMode(config.getString("predictMode"));

            MultiLabel[] trainPredict;
            MultiLabel[] testPredict;

//            trainPredict = bmmClassifier.predict(trainSet);
//            testPredict = bmmClassifier.predict(testSet);
//            System.out.print("random init");
//            System.out.print("objective: "+optimizer.getObjective()+ "\t");
//            System.out.print("trainAcc : "+ Accuracy.accuracy(trainSet.getMultiLabels(), trainPredict) + "\t");
//            System.out.print("trainOver: "+ Overlap.overlap(trainSet.getMultiLabels(), trainPredict) + "\t");
//            System.out.print("testACC  : "+ Accuracy.accuracy(testSet.getMultiLabels(),testPredict) + "\t");
//            System.out.println("testOver : "+ Overlap.overlap(testSet.getMultiLabels(), testPredict) + "\t");

            if (config.getBoolean("initialize")) {
                System.out.println("after initialization");
                BMMInitializer.initialize(bmmClassifier, trainSet, softmaxVariance, logitVariance);
            }
            trainPredict = bmmClassifier.predict(trainSet);
            testPredict = bmmClassifier.predict(testSet);

            System.out.print("trainAcc : " + Accuracy.accuracy(trainSet.getMultiLabels(), trainPredict) + "\t");
            System.out.print("trainOver: " + Overlap.overlap(trainSet.getMultiLabels(), trainPredict) + "\t");
            System.out.print("testACC  : " + Accuracy.accuracy(testSet.getMultiLabels(), testPredict) + "\t");
            System.out.println("testOver : " + Overlap.overlap(testSet.getMultiLabels(), testPredict) + "\t");

        }

        return bmmClassifier;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Please specify a properties file.");
        }

        Config config = new Config(args[0]);

        System.out.println(config);

        MultiLabelClfDataSet trainSet = TRECFormat.loadMultiLabelClfDataSet(config.getString("input.trainData"),
                DataSetType.ML_CLF_SEQ_SPARSE, true);
        MultiLabelClfDataSet testSet = TRECFormat.loadMultiLabelClfDataSet(config.getString("input.testData"),
                DataSetType.ML_CLF_SEQ_SPARSE, true);

        double softmaxVariance = config.getDouble("softmaxVariance");
        double logitVariance = config.getDouble("logitVariance");
        int numIterations = config.getInt("numIterations");


        String output = config.getString("output");
        String modelName = config.getString("modelName");

        BMMClassifier bmmClassifier = loadBMM(config,trainSet,testSet);

        BMMOptimizer optimizer = new BMMOptimizer(bmmClassifier, trainSet, softmaxVariance, logitVariance);
        optimizer.setMeanRegularization(config.getBoolean("meanRegularization"));
        optimizer.setInverseTemperature(config.getDouble("inverseTemperature"));
        optimizer.setMeanRegVariance(config.getDouble("meanRegVariance"));


        for (int i=1;i<=numIterations;i++){
            System.out.print("iter : "+i + "\t");
            optimizer.iterate();
            MultiLabel[] trainPredict;
            MultiLabel[] testPredict;
            trainPredict = bmmClassifier.predict(trainSet);
            testPredict = bmmClassifier.predict(testSet);
            System.out.print("objective: "+optimizer.getTerminator().getLastValue() + "\t");
            System.out.print("trainAcc : "+ Accuracy.accuracy(trainSet.getMultiLabels(),trainPredict)+ "\t");
            System.out.print("trainOver: "+ Overlap.overlap(trainSet.getMultiLabels(), trainPredict)+ "\t");
            System.out.print("testAcc  : "+ Accuracy.accuracy(testSet.getMultiLabels(),testPredict)+ "\t");
            System.out.println("testOver : "+ Overlap.overlap(testSet.getMultiLabels(), testPredict)+ "\t");
            if (config.getBoolean("saveModelForEachIter")) {
                String path = output + "/" + modelName;
                (new File(path)).mkdirs();
                File serializeModel = new File(path,  "iter." + i + ".model");
                bmmClassifier.serialize(serializeModel);
                double[][] gammas = optimizer.getGammas();
                double[][] PIs = optimizer.getPIs();
                BufferedWriter bw = new BufferedWriter(new FileWriter(new File(path, "iter."+i+".gammas")));
                BufferedWriter bw1 = new BufferedWriter(new FileWriter(new File(path, "iter."+i+".PIs")));
                for (int n=0; n<gammas.length; n++) {
                    for (int k=0; k<gammas[n].length; k++) {
                        bw.write(gammas[n][k] + "\t");
                        bw1.write(PIs[n][k] + "\t");
                    }
                    bw.write("\n");
                    bw1.write("\n");
                }
                bw.close();
                bw1.close();
            }
        }
        System.out.println("history = "+optimizer.getTerminator().getHistory());


        System.out.println("--------------------------------Results-----------------------------\n");
        System.out.println();
        System.out.print("trainAcc : " + Accuracy.accuracy(bmmClassifier, trainSet) + "\t");
        System.out.print("trainOver: "+ Overlap.overlap(bmmClassifier, trainSet)+ "\t");
        System.out.print("testAcc  : "+ Accuracy.accuracy(bmmClassifier,testSet)+ "\t");
        System.out.println("testOver : "+ Overlap.overlap(bmmClassifier, testSet)+ "\t");
        System.out.println();
        System.out.println();
//        System.out.println(bmmClassifier);

        if (config.getBoolean("saveModel")) {
            (new File(output+"/"+modelName)).mkdirs();
            File serializeModel = new File(output+"/"+modelName, "model");
            bmmClassifier.serialize(serializeModel);
        }
    }
}
