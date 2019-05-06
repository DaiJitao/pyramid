package edu.neu.ccs.pyramid.util;

import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.SequentialAccessSparseVector;
import org.apache.mahout.math.Vector;

import java.util.List;

/**
 * Created by chengli on 1/3/16.
 */
public class Vectors {

    public static double[] toArray(Vector vector){
        double[] arr = new double[vector.size()];
        for (Vector.Element nonZero: vector.nonZeroes()){
            int index = nonZero.index();
            double v = nonZero.get();
            arr[index] = v;
        }
        return arr;
    }

    public static double cosine(Vector vector1, Vector vector2){
        double prod = vector1.dot(vector2);
        return prod/(vector1.norm(2)*vector2.norm(2));
    }

    public static Vector conatenateToSparseRandom(List<Vector> vectors){
        int size = 0;
        for (Vector vector: vectors){
            size += vector.size();
        }
        Vector concatenated = new RandomAccessSparseVector(size);
        int offset = 0;
        for (Vector vector: vectors){
            for (Vector.Element nonZeros: vector.nonZeroes()) {
                int index = nonZeros.index();
                double value = nonZeros.get();
                concatenated.set(index+offset,value);
            }
            offset += vector.size();
        }
        return concatenated;
    }

    public static Vector concatenate(Vector vector, Vector vector2){

        Vector con = null;
        if (vector instanceof DenseVector){
            con = new DenseVector(vector.size()+vector2.size());
        }
        if (vector instanceof RandomAccessSparseVector){
            con = new RandomAccessSparseVector(vector.size()+vector2.size());
        }

        if (vector instanceof SequentialAccessSparseVector){
            con = new SequentialAccessSparseVector(vector.size()+vector2.size());
        }




        for (Vector.Element nonZeros: vector.nonZeroes()){
            int index = nonZeros.index();
            double value = nonZeros.get();
            con.set(index, value);
        }


        for (Vector.Element nonZeros: vector2.nonZeroes()){
            int index = nonZeros.index();
            double value = nonZeros.get();
            con.set(index+vector.size(), value);
        }

        return con;
    }

    public static Vector concatenate(Vector vector, double number){
        Vector con = null;
        if (vector instanceof DenseVector){
            con = new DenseVector(vector.size()+1);
        }
        if (vector instanceof RandomAccessSparseVector){
            con = new RandomAccessSparseVector(vector.size()+1);
        }

        if (vector instanceof SequentialAccessSparseVector){
            con = new SequentialAccessSparseVector(vector.size()+1);
        }

        for (Vector.Element nonZeros: vector.nonZeroes()){
            int index = nonZeros.index();
            double value = nonZeros.get();
            con.set(index, value);
        }
        con.set(con.size()-1,number);
        return con;
    }

    public static Vector concatenate(Vector vector, double[] numbers){
        Vector con = null;
        if (vector instanceof DenseVector){
            con = new DenseVector(vector.size()+numbers.length);
        }
        if (vector instanceof RandomAccessSparseVector){
            con = new RandomAccessSparseVector(vector.size()+numbers.length);
        }

        if (vector instanceof SequentialAccessSparseVector){
            con = new SequentialAccessSparseVector(vector.size()+numbers.length);
        }

        for (Vector.Element nonZeros: vector.nonZeroes()){
            int index = nonZeros.index();
            double value = nonZeros.get();
            con.set(index, value);
        }
        for (int i=0;i<numbers.length;i++){
            con.set(i+vector.size(), numbers[i]);
        }
        return con;
    }


    public static double dot(Vector vector1, Vector vector2){
        if (vector1.size()!=vector2.size()){
            throw new IllegalArgumentException("vector1.size()!=vector2.size()");
        }

        boolean vector1Dense = vector1.isDense();
        boolean vector2Dense = vector2.isDense();

        if (vector1Dense&&vector2Dense){
            return dotDenseDense(vector1,vector2);
        } else if (vector1Dense && !vector2Dense){
            return dotDenseSparse(vector1,vector2);
        } else if (!vector1Dense && vector2Dense){
            return dotDenseSparse(vector2,vector1);
        } else {
            throw new UnsupportedOperationException("sparse dot sparse is not supported");
        }

    }

    private static double dotDenseDense(Vector vector1, Vector vector2){
        int size = vector1.size();
        double sum = 0;
        for (int d=0;d<size;d++){
            sum += vector1.getQuick(d)*vector2.getQuick(d);
        }
        return sum;
    }

    private static double dotDenseSparse(Vector denseVector, Vector sparseVector){
        double sum = 0;
        for (Vector.Element element: sparseVector.nonZeroes()){
            int index = element.index();
            double value = element.get();
            sum += value*denseVector.getQuick(index);
        }
        return sum;
    }
}
