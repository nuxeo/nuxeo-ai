/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.model.export;

import static org.nuxeo.ai.listeners.ContinuousExportListener.FORCE_EXPORT;
import static org.nuxeo.ai.listeners.ContinuousExportListener.START_CONTINUOUS_EXPORT;
import static org.nuxeo.ecm.automation.core.Constants.CAT_LOCAL_CONFIGURATION;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.runtime.api.Framework;

@Operation(id = StartExport.ID, category = CAT_LOCAL_CONFIGURATION, label = "Start Export", description = "Start Export Operation")
public class StartExport {

    public static final String ID = "AI.StartExport";

    @Context
    protected CoreSession session;

    @OperationMethod
    public void run() {
        EventContextImpl evctx = new EventContextImpl(session, session.getPrincipal());
        evctx.setProperty(FORCE_EXPORT, true);
        Framework.getService(EventProducer.class).fireEvent(evctx.newEvent(START_CONTINUOUS_EXPORT));
    }
}
