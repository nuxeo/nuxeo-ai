/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */
package org.nuxeo.ai.similar.content.operation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.nuxeo.ai.similar.content.pipelines.DuplicationPipeline.PIPELINE_NAME;

import java.io.Externalizable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogAppender;
import org.nuxeo.lib.stream.log.Name;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;

@Operation(id = ProcessDuplicates.ID, label = "Schedules Deduplication using Insight", description = "Operation submits a new deduplication task into Nuxeo Stream")
public class ProcessDuplicates {

    private static final Logger log = LogManager.getLogger(ProcessDuplicates.class);

    public static final String ID = "Insight.ProcessDuplicates";

    @Context
    protected CoreSession session;

    @OperationMethod
    public void run() {
        byte[] bytes = session.getPrincipal().getActingUser().getBytes(UTF_8);
        org.nuxeo.lib.stream.log.LogManager manager = Framework.getService(StreamService.class).getLogManager();

        Record record = Record.of(session.getRepositoryName(), bytes);
        LogAppender<Externalizable> appender = manager.getAppender(Name.ofUrn(PIPELINE_NAME));
        appender.append(session.getRepositoryName(), record);
    }
}
