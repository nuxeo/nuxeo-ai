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

import static org.nuxeo.runtime.stream.pipes.events.DocEventToStream.BLOB_PROPERTIES;
import static org.nuxeo.runtime.stream.pipes.events.DocEventToStream.CUSTOM_PROPERTIES;
import static org.nuxeo.runtime.stream.pipes.events.DocEventToStream.TEXT_PROPERTIES;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.stream.pipes.events.DocEventToStream;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

/**
 * A function that takes document properties and sends them to a stream using {@link DocEventToStream}.
 * The function will first run 1 or more <code>Event</code> filters to determine if it should transform the document.
 * <p>
 * Using the three *properties parameters it takes the specified properties and serializes them to a stream <code>Record</code>.
 * <ul>
 * <li>If you specify a list of <code>blobProperties</code> then you will have 1 record per property.
 * <li>If you specify a list of <code>textProperties</code> then you will have 1 record per property.
 * <li>If you specify a list of <code>customProperties</code> then you will have 1 record with all the properties.
 * </ul>
 * <p>
 * The properties are not mutually exclusive. For example, if you specify "file:content" in <code>blobProperties</code>
 * and "dc:title" and "dc:creator" in <code>customProperties</code> you will have 1 record with those 3 properties.
 * @see PreFilterFunction
 */
public class PropertiesToStream extends PreFilterFunction<Event, Collection<Record>> {

    protected List<String> blobProperties;
    protected List<String> textProperties;
    protected List<String> customProperties;

    public PropertiesToStream() {
        super();
    }

    @Override
    public void init(Map<String, String> options) {
        processOptions(options);
        this.transformation = setupTransformation();
    }

    /**
     * Handles the provided options and sets up the class
     */
    protected void processOptions(Map<String, String> options) {
        blobProperties = propsList(options.get(BLOB_PROPERTIES));
        textProperties = propsList(options.get(TEXT_PROPERTIES));
        customProperties = propsList(options.get(CUSTOM_PROPERTIES));
    }

    /**
     * Sets up the transformation function to be applied
     */
    protected Function<? super Event, ? extends Collection<Record>> setupTransformation() {
        Function<Event, Collection<BlobTextStream>> func =
                new DocEventToStream(blobProperties, textProperties, customProperties);
        return e -> {
            Collection<BlobTextStream> items = func.apply(e);
            return items.stream().map(i -> toRecord(i.getKey(), i)).collect(Collectors.toList());
        };
    }

}
