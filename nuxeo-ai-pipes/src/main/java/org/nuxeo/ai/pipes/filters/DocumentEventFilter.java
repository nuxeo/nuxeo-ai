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
package org.nuxeo.ai.pipes.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ai.pipes.filters.Filter.EventFilter;

/**
 * Filters document events based on an <code>Event</code> or <code>DocumentModel</code> <code>Predicate</code>
 */
public class DocumentEventFilter implements EventFilter {

    protected final Predicate<Event> eventPredicate;
    protected final Predicate<DocumentModel> docPredicate;

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

        protected List<Predicate<DocumentModel>> documentFilters = new ArrayList<>();
        protected List<Predicate<Event>> eventFilters = new ArrayList<>();

        public Builder withDocumentFilter(DocumentFilter filter) {
            documentFilters.add(filter);
            return this;
        }

        public Builder withEventFilter(EventFilter filter) {
            eventFilters.add(filter);
            return this;
        }

        public EventFilter build() {
            Predicate<Event> eventFilter = eventFilters.stream().reduce(Objects::nonNull, Predicate::and);
            Predicate<DocumentModel> docFilter = documentFilters.stream().reduce(Objects::nonNull, Predicate::and);

            return new DocumentEventFilter(eventFilter, docFilter);
        }

    }
}
