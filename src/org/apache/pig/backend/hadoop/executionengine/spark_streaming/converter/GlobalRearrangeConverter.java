package org.apache.pig.backend.hadoop.executionengine.spark_streaming.converter;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POGlobalRearrange;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POPackage;
import org.apache.pig.backend.hadoop.executionengine.spark_streaming.SparkUtil;
import org.apache.pig.backend.hadoop.executionengine.spark_streaming.converter.DistinctConverter.ToValueFunction;
import org.apache.pig.data.DefaultTuple;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.spark.HashPartitioner;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.rdd.CoGroupedRDD;
import org.apache.spark.rdd.RDD;
import org.apache.spark.streaming.dstream.DStream;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;

import scala.Function1;
import scala.Product2;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.reflect.ClassTag;
import scala.runtime.AbstractFunction1;

@SuppressWarnings({ "serial"})
public class GlobalRearrangeConverter implements POConverter<Tuple, Tuple, POGlobalRearrange> {
    private static final Log LOG = LogFactory.getLog(GlobalRearrangeConverter.class);

    private static final TupleFactory tf = TupleFactory.getInstance();

    // GROUP FUNCTIONS
    private static final ToKeyValueFunction TO_KEY_VALUE_FUNCTION = new ToKeyValueFunction();
    private static final GetKeyFunction GET_KEY_FUNCTION = new GetKeyFunction();
    private static final CreatePairFunction CREATE_PAIR_FUNCTION = new CreatePairFunction();
   // COGROUP FUNCTIONS
    private static final GroupTupleFunction GROUP_TUPLE_FUNCTION = new GroupTupleFunction();
    private static final ToGroupKeyValueFunction TO_GROUP_KEY_VALUE_FUNCTION = new ToGroupKeyValueFunction();

    @Override
    public JavaDStream<Tuple> convert(List<JavaDStream<Tuple>> predecessors,
            POGlobalRearrange physicalOperator) throws IOException {
        SparkUtil.assertPredecessorSizeGreaterThan(predecessors, physicalOperator, 0);
        int parallelism = 1;//SparkUtil.getParallelism(predecessors, physicalOperator);
        
        String reducers = System.getenv("SPARK_REDUCERS");
        if (reducers != null ) {
            parallelism = Integer.parseInt(reducers);
        }
        LOG.info("Parallelism for Spark groupBy: " + parallelism);
        
        if (predecessors.size() == 1) {
            //GROUP
        	
        	return  predecessors.get(0).map(CREATE_PAIR_FUNCTION).groupByKey().map(GROUP_TUPLE_FUNCTION);
        	
        } else {
        	return  predecessors.get(0).map(CREATE_PAIR_FUNCTION).groupByKey().map(GROUP_TUPLE_FUNCTION);
        		/*
            //COGROUP
            // each pred returns (index, key, value)
            ClassTag<Tuple2<Object, Tuple>> tuple2ClassTag = SparkUtil.<Object, Tuple>getTuple2Manifest();
            
            List<JavaDStream<Tuple2<Object, Tuple>>> rddPairs = new ArrayList();
            for (JavaDStream<Tuple> rdd : predecessors) {
            	JavaDStream<Tuple2<Object, Tuple>> rddPair = rdd.map(TO_KEY_VALUE_FUNCTION, tuple2ClassTag);
                rddPairs.add(rddPair);
            }

            // Something's wrong with the type parameters of CoGroupedRDD
            // key and value are the same type ???
            
            CoGroupedRDD<Object> coGroupedRDD = new CoGroupedRDD<Object>(
                (Seq<JavaDStream<? extends Product2<Object, ?>>>)(Object)(JavaConversions.asScalaBuffer(rddPairs).toSeq()),
                new HashPartitioner(parallelism));

            JavaDStream<Tuple2<Object,Seq<Seq<Tuple>>>> rdd = (JavaDStream<Tuple2<Object,Seq<Seq<Tuple>>>>)(Object)coGroupedRDD;
            return rdd.map(TO_GROUP_KEY_VALUE_FUNCTION,  SparkUtil.getManifest(Tuple.class));
            */
        }
    }
    
