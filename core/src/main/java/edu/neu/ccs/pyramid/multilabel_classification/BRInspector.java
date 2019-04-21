package edu.neu.ccs.pyramid.multilabel_classification;

import edu.neu.ccs.pyramid.calibration.*;
import edu.neu.ccs.pyramid.classification.PriorProbClassifier;
import edu.neu.ccs.pyramid.classification.logistic_regression.LogisticRegression;
import edu.neu.ccs.pyramid.dataset.IdTranslator;
import edu.neu.ccs.pyramid.dataset.LabelTranslator;
import edu.neu.ccs.pyramid.dataset.MultiLabel;
import edu.neu.ccs.pyramid.dataset.MultiLabelClfDataSet;
import edu.neu.ccs.pyramid.feature.Feature;
import edu.neu.ccs.pyramid.multilabel_classification.cbm.CBM;
import edu.neu.ccs.pyramid.multilabel_classification.imlgb.IMLGradientBoosting;
import edu.neu.ccs.pyramid.multilabel_classification.predictor.SupportPredictor;
import edu.neu.ccs.pyramid.regression.*;
import edu.neu.ccs.pyramid.regression.regression_tree.RegressionTree;
import edu.neu.ccs.pyramid.regression.regression_tree.TreeRule;
import edu.neu.ccs.pyramid.util.Pair;
import org.apache.mahout.math.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BRInspector {

    public static  MultiLabelPredictionAnalysis analyzePrediction(MultiLabelClassifier.ClassProbEstimator classProbEstimator,
                                                                  LabelCalibrator labelCalibrator,
                                                                  VectorCalibrator setCalibrator,
                                                                  MultiLabelClfDataSet dataSet,
                                                                  MultiLabelClassifier classifier,
                                                                  PredictionFeatureExtractor predictionFeatureExtractor,
                                                                  int dataPointIndex, int ruleLimit,
                                                                  int labelSetLimit, double classProbThreshold){
        MultiLabelPredictionAnalysis predictionAnalysis = new MultiLabelPredictionAnalysis();
        LabelTranslator labelTranslator = dataSet.getLabelTranslator();
        IdTranslator idTranslator = dataSet.getIdTranslator();
        predictionAnalysis.setInternalId(dataPointIndex);
        predictionAnalysis.setId(idTranslator.toExtId(dataPointIndex));
        predictionAnalysis.setInternalLabels(dataSet.getMultiLabels()[dataPointIndex].getMatchedLabelsOrdered());
        List<String> labels = dataSet.getMultiLabels()[dataPointIndex].getMatchedLabelsOrdered().stream()
                .map(labelTranslator::toExtLabel).collect(Collectors.toList());
        predictionAnalysis.setLabels(labels);

        double[] classProbs = classProbEstimator.predictClassProbs(dataSet.getRow(dataPointIndex));
        double[] calibratedClassProbs = labelCalibrator.calibratedClassProbs(classProbs);

        PredictionCandidate trueCandidate = new PredictionCandidate();
        trueCandidate.x = dataSet.getRow(dataPointIndex);
        trueCandidate.multiLabel = dataSet.getMultiLabels()[dataPointIndex];
        trueCandidate.labelProbs = calibratedClassProbs;
        predictionAnalysis.setProbForTrueLabels(setCalibrator.calibrate(predictionFeatureExtractor.extractFeatures(trueCandidate)));

        MultiLabel predictedLabels = classifier.predict(dataSet.getRow(dataPointIndex));
        List<Integer> internalPrediction = predictedLabels.getMatchedLabelsOrdered();
        predictionAnalysis.setInternalPrediction(internalPrediction);
        List<String> prediction = internalPrediction.stream().map(labelTranslator::toExtLabel).collect(Collectors.toList());
        predictionAnalysis.setPrediction(prediction);

        PredictionCandidate predictedCandidate = new PredictionCandidate();
        predictedCandidate.x = dataSet.getRow(dataPointIndex);
        predictedCandidate.multiLabel = predictedLabels;
        predictedCandidate.labelProbs = calibratedClassProbs;

        predictionAnalysis.setProbForPredictedLabels(setCalibrator.calibrate(predictionFeatureExtractor.extractFeatures(predictedCandidate)));

        List<Integer> classes = new ArrayList<Integer>();
        for (int k = 0; k < classProbEstimator.getNumClasses(); k++){
            if (calibratedClassProbs[k]>=classProbThreshold
                    ||dataSet.getMultiLabels()[dataPointIndex].matchClass(k)
                    ||predictedLabels.matchClass(k)){
                classes.add(k);
            }
        }

        //todo
        List<ClassScoreCalculation> classScoreCalculations = new ArrayList<>();
        for (int k: classes){
            ClassScoreCalculation classScoreCalculation = null;
            if (classProbEstimator instanceof IMLGradientBoosting){
                classScoreCalculation = decisionProcess((IMLGradientBoosting) classProbEstimator,labelTranslator,calibratedClassProbs[k],
                        dataSet.getRow(dataPointIndex),k,ruleLimit);
            }

            if (classProbEstimator instanceof CBM){
                classScoreCalculation = decisionProcess((CBM) classProbEstimator,labelTranslator,calibratedClassProbs[k],
                        dataSet.getRow(dataPointIndex),k,ruleLimit);
            }

            classScoreCalculations.add(classScoreCalculation);
        }
        predictionAnalysis.setClassScoreCalculations(classScoreCalculations);

        List<MultiLabelPredictionAnalysis.ClassRankInfo> labelRanking = classes.stream().map(label -> {
                    MultiLabelPredictionAnalysis.ClassRankInfo rankInfo = new MultiLabelPredictionAnalysis.ClassRankInfo();
                    rankInfo.setClassIndex(label);
                    rankInfo.setClassName(labelTranslator.toExtLabel(label));
                    rankInfo.setProb(calibratedClassProbs[label]);
                    return rankInfo;
                }
        ).collect(Collectors.toList());
        predictionAnalysis.setPredictedRanking(labelRanking);

        List<Pair<MultiLabel,Double>> topK;
        if (classifier instanceof SupportPredictor){
            topK = TopKFinder.topKinSupport(dataSet.getRow(dataPointIndex),classProbEstimator,labelCalibrator,setCalibrator,
                    predictionFeatureExtractor,((SupportPredictor)classifier).getSupport(),labelSetLimit);
        } else {
            topK = TopKFinder.topK(dataSet.getRow(dataPointIndex),classProbEstimator,labelCalibrator,setCalibrator,
                    predictionFeatureExtractor,labelSetLimit);
        }

        List<MultiLabelPredictionAnalysis.LabelSetProbInfo> labelSetRanking = topK.stream()
                .map(pair -> {
                    MultiLabel multiLabel = pair.getFirst();
                    double setProb = pair.getSecond();
                    MultiLabelPredictionAnalysis.LabelSetProbInfo labelSetProbInfo = new MultiLabelPredictionAnalysis.LabelSetProbInfo(multiLabel, setProb, labelTranslator);
                    return labelSetProbInfo;
                }).sorted(Comparator.comparing(MultiLabelPredictionAnalysis.LabelSetProbInfo::getProbability).reversed())
                .limit(labelSetLimit)
                .collect(Collectors.toList());

        predictionAnalysis.setPredictedLabelSetRanking(labelSetRanking);

        return predictionAnalysis;
    }


    public static ClassScoreCalculation decisionProcess(IMLGradientBoosting boosting, LabelTranslator labelTranslator, double prob,
                                                        Vector vector, int classIndex, int limit){
        ClassScoreCalculation classScoreCalculation = new ClassScoreCalculation(classIndex,labelTranslator.toExtLabel(classIndex),
                boosting.predictClassScore(vector,classIndex));
        classScoreCalculation.setClassProbability(prob);
        List<Regressor> regressors = boosting.getRegressors(classIndex);
        List<TreeRule> treeRules = new ArrayList<>();
        for (Regressor regressor : regressors) {
            if (regressor instanceof ConstantRegressor) {
                Rule rule = new ConstantRule(((ConstantRegressor) regressor).getScore());
                classScoreCalculation.addRule(rule);
            }

            if (regressor instanceof RegressionTree) {
                RegressionTree tree = (RegressionTree) regressor;
                TreeRule treeRule = new TreeRule(tree, vector);
                treeRules.add(treeRule);
            }
        }
        Comparator<TreeRule> comparator = Comparator.comparing(decision -> Math.abs(decision.getScore()));
        List<TreeRule> merged = TreeRule.merge(treeRules).stream().sorted(comparator.reversed())
                .limit(limit).collect(Collectors.toList());
        for (TreeRule treeRule : merged){
            classScoreCalculation.addRule(treeRule);
        }

        return classScoreCalculation;
    }

    //only show the positive class score calculation
    public static ClassScoreCalculation decisionProcess(CBM cbm, LabelTranslator labelTranslator, double prob,
                                                        Vector vector, int classIndex, int limit){
        if (cbm.getBinaryClassifiers()[0][classIndex] instanceof PriorProbClassifier){
            PriorProbClassifier priorProbClassifier = (PriorProbClassifier) cbm.getBinaryClassifiers()[0][classIndex];
            ClassScoreCalculation classScoreCalculation = new ClassScoreCalculation(classIndex,labelTranslator.toExtLabel(classIndex),
                    priorProbClassifier.predictClassProb(vector,1));
            classScoreCalculation.setClassProbability(prob);

            return classScoreCalculation;
        }
        LogisticRegression logisticRegression = (LogisticRegression)cbm.getBinaryClassifiers()[0][classIndex];
        ClassScoreCalculation classScoreCalculation = new ClassScoreCalculation(classIndex,labelTranslator.toExtLabel(classIndex),
                logisticRegression.predictClassScore(vector,1));
        classScoreCalculation.setClassProbability(prob);

        List<LinearRule> linearRules = new ArrayList<>();
        Rule bias = new ConstantRule(logisticRegression.getWeights().getBiasForClass(1));
        classScoreCalculation.addRule(bias);
        //todo speed up using sparsity
        for (int j=0;j<logisticRegression.getNumFeatures();j++){
            Feature feature = logisticRegression.getFeatureList().get(j);
            double weight = logisticRegression.getWeights().getWeightsWithoutBiasForClass(1).get(j);
            double featureValue = vector.get(j);
            double score = weight*featureValue;
            LinearRule rule = new LinearRule();
            rule.setFeature(feature);
            rule.setFeatureValue(featureValue);
            rule.setScore(score);
            rule.setWeight(weight);
            linearRules.add(rule);
        }

        Comparator<LinearRule> comparator = Comparator.comparing(decision -> Math.abs(decision.getScore()));
        List<LinearRule> sorted = linearRules.stream().sorted(comparator.reversed()).limit(limit).collect(Collectors.toList());

        for (LinearRule linearRule : sorted){
            classScoreCalculation.addRule(linearRule);
        }

        return classScoreCalculation;
    }
}
