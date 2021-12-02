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
package org.nuxeo.ai.similar.content.services;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;
import static org.nuxeo.ai.similar.content.DedupConstants.DEDUPLICATION_FACET;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
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
public class SimilarContentServiceTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected SimilarContentService scs;

    @Inject
    protected BulkService bs;

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txf;

    @Test
    public void shouldContainConfiguration() {
        String query = scs.getQuery("test");
        assertThat(query).isNotEmpty();

        String xpath = scs.getXPath("test");
        assertThat(xpath).isNotEmpty();
    }

    @Test
    public void shouldFilterDocuments() {
        DocumentModel fileDoc = session.createDocumentModel("/", "TestFile", "File");
        fileDoc = session.createDocument(fileDoc);
        session.save();

        assertThat(fileDoc).isNotNull();

        assertThat(scs.test("test", fileDoc)).isTrue();
        assertThat(scs.test("test2", fileDoc)).isFalse();

        assertThat(scs.test("test3", fileDoc)).isTrue();
        assertThat(scs.test("test4", fileDoc)).isFalse();
    }

    @Test
    public void shouldIndexDocuments() throws InterruptedException {
        List<String> docIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            DocumentModel doc = session.createDocumentModel("/", "test_file_" + i, "File");
            doc.setPropertyValue(FILE_CONTENT, (Serializable) Blobs.createBlob("Text blob content #" + i));
            doc = session.createDocument(doc);
            docIds.add(doc.getId());
        }

        txf.nextTransaction();

        String query = "SELECT * FROM Document WHERE ecm:primaryType = 'File'";
        String id = scs.index(query, session.getPrincipal().getActingUser(), true);
        assertThat(bs.await(id, Duration.ofSeconds(30))).isTrue();
        BulkStatus status = bs.getStatus(id);
        assertThat(status.getErrorCount()).isEqualTo(0);
        assertThat(status.getProcessed()).isEqualTo(20);

        txf.nextTransaction();

        List<String> allHaveFacet = docIds.stream()
                                          .filter(docId -> session.getDocument(new IdRef(docId))
                                                    .getFacets()
                                                    .contains(DEDUPLICATION_FACET)).collect(Collectors.toList());
        assertThat(allHaveFacet).hasSize(20);

        for (int i = 20; i < 40; i++) {
            DocumentModel doc = session.createDocumentModel("/", "test_file_" + i, "File");
            doc.setPropertyValue(FILE_CONTENT, (Serializable) Blobs.createBlob("Text blob content #" + i));
            doc = session.createDocument(doc);
            docIds.add(doc.getId());
        }

        txf.nextTransaction();

        id = scs.index(query, session.getPrincipal().getActingUser(), false);
        assertThat(bs.await(id, Duration.ofSeconds(30))).isTrue();
        status = bs.getStatus(id);
        assertThat(status.getErrorCount()).isEqualTo(0);
        assertThat(status.getProcessed()).isEqualTo(20);

        txf.nextTransaction();

        assertThat(docIds).hasSize(40);
        allHaveFacet = docIds.stream()
                             .filter(docId -> session.getDocument(new IdRef(docId))
                                                     .getFacets()
                                                     .contains(DEDUPLICATION_FACET)).collect(Collectors.toList());
        assertThat(allHaveFacet).hasSize(40);
    }
}