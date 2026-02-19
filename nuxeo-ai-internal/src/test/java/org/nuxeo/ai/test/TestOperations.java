/*
 * (C) Copyright 2020 Nuxeo SA (http://nuxeo.com/).
 * This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 * Notice of copyright on this source code does not indicate publication. *
 *
 * Contributors:
 *     Nuxeo
 */
package org.nuxeo.ai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.internal.InitAudit;
import org.nuxeo.ai.internal.InitDatasetDocuments;
import org.nuxeo.audit.api.AuditQueryBuilder;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.audit.test.AuditFeature;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ AuditFeature.class, AutomationFeature.class })
@Deploy({ "org.nuxeo.ai.ai-internal" })
@Deploy({ "org.nuxeo.ecm.platform.picture.core" })
@Deploy({ "org.nuxeo.ecm.platform.tag" })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
public class TestOperations {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Test
    public void iCanInitAudit() throws OperationException {
        AuditBackend audit = Framework.getService(AuditBackend.class);
        AuditQueryBuilder qb = new AuditQueryBuilder();
        qb.predicate(Predicates.eq("category", "AI"));
        List<LogEntry> entriesBefore = audit.queryLogs(qb);
        assertThat(entriesBefore).isEmpty();

        OperationContext ctx = new OperationContext(session);
        ctx.put("modelName", "something");
        Object response = automationService.run(ctx, InitAudit.ID);
        assertThat(response).isNotNull();

        qb = new AuditQueryBuilder();
        qb.predicate(Predicates.eq("category", "AI"));
        List<LogEntry> entriesAfter = audit.queryLogs(qb);
        assertThat(entriesAfter).isNotEmpty();
    }

    @Test
    public void iCanInitDocumentsForDatalake() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        String[] uids = (String[]) automationService.run(ctx, InitDatasetDocuments.ID);
        assertThat(uids).hasSize(30);
    }
}
