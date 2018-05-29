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
package org.nuxeo.ai.enrichment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;

@XObject("enrichment")
public class EnrichmentDescriptor {

    public static final long DEFAULT_MAX_SIZE = 5_000_000L;

    @XNode("@name")
    public String name;

    @XNode("@kind")
    public String kind;

    @XNode("@enabled")
    protected boolean enabled = true;

    @XNode("@class")
    protected Class<? extends EnrichmentService> service;

    @XNode("@blobProviderId")
    protected String blobProviderId;

    @XNode("@maxSize")
    protected long maxSize = DEFAULT_MAX_SIZE;

    @XNodeList(value = "encoding", type = ArrayList.class, componentType = String.class)
    protected List<String> encoding = new ArrayList<>(0);

    @XNodeList(value = "mimeTypes/mimeType", type = ArrayList.class, componentType = MimeType.class)
    protected List<MimeType> mimeTypes = new ArrayList<>(0);

    @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
    public Map<String, String> options = new HashMap<>();

    public List<MimeType> getMimeTypes() {
        return mimeTypes;
    }

    public String getKind() {
        return kind;
    }

    public String getBlobProviderId() {
        return blobProviderId;
    }

    public EnrichmentService getService() {
        try {
            EnrichmentService serviceInstance = service.newInstance();
            serviceInstance.init(this);
            return serviceInstance;
        } catch (IllegalAccessException | NullPointerException | InstantiationException e) {
            throw new NuxeoException(String.format("EnrichmentDescriptor for %s must define a valid EnrichmentService", name), e);
        }
    }

    @XObject(value = "mimeType")
    public static class MimeType {

        @XNode("@name")
        public String name;

        @XNode("@normalized")
        public boolean normalized = false;
    }
}
