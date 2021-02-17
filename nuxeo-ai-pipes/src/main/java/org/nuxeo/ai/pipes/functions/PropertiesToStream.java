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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.pipes.events.DocEventToStream;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.lib.stream.computation.Record;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static org.nuxeo.ai.pipes.events.DocEventToStream.BLOB_PROPERTIES;
import static org.nuxeo.ai.pipes.events.DocEventToStream.BLOB_PROPERTIES_TYPE;
import static org.nuxeo.ai.pipes.events.DocEventToStream.CUSTOM_PROPERTIES;
import static org.nuxeo.ai.pipes.events.DocEventToStream.TEXT_PROPERTIES;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;

/**
 * A function that takes document properties and sends them to a stream using {@link DocEventToStream}. The function
 * will first run 1 or more <code>Event</code> filters to determine if it should transform the document.
 * <p>
 * Using the three *properties parameters it takes the specified properties and serializes them to a stream
 * <code>Record</code>.
 * <ul>
 * <li>If you specify a list of <code>blobProperties</code> then you will have 1 record per property.
 * <li>If you specify a list of <code>textProperties</code> then you will have 1 record per property.
 * <li>If you specify a list of <code>customProperties</code> then you will have 1 record with all the properties.
 * </ul>
 * <p>
 * The properties are not mutually exclusive. For example, if you specify "file:content" in <code>blobProperties</code>
 * and "dc:title" and "dc:creator" in <code>customProperties</code> you will have 1 record with those 3 properties.
 *
 * @see PreFilterFunction
 */
public class PropertiesToStream extends PreFilterFunction<Event, Collection<Record>> {

    private static final Logger log = LogManager.getLogger(PropertiesToStream.class);

    protected List<PropertyType> blobProperties;

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
        List<String> properties = propsList(options.get(BLOB_PROPERTIES));
        List<String> types = propsList(options.get(BLOB_PROPERTIES_TYPE));
        if (types != null && (properties.size() != types.size())) {
            String msg = log.getMessageFactory()
                            .newMessage("If {} is defined, must be the same size as {};\nproperties: {}\ntypes: {}",
                                    BLOB_PROPERTIES_TYPE, BLOB_PROPERTIES, properties.toArray(), types.toArray())
                            .getFormat();
            throw new NuxeoException(msg);
        }

        blobProperties = new ArrayList<>();
        for (int index = 0; index < properties.size(); index++) {
            String type = (types == null) ? IMAGE_TYPE : types.get(index);
            blobProperties.add(new PropertyType(properties.get(index), type));
        }
        textProperties = propsList(options.get(TEXT_PROPERTIES));
        customProperties = propsList(options.get(CUSTOM_PROPERTIES));
    }

    /**
     * Sets up the transformation function to be applied
     */
    protected Function<Event, Collection<Record>> setupTransformation() {
        Function<Event, Collection<BlobTextFromDocument>> func = new DocEventToStream(blobProperties, textProperties,
                customProperties);
        return func.andThen(items -> items.stream().map(i -> toRecord(i.getKey(), i)).collect(toList()));
    }

}
