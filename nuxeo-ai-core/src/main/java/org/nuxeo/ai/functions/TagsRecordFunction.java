package org.nuxeo.ai.functions;

import static org.nuxeo.runtime.stream.pipes.events.JacksonUtil.fromRecord;

import java.util.function.Consumer;

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessorTopology;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * A stream processor that saves enrichment labels as tags.
 */
public class TagsRecordFunction implements FunctionStreamProcessorTopology {

    protected final EnrichmentTagsConsumer consumer;

    public TagsRecordFunction() {
        consumer = new EnrichmentTagsConsumer();
    }

    @Override
    public Record apply(Record record) {
        EnrichmentMetadata metadata = fromRecord(record, EnrichmentMetadata.class);
        consumer.accept(metadata);
        return null;
    }

    /**
     * Adds enrichment labels as tags.
     */
    public static class EnrichmentTagsConsumer implements Consumer<EnrichmentMetadata> {

        protected final TagService tagService;

        public EnrichmentTagsConsumer() {
            tagService = Framework.getService(TagService.class);
        }

        @Override
        public void accept(EnrichmentMetadata enrichmentMetadata) {
            TransactionHelper.runInTransaction(() -> {
                //TODO: get Repo, perhaps from EnrichmentMetadata.repoName
                CoreInstance.doPrivileged((String) null, session -> {
                    enrichmentMetadata.getLabels().forEach(l -> {
                        tagService.tag(session, enrichmentMetadata.getTargetDocumentRef(), l.getName());
                    });
                });

            });
        }
    }
}
