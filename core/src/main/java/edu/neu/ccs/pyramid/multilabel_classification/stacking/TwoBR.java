package edu.neu.ccs.pyramid.multilabel_classification.stacking;

import edu.neu.ccs.pyramid.dataset.LabelTranslator;
import edu.neu.ccs.pyramid.dataset.MultiLabel;
import edu.neu.ccs.pyramid.feature.FeatureList;
import edu.neu.ccs.pyramid.multilabel_classification.MultiLabelClassifier;
import edu.neu.ccs.pyramid.util.Vectors;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;

public class TwoBR implements MultiLabelClassifier{
    private static final long serialVersionUID = 1L;
    private MultiLabelClassifier.ClassProbEstimator stage1BR;
    private MultiLabelClassifier.ClassProbEstimator stage2BR;
    private boolean useXStage2 = false;


    public void setStage1BR(ClassProbEstimator stage1BR) {
        this.stage1BR = stage1BR;
    }

    public void setStage2BR(ClassProbEstimator stage2BR) {
        this.stage2BR = stage2BR;
    }

    public void setUseXStage2(boolean useXStage2) {
        this.useXStage2 = useXStage2;
    }

    @Override
    public int getNumClasses() {
        return stage1BR.getNumClasses();
    }

    @Override
    public MultiLabel predict(Vector vector) {
//        MultiLabel stage1Predictions = stage1BR.predict(vector);
        double[] stage1Predictions = stage1BR.predictClassProbs(vector);
        Vector stage2input;

        if (useXStage2){
            stage2input = Vectors.concatenate(vector, stage1Predictions);
        } else {
            stage2input = new DenseVector(stage1Predictions);
        }
        return stage2BR.predict(stage2input);
    }

    @Override
    public FeatureList getFeatureList() {
        return null;
    }

    @Override
    public LabelTranslator getLabelTranslator() {
        return null;
    }
}
