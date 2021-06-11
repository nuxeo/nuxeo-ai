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
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import javax.ws.rs.core.Response;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.impl.ExtendedInfoImpl;
import org.nuxeo.runtime.api.Framework;

@Operation(id = ID, category = "AICore", label = "Init Audit", description = "Init audit for metrics")
public class InitAudit {

    public static final String ID = "AICore.InitAudit";

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
        CoreInstance.doPrivileged(session, s -> {
            DocumentModel root = session.getRootDocument();
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, -1);
            Date d1 = cal.getTime();
            Date d2 = new Date();
            int[] backtrack = { -1, 1 };
            String[] autos = new String[] { "AUTO_FILLED", "AUTO_CORRECTED" };
            for (int i = 0; i < 5000; i++) {
                Date randomDate = new Date(ThreadLocalRandom.current().nextLong(d1.getTime(), d2.getTime()));
                storeAudit(root, autos[new Random().nextInt(autos.length)], modelName,
                        backtrack[new Random().nextInt(backtrack.length)], randomDate);
            }
            for (int i = 0; i < 5000; i++) {
                Date randomDate = new Date(ThreadLocalRandom.current().nextLong(d1.getTime(), d2.getTime()));
                storeAudit(root, autos[new Random().nextInt(autos.length)], modelName, 1, randomDate);
            }
        });
        return Response.status(200).build();
    }

    private void storeAudit(DocumentModel doc, String eventName, String model, long value, Date date) {
        AuditLogger audit = Framework.getService(AuditLogger.class);
        LogEntry logEntry = audit.newLogEntry();
        logEntry.setCategory("AI");
        logEntry.setEventId(eventName);
        logEntry.setDocUUID(doc.getId());
        logEntry.setDocPath(doc.getPathAsString());
        logEntry.setEventDate(date);

        ExtendedInfoImpl.StringInfo modelInfo = new ExtendedInfoImpl.StringInfo(model);
        ExtendedInfoImpl.LongInfo one = new ExtendedInfoImpl.LongInfo(value);

        HashMap<String, ExtendedInfo> infos = new HashMap<>();
        infos.put("model", modelInfo);
        infos.put("value", one);
        logEntry.setExtendedInfos(infos);

        audit.addLogEntries(Collections.singletonList(logEntry));
    }
}
