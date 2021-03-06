package edu.neu.ccs.pyramid.ranking;

import edu.neu.ccs.pyramid.dataset.DataSet;
import edu.neu.ccs.pyramid.eval.NDCG;
import edu.neu.ccs.pyramid.optimization.gradient_boosting.GBOptimizer;
import edu.neu.ccs.pyramid.optimization.gradient_boosting.GradientBoosting;
import edu.neu.ccs.pyramid.regression.RegressorFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class LambdaMARTOptimizer extends GBOptimizer {
    private double[] relevanceGrades;
    private int ndcgTruncationLevel=10;

    // format instanceIdsInEachQuery.get(query id).get(local instance id in query) = global instance id in dataset
    private List<List<Integer>> instanceIdsInEachQuery;
    private int numQueries;


    public LambdaMARTOptimizer(LambdaMART lambdaMART, DataSet dataSet, double[] relevanceGrades, RegressorFactory factory, List<List<Integer>> instanceIdsInEachQuery) {
        super(lambdaMART, dataSet, factory);
        this.relevanceGrades = relevanceGrades;
        this.instanceIdsInEachQuery = instanceIdsInEachQuery;
        this.numQueries = instanceIdsInEachQuery.size();
    }

    public void setNdcgTruncationLevel(int ndcgTruncationLevel) {
        this.ndcgTruncationLevel = ndcgTruncationLevel;
    }

    private List<Integer> instancesForQuery(int queryId){
        return instanceIdsInEachQuery.get(queryId);
    }

    // calculate gradients for all instances in a query
    private double gradientForInstanceInQuery(double[] predictedScores, double[] relevance, int dataIndexInQuery){
        double grade = relevance[dataIndexInQuery];
        double score = predictedScores[dataIndexInQuery];
        double gradient = 0;
        //todo times ndcg delta
        for (int i=0;i<relevance.length;i++){
            if (grade>relevance[i]){
                gradient += 1.0/(1+Math.exp(score - predictedScores[i]))*delta(predictedScores,relevance, dataIndexInQuery, i, ndcgTruncationLevel);
            }

            if (grade< relevance[i]){
                gradient -= 1.0/(1+Math.exp(predictedScores[i]- score))*delta(predictedScores,relevance, dataIndexInQuery, i, ndcgTruncationLevel);
            }
        }
        return gradient;
    }

    private double delta(double[] predictedScores, double[] relevance, int data1, int data2, int truncation){
        double ndcg = NDCG.ndcg(relevance,predictedScores,truncation);
        double[] swapped = Arrays.copyOf(relevance, relevance.length);
        double data1Rel = relevance[data1];
        double data2Rel = relevance[data2];
        swapped[data1] = data2Rel;
        swapped[data2] = data1Rel;
        double swappedNDCG = NDCG.ndcg(swapped, predictedScores, truncation);
        return Math.abs(swappedNDCG-ndcg);
    }

    // calculate gradients for all instances in a query
    private double[] gradientForQuery(int queryIndex){
        List<Integer> instancesForQuery = instancesForQuery(queryIndex);
        double[] predictedScores = instancesForQuery.stream().mapToDouble(i->scoreMatrix.getScoresForData(i)[0]).toArray();
        double[] relevance = instancesForQuery.stream().mapToDouble(i->relevanceGrades[i]).toArray();
        double[] gradients = new double[instancesForQuery.size()];
        for (int i=0;i<gradients.length;i++){
            gradients[i] = gradientForInstanceInQuery(predictedScores,relevance,i);
        }
        return gradients;
    }

    @Override
    protected void addPriors() {
        //for ranking purpose, the neutral point does not matter
        // if we add a prior, it will simply increase the score for all points
    }

    @Override
    protected double[] gradient(int ensembleIndex) {
        double[] gradients = new double[dataSet.getNumDataPoints()];
        IntStream.range(0, numQueries).parallel()
                .forEach(q->{
                    double[] queryGradients = gradientForQuery(q);
                    List<Integer> instancesInQuery = instancesForQuery(q);
                    for (int i=0;i<instancesInQuery.size();i++){
                        int globalIndex = instancesInQuery.get(i);
                        gradients[globalIndex] = queryGradients[i];
                    }
                });
        return gradients;
    }

    @Override
    protected void initializeOthers() {

    }

    @Override
    protected void updateOthers() {

    }
}
