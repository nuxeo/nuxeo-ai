package org.nuxeo.ai;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy({ "org.nuxeo.elasticsearch.core", "org.nuxeo.ai.ai-core" })
public class TestCorePageProvider {

    @Inject
    protected CoreSession session;

    @Inject
    protected PageProviderService pps;

    @Test
    public void iCanFetchDocTypesAgg() {
        PageProviderDefinition ppdef = pps.getPageProviderDefinition("doctypes_pp");
        assertThat(ppdef.getAggregates()).hasSize(1);
    }
}
