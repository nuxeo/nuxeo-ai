package org.nuxeo.ai.model.serving;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ai.bulk.BaseBulkEnrich;
import org.nuxeo.ai.bulk.BulkEnrichmentAction;
import org.nuxeo.ai.bulk.BulkRemoveEnrichmentAction;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkCommand.Builder;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.util.List;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.nuxeo.ai.bulk.BulkRemoveEnrichmentAction.PARAM_MODEL;
import static org.nuxeo.ai.enrichment.TestConfiguredStreamProcessors.waitForNoLag;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, AutomationFeature.class, PlatformFeature.class, CoreBulkFeature.class,
        RepositoryElasticSearchFeature.class })
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.ai-core")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy({ "org.nuxeo.ai.ai-core:OSGI-INF/recordwriter-test.xml", "org.nuxeo.ai.ai-model:OSGI-INF/bulk-test.xml" })
@Deploy("org.nuxeo.elasticsearch.core.test:elasticsearch-test-contrib.xml")
public class AnnotationConfirmTest extends BaseBulkEnrich {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(5089);

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected TransactionalFeature txf;

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldNotBacktrackConfirmedValue() throws Exception {
        DocumentModel testRoot = setupTestData();
        String repo = session.getRepositoryName();
        String principal = session.getPrincipal().getName();

        String nxql = format("SELECT * from Document WHERE ecm:parentId='%s' AND ecm:primaryType = 'File'",
                testRoot.getId());
        BulkCommand command = new Builder(BulkEnrichmentAction.ACTION_NAME, nxql).user(principal)
                                                                                 .repository(repo)
                                                                                 .build();
        submitAndAssert(command);

        LogManager manager = Framework.getService(StreamService.class).getLogManager("bulk");
        waitForNoLag(manager, "enrichment.in", "enrichment.in$SaveEnrichmentFunction", ofSeconds(5));
        txf.nextTransaction();

        List<DocumentModel> docs = getSomeDocuments(nxql);
        assertThat(docs).isNotEmpty();

        DocumentModel workingDoc = docs.get(0);
        assertThat((String) workingDoc.getPropertyValue("dc:title")).isEqualTo("you");
        List<AutoHistory> autoHistories = getAutoHistories(workingDoc);
        assertThat(autoHistories).hasSize(2);

        AutoHistory onTitle = autoHistories.stream()
                                           .filter(h -> h.getProperty().equals("dc:title"))
                                           .findFirst()
                                           .orElse(null);
        assertThat(onTitle).isNotNull();

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(workingDoc);
        Properties props = new Properties();
        props.put("dc:title", "you");
        ctx.put("properties", props);
        automationService.run(ctx, AnnotationConfirm.ID);
        txf.nextTransaction();

        BulkCommand removed = new Builder(BulkRemoveEnrichmentAction.ACTION_NAME, nxql).user(principal)
                                                                                       .repository(repo)
                                                                                       .param(PARAM_MODEL,
                                                                                               "descBulkModel")
                                                                                       .build();
        submitAndAssert(removed);

        workingDoc = session.getDocument(workingDoc.getRef());
        assertThat(workingDoc.getPropertyValue("dc:title")).isEqualTo("you");
    }
}
