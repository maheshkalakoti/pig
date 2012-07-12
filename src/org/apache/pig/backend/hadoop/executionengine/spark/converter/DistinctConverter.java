package org.apache.pig.backend.hadoop.executionengine.spark.converter;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.Text;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.PODistinct;
import org.apache.pig.backend.hadoop.executionengine.spark.SparkUtil;
import org.apache.pig.data.Tuple;

import scala.Function1;
import scala.Function2;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.reflect.ClassManifest;
import scala.runtime.AbstractFunction1;
import scala.runtime.AbstractFunction2;
import spark.PairRDDFunctions;
import spark.RDD;

public class DistinctConverter implements POConverter<Tuple, Tuple, PODistinct> {
    private static final Log LOG = LogFactory.getLog(DistinctConverter.class);

    private static final Function1<Tuple, Tuple2<Tuple, Object>> TO_KEY_VALUE_FUNCTION = new ToKeyValueFunction();
    private static final Function2<Object, Object, Object> MERGE_VALUES_FUNCTION = new MergeValuesFunction();
    private static final Function1<Tuple2<Tuple, Object>, Tuple> TO_VALUE_FUNCTION = new ToValueFunction();

    @Override
    public RDD<Tuple> convert(List<RDD<Tuple>> predecessors, PODistinct poDistinct)
            throws IOException {
        SparkUtil.assertPredecessorSize(predecessors, poDistinct, 1);
        RDD<Tuple> rdd = predecessors.get(0);

        ClassManifest<Tuple2<Tuple, Object>> tuple2ClassManifest = (ClassManifest<Tuple2<Tuple, Object>>)(Object)SparkUtil.getManifest(Tuple2.class);

        RDD<Tuple2<Tuple, Object>> rddPairs = rdd.map(TO_KEY_VALUE_FUNCTION, tuple2ClassManifest);
        PairRDDFunctions<Tuple, Object> pairRDDFunctions =
                new PairRDDFunctions<Tuple, Object>(rddPairs, SparkUtil.getManifest(Tuple.class), SparkUtil.getManifest(Object.class));

        return pairRDDFunctions.reduceByKey(MERGE_VALUES_FUNCTION).map(TO_VALUE_FUNCTION, SparkUtil.getManifest(Tuple.class));
    }

    private static final class ToKeyValueFunction extends AbstractFunction1<Tuple,Tuple2<Tuple, Object>> implements Serializable {

        @Override
        public Tuple2<Tuple, Object> apply(Tuple t) {
            LOG.debug("DistinctConverter.ToKeyValueFunction in "+t);
            Tuple key = t;
            Object value = null; //value
            // (key, value)
            Tuple2<Tuple, Object> out = new Tuple2<Tuple, Object>(key, value);
            LOG.debug("DistinctConverter.ToKeyValueFunction out "+out);
            return out;
        }
    }

    private static final class MergeValuesFunction extends AbstractFunction2<Object,Object,Object> implements Serializable {
        @Override
        public Object apply(Object arg0, Object arg1) {
            return null;
        }
    }

    private static final class ToValueFunction extends AbstractFunction1<Tuple2<Tuple, Object>, Tuple> implements Serializable {
        @Override
        public Tuple apply(Tuple2<Tuple, Object> input) {
            return input._1;
        }
    }
}
