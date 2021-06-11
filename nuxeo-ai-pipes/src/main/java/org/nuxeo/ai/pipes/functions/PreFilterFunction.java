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
package org.nuxeo.ai.pipes.functions;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * A function that first applies a predicate filter.
 */
public class PreFilterFunction<T, R> implements Function<T, R>, Initializable {

    private static final Log log = LogFactory.getLog(PreFilterFunction.class);

    protected Predicate<? super T> filter;

    protected Function<? super T, ? extends R> transformation;

    public PreFilterFunction() {

    }

    protected PreFilterFunction(Predicate<? super T> filter, Function<? super T, ? extends R> transformation) {
        this.filter = filter;
        this.transformation = transformation;
    }

    @Override
    public void init(Map<String, String> options) {
        if (log.isDebugEnabled()) {
            log.debug("Initializing function " + this.getClass().getSimpleName());
        }
    }

    public void setFilter(Predicate<? super T> filter) {
        this.filter = filter;
    }

    @Override
    public R apply(T in) {
        try {
            if (filter == null || filter.test(in)) {
                return transformation.apply(in);
            }
        } catch (NullPointerException | ClassCastException cce) {
            log.error("Invalid function definition ", cce);
        } catch (NuxeoException ne) {
            log.error("Nuxeo error from function ", ne);
        }
        return null;
    }
}
