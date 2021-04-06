/*
 * (C) Copyright 2020 Nuxeo SA (http://nuxeo.com/).
 * This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 * Notice of copyright on this source code does not indicate publication. *
 *
 * Contributors:
 *     Nuxeo
 */
package org.nuxeo.ai.test;

import com.sun.jersey.core.spi.factory.ResponseImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.internal.InitAudit;
import org.nuxeo.ai.internal.InitDatasetDocuments;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.platform.audit.api.AuditQueryBuilder;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.nuxeo.ecm.platform.audit.api.BuiltinLogEntryData.LOG_CATEGORY;
import static org.nuxeo.ecm.platform.audit.api.BuiltinLogEntryData.LOG_EVENT_ID;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, RuntimeFeature.class, AuditFeature.class })
@Deploy({ "org.nuxeo.ai.ai-internal" })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
public class TestOperations {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected AuditReader auditReader;

    @Test
    public void iCanInitAudit() throws OperationException {
        AuditQueryBuilder qb = new AuditQueryBuilder();
        Predicate predicate = Predicates.eq(LOG_CATEGORY, "AI");
        qb.predicate(predicate).and(Predicates.eq(LOG_EVENT_ID, "AUTO_FILLED"));
        List<LogEntry> logEntries = auditReader.queryLogs(qb);
        assertThat(logEntries.size()).isZero();
        OperationContext ctx = new OperationContext(session);
        ctx.put("modelName", "something");
        ResponseImpl response = (ResponseImpl) automationService.run(ctx, InitAudit.ID);
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        logEntries = auditReader.queryLogs(qb);
        assertThat(logEntries.size()).isNotZero();
    }

    @Test
    public void iCanInitDocumentsForDatalake() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        String[] uids = (String[]) automationService.run(ctx, InitDatasetDocuments.ID);
        assertThat(uids).hasSize(20);
    }
}
