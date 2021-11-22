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

package org.nuxeo.ai.similar.content.pipelines;

import static org.nuxeo.ai.similar.content.pipelines.DeduplicationScrollerComputation.SCROLLER_COMPUTATION_NAME;
import static org.nuxeo.ai.similar.content.pipelines.DuplicationPipeline.PIPELINE_NAME;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.similar.content.operation.ProcessDuplicates;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import net.sf.ehcache.util.TimeUtil;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, CoreBulkFeature.class })
@Deploy("org.nuxeo.ai.similar-content")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/cloud-client-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/dedup-config-test.xml")
@RepositoryConfig(cleanup = Granularity.METHOD)
public class DeduplicationPipelineTest {

    @Inject
    protected AutomationService as;

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txf;

    @Test
    public void shouldScrollThroughTuples() throws OperationException, InterruptedException {
        OperationContext ctx = new OperationContext(session);
        as.run(ctx, ProcessDuplicates.ID);

        txf.nextTransaction();

        LogManager manager = Framework.getService(StreamService.class).getLogManager();

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (manager.getLag(PIPELINE_NAME, SCROLLER_COMPUTATION_NAME).lag() > 0) {
            if (System.currentTimeMillis() > deadline) {
                break;
            }

            Thread.sleep(100);
        }
    }
}
