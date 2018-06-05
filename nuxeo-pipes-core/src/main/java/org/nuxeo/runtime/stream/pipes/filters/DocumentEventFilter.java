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
package org.nuxeo.runtime.stream.pipes.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.stream.pipes.filters.Filter.EventFilter;

/**
 * Filters document events based on an <code>Event</code> or <code>DocumentModel</code> <code>Predicate</code>
 */
public class DocumentEventFilter implements EventFilter {

    final Predicate<Event> eventPredicate;
    final Predicate<DocumentModel> docPredicate;

    protected DocumentEventFilter(Predicate<Event> eventPredicate, Predicate<DocumentModel> docPredicate) {
        this.eventPredicate = eventPredicate;
        this.docPredicate = docPredicate;
    }

    @Override
    public boolean test(Event event) {
        if (eventPredicate.test(event)) {
            DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
            if (docCtx == null) {
                return false;
            }
            DocumentModel doc = docCtx.getSourceDocument();
            return doc != null && test(doc);
        }
        return false;
    }

    /**
     * Evaluates this predicate on the given <code>DocumentModel</code> argument.
     */
    public boolean test(DocumentModel documentModel) {
        return docPredicate.test(documentModel);
    }

    public static class Builder {

        List<DocumentFilter> documentFilters = new ArrayList<>();
        List<EventFilter> eventFilters = new ArrayList<>();

        public Builder() {
            withEventFilter(Objects::nonNull);
            withDocumentFilter(Objects::nonNull);
        }

        public Builder withDocumentFilter(DocumentFilter filter) {
            documentFilters.add(filter);
            return this;
        }

        public Builder withEventFilter(EventFilter filter) {
            eventFilters.add(filter);
            return this;
        }

        protected Predicate<Event> buildEventPredicate(Predicate<Event> predicate, int index) {
            Predicate<Event> filter = eventFilters.get(index);
            return predicate.and(filter);
        }

        protected Predicate<DocumentModel> buildDocPredicate(Predicate<DocumentModel> predicate, int index) {
            Predicate<DocumentModel> filter = documentFilters.get(index);
            return predicate.and(filter);
        }

        public EventFilter build() {
            Predicate<Event> eventFilter = eventFilters.get(0);
            for (int i = 1; i < eventFilters.size(); i++) {
                eventFilter = buildEventPredicate(eventFilter, i);
            }

            Predicate<DocumentModel> filter = documentFilters.get(0);
            for (int i = 1; i < documentFilters.size(); i++) {
                filter = buildDocPredicate(filter, i);
            }

            return new DocumentEventFilter(eventFilter, filter);
        }

    }
}
