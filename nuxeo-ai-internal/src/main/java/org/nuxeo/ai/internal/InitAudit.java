/*
 * (C) Copyright 2018-2020 Nuxeo SA (http://nuxeo.com/).
 *   This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 *   Notice of copyright on this source code does not indicate publication.
 *
 *   Contributors:
 *       Nuxeo
 */

package org.nuxeo.ai.internal;

import static org.nuxeo.ai.internal.InitAudit.ID;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.runtime.api.Framework;

@Operation(id = ID, category = "AICore", label = "Init Audit", description = "Init audit for metrics")
public class InitAudit {

    public static final String ID = "AICore.InitAudit";

    private static final Log log = LogFactory.getLog(InitAudit.class);

    @Context
    protected CoreSession session;

    @Param(name = "modelName")
    protected String modelName;

    @OperationMethod
    public Object run() {
        NuxeoPrincipal nxPrincipal = session.getPrincipal();
        // Requires administrator user
        if (!nxPrincipal.isAdministrator()) {
            return Response.status(404).build();
        }
        AuditBackend audit = Framework.getService(AuditBackend.class);
        if (audit == null) {
            if (log.isDebugEnabled()) {
                log.debug("AuditBackend service unavailable, skipping initialization for model=" + modelName);
            }
            // Return OK since absence of audit should not fail the operation
            return Response.status(200).entity("Audit service unavailable; initialization skipped").build();
        }
        CoreInstance.doPrivileged(session, s -> {
            DocumentModel root = session.getRootDocument();
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, -1);
            Date d1 = cal.getTime();
            Date d2 = new Date();
            int[] backtrack = { -1, 1 };
            String[] autos = new String[] { "AUTO_FILLED", "AUTO_CORRECTED" };
            Random rnd = ThreadLocalRandom.current();
            for (int i = 0; i < 5000; i++) {
                Date randomDate = new Date(ThreadLocalRandom.current().nextLong(d1.getTime(), d2.getTime()));
                storeAudit(audit, root, autos[rnd.nextInt(autos.length)], modelName,
                        backtrack[rnd.nextInt(backtrack.length)], randomDate);
            }
            for (int i = 0; i < 5000; i++) {
                Date randomDate = new Date(ThreadLocalRandom.current().nextLong(d1.getTime(), d2.getTime()));
                storeAudit(audit, root, autos[rnd.nextInt(autos.length)], modelName, 1, randomDate);
            }
        });
        return Response.status(200).build();
    }

    private void storeAudit(AuditBackend audit, DocumentModel doc, String eventName, String model, long value,
            Date date) {
        LogEntry logEntry = LogEntry.builder(eventName, date)
                                    .category("AI")
                                    .docUUID(doc.getId())
                                    .docPath(doc.getPathAsString())
                                    .extended("model", model)
                                    .extended("value", value)
                                    .build();
        audit.addLogEntries(Collections.singletonList(logEntry));
    }
}
