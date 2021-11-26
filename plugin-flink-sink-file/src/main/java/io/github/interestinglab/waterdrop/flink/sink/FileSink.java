/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.interestinglab.waterdrop.flink.sink;

import io.github.interestinglab.waterdrop.config.Config;
import io.github.interestinglab.waterdrop.common.config.CheckConfigUtil;
import io.github.interestinglab.waterdrop.flink.FlinkEnvironment;
import io.github.interestinglab.waterdrop.flink.batch.FlinkBatchSink;
import io.github.interestinglab.waterdrop.flink.stream.FlinkStreamSink;
import io.github.interestinglab.waterdrop.common.config.CheckResult;
import org.apache.flink.api.common.io.FileOutputFormat;
import org.apache.flink.api.common.serialization.Encoder;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.io.TextOutputFormat;
import org.apache.flink.api.java.operators.DataSink;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class FileSink implements FlinkStreamSink<Row, Row>, FlinkBatchSink<Row, Row> {

    private static final Logger LOG = LoggerFactory.getLogger(FileSink.class);

    private final static String PATH = "path";
    private final static String FORMAT = "format";
    private final static String WRITE_MODE = "write_mode";

    private Config config;

    private FileOutputFormat outputFormat;

    private Path filePath;

    @Override
    public DataStreamSink<Row> outputStream(FlinkEnvironment env, DataStream<Row> dataStream) {

        final StreamingFileSink<Row> sink = StreamingFileSink
                .forRowFormat(filePath, (Encoder<Row>) (element, stream) -> {
                    PrintStream out = new PrintStream(stream);
                    out.println(element);
                })
                .build();
        return dataStream.addSink(sink);
    }

    @Override
    public DataSink<Row> outputBatch(FlinkEnvironment env, DataSet<Row> dataSet) {
        String format = config.getString(FORMAT);
        switch (format) {
            case "json":
                RowTypeInfo rowTypeInfo = (RowTypeInfo) dataSet.getType();
                outputFormat = new JsonRowOutputFormat(filePath, rowTypeInfo);
                break;
            case "csv":
                CsvRowOutputFormat csvFormat = new CsvRowOutputFormat(filePath);
                outputFormat = csvFormat;
                break;
            case "text":
                outputFormat = new TextOutputFormat(filePath);
                break;
            default:
                LOG.warn(" unknown file_format [{}],only support json,csv,text", format);
                break;

        }
        if (config.hasPath(WRITE_MODE)) {
            String mode = config.getString(WRITE_MODE);
            outputFormat.setWriteMode(FileSystem.WriteMode.valueOf(mode));
        }
        return dataSet.output(outputFormat);
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public CheckResult checkConfig() {
        return CheckConfigUtil.check(config, PATH, FORMAT);
    }

    @Override
    public void prepare(FlinkEnvironment env) {
        String path = config.getString(PATH);
        filePath = new Path(path);
    }
}