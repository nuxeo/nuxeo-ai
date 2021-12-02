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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;
import static org.nuxeo.ai.similar.content.pipelines.IndexAction.INDEX_ACTION_NAME;
import static org.nuxeo.ai.similar.content.pipelines.IndexAction.XPATH_PARAM;

import java.io.Serializable;
import java.time.Duration;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, CoreBulkFeature.class })
@Deploy("org.nuxeo.ai.similar-content")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/cloud-client-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/dedup-config-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/disable-dedup-listener.xml")
@RepositoryConfig(cleanup = Granularity.METHOD)
public class IndexActionTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected BulkService bs;

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txf;

    @Test
    public void shouldRunIndexBAF() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            DocumentModel doc = session.createDocumentModel("/", "test_file_" + i, "File");
            if (i % 2 == 0) {
                doc.setPropertyValue(FILE_CONTENT, (Serializable) Blobs.createBlob("Text blob content #" + i));
            }
            doc = session.createDocument(doc);
        }

        txf.nextTransaction();

        BulkCommand command = new BulkCommand.Builder(INDEX_ACTION_NAME,
                "SELECT * FROM Document WHERE ecm:primaryType = 'File'").user(session.getPrincipal().getActingUser())
                                                                        .repository(session.getRepositoryName())
                                                                        .param(XPATH_PARAM, FILE_CONTENT)
                                                                        .build();
        String bafId = bs.submit(command);

        txf.nextTransaction();
        assertThat(bs.await(bafId, Duration.ofSeconds(30))).isTrue();

        txf.nextTransaction();
        BulkStatus status = bs.getStatus(bafId);
        assertThat(status.getErrorCount()).isEqualTo(10);
        assertThat(status.getProcessed()).isEqualTo(20);
    }
}
