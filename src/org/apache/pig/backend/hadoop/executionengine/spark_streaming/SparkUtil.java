package org.apache.pig.backend.hadoop.executionengine.spark_streaming;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobConf;
import org.apache.pig.backend.hadoop.datastorage.ConfigurationUtil;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POGlobalRearrange;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.util.ObjectSerializer;
import org.apache.pig.impl.util.UDFContext;

import scala.Tuple2;
import scala.Product2;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.reflect.ClassManifest;
import scala.reflect.ClassManifest$;

import org.apache.spark.rdd.RDD;
import org.apache.spark.streaming.api.java.JavaDStream;

import java.io.IOException;
import java.util.List;

/**
 * @author billg
 */
public class SparkUtil {

    public static <T> ClassManifest<T> getManifest(Class<T> clazz) {
        return ClassManifest$.MODULE$.fromClass(clazz);
    }

    @SuppressWarnings("unchecked")
    public static <K,V> ClassManifest<Tuple2<K, V>> getTuple2Manifest() {
        return (ClassManifest<Tuple2<K, V>>)(Object)getManifest(Tuple2.class);
    }
    
    @SuppressWarnings("unchecked")
    public static <K,V> ClassManifest<Product2<K, V>> getProduct2Manifest() {
        return (ClassManifest<Product2<K, V>>)(Object)getManifest(Product2.class);
    }

    public static JobConf newJobConf(PigContext pigContext) throws IOException {
        JobConf jobConf = new JobConf(ConfigurationUtil.toConfiguration(pigContext.getProperties()));
        jobConf.set("pig.pigContext", ObjectSerializer.serialize(pigContext));
        UDFContext.getUDFContext().serialize(jobConf);
        jobConf.set("udf.import.list", ObjectSerializer.serialize(PigContext.getPackageImportList()));
        return jobConf;
    }
    

    public static <T> Seq<T> toScalaSeq(List<T> list) {
        return JavaConversions.asScalaBuffer(list);
    }

    public static void assertPredecessorSize(List<JavaDStream<Tuple>> predecessors,
                                             PhysicalOperator physicalOperator, int size) {
        if (predecessors.size() != size) {
            throw new RuntimeException("Should have " + size + " predecessors for " +
                    physicalOperator.getClass() + ". Got : " + predecessors.size());
        }
    }

    public static void assertPredecessorSizeGreaterThan(List<JavaDStream<Tuple>> predecessors,
                                             PhysicalOperator physicalOperator, int size) {
        if (predecessors.size() <= size) {
            throw new RuntimeException("Should have greater than" + size + " predecessors for " +
                    physicalOperator.getClass() + ". Got : " + predecessors.size());
        }
    }

    public static  int getParallelism(List<JavaDStream<Tuple>> predecessors, PhysicalOperator physicalOperator) {
        int parallelism = physicalOperator.getRequestedParallelism();
        if (parallelism <= 0) {
            // Parallelism wasn't set in Pig, so set it to whatever Spark thinks is reasonable.
            parallelism =  predecessors.get(0).context().sparkContext().defaultParallelism();
        }
        return parallelism;
    }

}