    private static class GetKeyFunction extends Function<Tuple2<Tuple,List<Tuple>>, Tuple> implements Serializable {

    	@Override
		public Tuple call(Tuple2<Tuple, List<Tuple>> t) throws Exception {
    		
                LOG.debug("GetKeyFunction in "+t);
                // see PigGenericMapReduce For the key
                Tuple tuple = tf.newTuple();
                tuple.append(t._1);
                tuple.append(t._2.iterator());
                LOG.debug("GetKeyFunction out "+tuple);
                return tuple;
         
        }

		

		
    }
    
    private static class CreatePairFunction extends PairFunction<Tuple, Object, Tuple>  implements Serializable {

    	@Override public Tuple2<Object, Tuple> call(Tuple s) throws Exception {
    				Tuple2<Object, Tuple> output=new Tuple2<Object, Tuple>(s.get(1),s);
			      return output;
			    }
    }

    private static class GroupTupleFunction extends Function<Tuple2<Object, List<Tuple>>, Tuple> implements Serializable {

        @Override
        public Tuple call(Tuple2<Object, List<Tuple>> v1) {
            try {
                LOG.debug("GroupTupleFunction in "+v1);
                Tuple tuple = tf.newTuple(2);
                tuple.set(0, v1._1()); // the (index, key) tuple
                tuple.set(1, v1._2().iterator()); // the Seq<Tuple> aka bag of values
                LOG.debug("GroupTupleFunction out "+tuple);
                return tuple;
            } catch (ExecException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ToKeyValueFunction extends Function<Tuple,Tuple2<Object, Tuple>> implements Serializable {

        @Override
        public Tuple2<Object, Tuple> call(Tuple t) {
            try {
                // (index, key, value)
                LOG.debug("ToKeyValueFunction in "+t);
                Object key = t.get(1);
                Tuple value = (Tuple)t.get(2); //value
                // (key, value)
                Tuple2<Object, Tuple> out = new Tuple2<Object, Tuple>(key, value);
                LOG.debug("ToKeyValueFunction out "+out);
                return out;
            } catch (ExecException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ToGroupKeyValueFunction extends Function<Tuple2<Object,Seq<Seq<Tuple>>>,Tuple> implements Serializable {

        @Override
        public Tuple call(Tuple2<Object, Seq<Seq<Tuple>>> input) {
            try {
                LOG.debug("ToGroupKeyValueFunction2 in "+input);
                final Object key = input._1();
                Seq<Seq<Tuple>> bags = input._2();
                Iterable<Seq<Tuple>> bagsList = JavaConversions.asJavaIterable(bags);
                int i = 0;
                List<Iterator<Tuple>> tupleIterators = new ArrayList();
                for (Seq<Tuple> bag : bagsList) {
                    Iterator<Tuple> iterator = JavaConversions.asJavaCollection(bag).iterator();
                    final int index = i;
                    tupleIterators.add(new IteratorTransform<Tuple, Tuple>(iterator) {
                        @Override
                        protected Tuple transform(Tuple next) {
                            try {
                                Tuple tuple = tf.newTuple(3);
                                tuple.set(0, index);
                                tuple.set(1, key);
                                tuple.set(2, next);
                                return tuple;
                            } catch (ExecException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                    ++i;
                }
                Tuple out = tf.newTuple(2);
                out.set(0, key);
                out.set(1, new IteratorUnion<Tuple>(tupleIterators.iterator()));
                LOG.debug("ToGroupKeyValueFunction2 out "+out);
                return out;
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class IteratorUnion<T> implements Iterator<T> {

        private final Iterator<Iterator<T>> iterators;

        private Iterator<T> current;

        public IteratorUnion(Iterator<Iterator<T>> iterators) {
            super();
            this.iterators = iterators;
        }

        @Override
        public boolean hasNext() {
            if (current != null && current.hasNext()) {
                return true;
            } else if (iterators.hasNext()) {
                current = iterators.next();
                return hasNext();
            } else {
                return false;
            }
        }

        @Override
        public T next() {
            return current.next();
        }

        @Override
        public void remove() {
            current.remove();
        }

    }

}