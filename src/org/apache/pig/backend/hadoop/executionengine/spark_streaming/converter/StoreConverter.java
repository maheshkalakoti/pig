package org.apache.pig.backend.hadoop.executionengine.spark_streaming.converter;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.pig.StoreFuncInterface;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.JobControlCompiler;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigOutputFormat;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POLoad;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POStore;
import org.apache.pig.backend.hadoop.executionengine.spark_streaming.SparkUtil;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.util.ObjectSerializer;

import scala.Tuple2;
import scala.runtime.AbstractFunction1;

import org.apache.spark.rdd.PairRDDFunctions;
import org.apache.spark.rdd.RDD;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.api.java.function.Function;

import com.google.common.collect.Lists;

/**
 * Converter that takes a POStore and stores it's content.
 *
 * @author billg
 */
@SuppressWarnings({ "serial"})
public class StoreConverter implements POConverter<Tuple, Tuple2<Text, Tuple>, POStore> {
	private static final Log LOG = LogFactory.getLog(GlobalRearrangeConverter.class);
    private static final FromTupleFunction FROM_TUPLE_FUNCTION = new FromTupleFunction();

    private PigContext pigContext;

    public StoreConverter(PigContext pigContext) {
        this.pigContext = pigContext;
    }

    
    @Override
    public JavaDStream<Tuple> convert(List<JavaDStream<Tuple>> predecessors, POStore physicalOperator) throws IOException {

        SparkUtil.assertPredecessorSize(predecessors, physicalOperator, 1);
        JavaDStream<Tuple> rdd = predecessors.get(0);
        JobConf storeJobConf = SparkUtil.newJobConf(pigContext);
        POStore poStore = configureStorer(storeJobConf, physicalOperator);
        rdd.dstream().print();
        //rdd.dstream().saveAsObjectFiles("hdfs://localhost:9000/output", "mayur");

        return rdd;
    }

    private static POStore configureStorer(JobConf jobConf,
            PhysicalOperator physicalOperator) throws IOException {
        ArrayList<POStore> storeLocations = Lists.newArrayList();
        POStore poStore = (POStore)physicalOperator;
        storeLocations.add(poStore);
        StoreFuncInterface sFunc = poStore.getStoreFunc();
        sFunc.setStoreLocation(poStore.getSFile().getFileName(), new org.apache.hadoop.mapreduce.Job(jobConf));
        poStore.setInputs(null);
        poStore.setParentPlan(null);

        jobConf.set(JobControlCompiler.PIG_MAP_STORES, ObjectSerializer.serialize(Lists.newArrayList()));
        jobConf.set(JobControlCompiler.PIG_REDUCE_STORES, ObjectSerializer.serialize(storeLocations));
        return poStore;
    }

    private static class FromTupleFunction extends Function<Tuple, Tuple2<Text, Tuple>>
            implements Serializable {

        private static Text EMPTY_TEXT = new Text();

        public Tuple2<Text, Tuple> call(Tuple v1) {
            return new Tuple2<Text, Tuple>(EMPTY_TEXT, v1);
        }
    }

	
}
