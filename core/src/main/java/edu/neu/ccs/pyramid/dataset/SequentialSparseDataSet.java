package edu.neu.ccs.pyramid.dataset;

import org.apache.mahout.math.SequentialAccessSparseVector;
import org.apache.mahout.math.Vector;


/**
 * Created by Rainicy on 1/3/16.
 */
public class SequentialSparseDataSet extends AbstractDataSet implements DataSet{

    protected transient SequentialAccessSparseVector[] featureRows;
    protected transient SequentialAccessSparseVector[] featureColumns;

    public SequentialSparseDataSet(int numDataPoints, int numFeatures, boolean missingValue) {
        super(numDataPoints,numFeatures,missingValue);
        this.featureRows = new SequentialAccessSparseVector[numDataPoints];
        for (int i=0;i<numDataPoints;i++){
            this.featureRows[i] = new SequentialAccessSparseVector(numFeatures);
        }
        this.featureColumns = new SequentialAccessSparseVector[numFeatures];
        for (int j=0;j<numFeatures;j++){
            this.featureColumns[j] = new SequentialAccessSparseVector(numDataPoints);
        }
    }

    @Override
    public Density density() {
        return Density.SPARSE_SEQUENTIAL;
    }

    @Override
    public Vector getColumn(int featureIndex) {
        return this.featureColumns[featureIndex];
    }

    @Override
    public Vector getRow(int dataPointIndex) {
        return this.featureRows[dataPointIndex];
    }

    /**
     * must be synchronized, otherwise may get ArrayIndexOutOfBoundsException
     * @param dataPointIndex
     * @param featureIndex
     * @param featureValue
     */
    @Override
    public synchronized void setFeatureValue(int dataPointIndex, int featureIndex, double featureValue) {
        if ((!this.hasMissingValue()) && Double.isNaN(featureValue)){
            throw new IllegalArgumentException("missing value is not allowed in this data set");
        }
        if (Double.isInfinite(featureValue)){
            throw new IllegalArgumentException("feature value cannot be infinity");
        }
        this.featureRows[dataPointIndex].set(featureIndex, featureValue);
        this.featureColumns[featureIndex].set(dataPointIndex, featureValue);
    }


    @Override
    public boolean isDense() {
        return false;
    }
}
