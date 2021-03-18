/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.metadata.Caption;
import org.nuxeo.ai.services.CaptionService;
import org.nuxeo.ai.transcribe.AudioTranscription;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.listeners.VideoAboutToChange.CAPTIONABLE_FACET;
import static org.nuxeo.ai.metadata.Caption.CAPTIONS_PROP;
import static org.nuxeo.ai.metadata.Caption.VTT_KEY_PROP;
import static org.nuxeo.ai.transcribe.AudioTranscription.Type.PRONUNCIATION;

/**
 * Asynchronous listener for adding captions to a transcribed video
 */
public class DocumentTranscribed implements PostCommitEventListener {

    public static final String LANGUAGE_KEY = "lang";

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Logger log = LogManager.getLogger(DocumentTranscribed.class);

    @Override
    public void handleEvent(EventBundle eventBundle) {
        eventBundle.forEach(this::handleEvent);
    }

    protected void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }
        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        if (!doc.hasFacet(CAPTIONABLE_FACET) || !doc.hasFacet(ENRICHMENT_FACET)) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enrichments = (List<Map<String, Object>>) doc.getProperty(ENRICHMENT_SCHEMA_NAME,
                ENRICHMENT_ITEMS);
        List<Blob> raws = enrichments.stream()
                                     .filter(en -> "aws.transcribe".equals(en.getOrDefault("model", "none")))
                                     .map(en -> (Blob) en.get("raw"))
                                     .collect(Collectors.toList());

        if (raws.isEmpty()) {
            log.debug("Could not find RAW transcription for document id = " + doc.getId());
            return;
        }
        Blob json = raws.get(0);
        if (json == null) {
            return;
        }

        AudioTranscription at;
        try {
            at = OBJECT_MAPPER.readValue(json.getString(), AudioTranscription.class);
        } catch (IOException e) {
            log.error(e);
            return;
        }

        String lang = at.getResults().getLanguageCode();

        List<Element> elements = at.getResults().getItems().stream().map(item -> {
            float st = 0L;
            float et = 0L;
            AudioTranscription.Type type = item.getType();
            if (PRONUNCIATION.equals(type)) {
                st = Float.parseFloat(item.getStartTime());
                et = Float.parseFloat(item.getEndTime());
            }

            String content = item.getContent();
            return new Element((long) (st * 1000), (long) (et * 1000), type, content);
        }).collect(Collectors.toList());

        List<Caption> captions = buildCaptions(elements);

        CaptionService cs = Framework.getService(CaptionService.class);
        Blob blob = cs.write(captions);
        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> caps = (List<Map<String, Serializable>>) doc.getPropertyValue(CAPTIONS_PROP);
        Optional<Map<String, Serializable>> res = caps.stream()
                                                      .filter(cap -> lang.equals(cap.get(LANGUAGE_KEY)))
                                                      .findFirst();

        if (res.isPresent()) {
            Map<String, Serializable> map = res.get();
            int idx = caps.indexOf(map);
            map.put(VTT_KEY_PROP, (Serializable) blob);
            caps.set(idx, map);
        } else {
            Map<String, Serializable> map = new HashMap<>();
            map.put(LANGUAGE_KEY, lang);
            map.put(VTT_KEY_PROP, (Serializable) blob);
            caps.add(map);
        }

        doc.setPropertyValue(CAPTIONS_PROP, (Serializable) caps);
        doc.getCoreSession().saveDocument(doc);
    }

    protected List<Caption> buildCaptions(List<Element> elements) {
        ArrayList<Caption> captions = new ArrayList<>();
        if (elements.isEmpty()) {
            return captions;
        }

        Element first = elements.get(0);
        long st = first.start;
        long et = first.end;
        StringBuilder lineBuilder = new StringBuilder();
        for (Element el : elements) {
            if (el.type == PRONUNCIATION) {
                lineBuilder.append(" ");
            }
            lineBuilder.append(el.content);

            et = el.end > 0 ? el.end : et;
            if (et - st > 5000) {
                Caption caption = new Caption(st, et, Collections.singletonList(lineBuilder.toString()));
                captions.add(caption);
                lineBuilder.setLength(0);
                st = et;
            }
        }

        if (lineBuilder.length() > 0) {
            Caption caption = new Caption(st, et, Collections.singletonList(lineBuilder.toString()));
            captions.add(caption);
        }

        return captions;
    }

    public static class Element {

        protected long start;

        protected long end;

        protected AudioTranscription.Type type;

        protected String content;

        public Element(long start, long end, AudioTranscription.Type type, String content) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.content = content;
        }
    }
}
