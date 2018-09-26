/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.pipes.consumers;

import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogAppender;
import org.nuxeo.lib.stream.log.internals.CloseableLogAppender;

/**
 * A record consumer using a log appender
 */
public class LogAppenderConsumer implements Consumer<Record>, AutoCloseable {

    private static final Log log = LogFactory.getLog(LogAppenderConsumer.class);
    private final CloseableLogAppender<Record> appender;

    public LogAppenderConsumer(CloseableLogAppender<Record> appender) {
        this.appender = appender;
    }

    @Override
    public void accept(Record record) {
        if (record != null) {
            getAppender().append(record.key, record);
        }
    }

    protected LogAppender<Record> getAppender() {
        if (appender.closed()) {
            log.warn("We can't append to a closed appender. " + appender.name());
        }
        return appender;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LogAppenderConsumer{");
        sb.append("appender=").append(appender);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public void close() {
        if (appender != null) {
            appender.close();
        }
    }
}
