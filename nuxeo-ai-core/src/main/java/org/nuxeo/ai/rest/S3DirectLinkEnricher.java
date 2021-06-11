/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.rest;

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.net.URI;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolderAdapterService;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.io.download.DefaultRedirectResolver;
import org.nuxeo.ecm.core.io.download.RedirectResolver;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.core.JsonGenerator;

@Setup(mode = SINGLETON, priority = REFERENCE)
public class S3DirectLinkEnricher extends AbstractJsonEnricher<DocumentModel> {

    public static final String NAME = "s3directlink";

    public S3DirectLinkEnricher() {
        super(NAME);
    }

    @Override
    public void write(JsonGenerator jsonGenerator, DocumentModel documentModel) throws IOException {
        RedirectResolver redirectResolver = new DefaultRedirectResolver();
        BlobHolderAdapterService blobHolderAdapterService = Framework.getService(BlobHolderAdapterService.class);
        Blob blob = blobHolderAdapterService.getBlobHolderAdapter(documentModel, "download").getBlob();
        URI uri = redirectResolver.getURI(blob, BlobManager.UsageHint.DOWNLOAD, null);
        if (uri != null) {
            jsonGenerator.writeObjectFieldStart(NAME);
            jsonGenerator.writeObjectField("url", uri.toString());
            jsonGenerator.writeEndObject();
        }
    }
}
