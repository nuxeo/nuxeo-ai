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
package org.nuxeo.runtime.stream.pipes.functions;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.stream.pipes.streams.Initializable;

/**
 * A function that first applies a predicate filter.
 */
public class FilterFunction<T, R> implements Function<T, R>, MetricsProducer, Initializable {

    private static final Log log = LogFactory.getLog(FilterFunction.class);

    public final Predicate<? super T> filter;
    public final Function<? super T, ? extends R> transformation;

    //metrics
    private long supplied = 0;
    private long errors = 0;
    private long transformed = 0;
    private long filterFailed = 0;

    public FilterFunction(Predicate<? super T> filter, Function<? super T, ? extends R> transformation) {
        this.filter = filter;
        this.transformation = transformation;
    }


    @Override
    public void init(Map<String, String> options) {
        if (log.isDebugEnabled()) {
            log.debug("Initializing function " + this.getClass().getSimpleName());
        }
    }

    @Override
    public R apply(T in) {
        supplied++;
        try {
            if (filter.test(in)) {
                R transform = transformation.apply(in);
                transformed++;
                return transform;
            } else {
                filterFailed++;
            }
        } catch (NullPointerException | ClassCastException cce) {
            log.error("Invalid function definition ", cce);
            errors++;
        } catch (NuxeoException ne) {
            log.error("Nuxeo error from function ", ne);
            errors++;
        }
        return null;
    }

    @Override
    public void withMetrics(NuxeoMetricSet nuxeoMetrics) {
        nuxeoMetrics.putGauge(() -> supplied, "supplied");
        nuxeoMetrics.putGauge(() -> transformed, "transformed");
        nuxeoMetrics.putGauge(() -> errors, "errors");
        nuxeoMetrics.putGauge(() -> filterFailed, "filterFailed");
    }

}
