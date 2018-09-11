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
package org.nuxeo.ai.bulk;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.stream.pipes.streams.Initializable;

@XObject("recordWriter")
public class RecordWriterDescriptor {

    @XNode("@class")
    protected Class<? extends RecordWriter> writer;

    @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> options = new HashMap<>();

    @XNodeList(value = "name", componentType = String.class, type = HashSet.class)
    protected Set<String> names;

    public Set<String> getNames() {
        return names;
    }

    public RecordWriter getWriter(String name) {
        try {

            RecordWriter writerImpl = writer.getDeclaredConstructor(String.class).newInstance(name);
            if (writerImpl instanceof Initializable) {
                ((Initializable) writerImpl).init(options);
            }
            return writerImpl;
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException("RecordWriterDescriptor must define a valid RecordWriter", e);
        }
    }
}
