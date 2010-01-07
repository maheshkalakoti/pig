/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pig.backend.local.executionengine;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.pig.backend.hadoop.datastorage.ConfigurationUtil;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.executionengine.ExecJob;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.io.FileSpec;
import org.apache.pig.LoadFunc;
import org.apache.pig.impl.io.FileLocalizer;
import org.apache.pig.impl.io.BufferedPositionedInputStream;
import org.apache.pig.impl.io.ReadToEndLoader;
import org.apache.pig.tools.pigstats.PigStats;


public class LocalJob implements ExecJob {

    private final Log log = LogFactory.getLog(getClass());
    
    protected JOB_STATUS status;
    protected PigContext pigContext;
    protected FileSpec outFileSpec;
    protected String alias;
    private PigStats stats;
    
    public LocalJob(JOB_STATUS status,
                PigContext pigContext,
                FileSpec outFileSpec) {
        this.status = status;
        this.pigContext = pigContext;
        this.outFileSpec = outFileSpec;
    }
    
    public LocalJob(JOB_STATUS status,
            PigContext pigContext,
            FileSpec outFileSpec,
            PigStats stats) {
        this.status = status;
        this.pigContext = pigContext;
        this.outFileSpec = outFileSpec;
        this.stats = stats;
    } 
    
    public JOB_STATUS getStatus() {
        return status;
    }
    
    public boolean hasCompleted() throws ExecException {
        return true;
    }
    
    public Iterator<Tuple> getResults() throws ExecException {
        final LoadFunc p;
        final InputFormat inputFormat;
        
        try{
             LoadFunc origLoadFunc = (LoadFunc)PigContext.instantiateFuncFromSpec(outFileSpec.getFuncSpec());
             
             String fileName = outFileSpec.getFileName();
             if(!fileName.startsWith("file://")) {
                 fileName = "file://" + fileName;
             }
             //XXX: FIXME: ensure this works in local mode (part of load-store redesign changes)
             p = new ReadToEndLoader(origLoadFunc, ConfigurationUtil.toConfiguration(
                                         pigContext.getProperties()), fileName, 0);
        }catch (Exception e){
            throw new ExecException("Unable to get results for " + outFileSpec, e);
        }
        
        return new Iterator<Tuple>() {
            Tuple   t;
            boolean atEnd;

            public boolean hasNext() {
                if (atEnd)
                    return false;
                try {
                    if (t == null)
                        t = p.getNext();
                    if (t == null)
                        atEnd = true;
                } catch (Exception e) {
                    log.error(e);
                    t = null;
                    atEnd = true;
                }
                return !atEnd;
            }

            public Tuple next() {
                Tuple next = t;
                if (next != null) {
                    t = null;
                    return next;
                }
                try {
                    next = p.getNext();
                } catch (Exception e) {
                    log.error(e);
                }
                if (next == null)
                    atEnd = true;
                return next;
            }

            public void remove() {
                throw new RuntimeException("Removal not supported");
            }

        };
    }

    public Properties getContiguration() {
        Properties props = new Properties();
        return props;
    }

    public PigStats getStatistics() {
        //throw new UnsupportedOperationException();
        return stats;
    }

    public void completionNotification(Object cookie) {
        throw new UnsupportedOperationException();
    }
    
    public void kill() throws ExecException {
        throw new UnsupportedOperationException();
    }
    
    public void getLogs(OutputStream log) throws ExecException {
        throw new UnsupportedOperationException();
    }
    
    public void getSTDOut(OutputStream out) throws ExecException {
        throw new UnsupportedOperationException();
    }
    
    public void getSTDError(OutputStream error) throws ExecException {
        throw new UnsupportedOperationException();
    }

    public Exception getException() {
        return null;
    }

    @Override
    public String getAlias() throws ExecException {
        return alias;
    }
}