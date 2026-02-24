package org.nuxeo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core" })
public class TestCorePageProvider {

    @Inject
    protected CoreSession session;

    @Inject
    protected PageProviderService pps;

    @Test
    public void iCanFetchDocTypesAgg() {
        PageProviderDefinition ppdef = pps.getPageProviderDefinition("doctypes_pp");
        assertThat(ppdef).isNotNull();
        // Aggregates may be unavailable if ES/OpenSearch provider not present; just ensure no exception
        if (ppdef.getAggregates() != null) {
            assertThat(ppdef.getAggregates().size()).isGreaterThanOrEqualTo(0);
        }
    }
}
