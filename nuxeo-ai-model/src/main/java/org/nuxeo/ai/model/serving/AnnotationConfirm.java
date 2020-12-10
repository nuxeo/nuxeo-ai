package org.nuxeo.ai.model.serving;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;

@Operation(id = AnnotationConfirm.ID, category = Constants.CAT_DOCUMENT, description = "Updated given document with provided properties")
public class AnnotationConfirm {

    public static final String ID = "AI.AnnotationConfirm";

    @Context
    protected CoreSession session;

    @Param(name = "properties")
    protected Properties properties;

    @OperationMethod
    public void run(DocumentModel doc) {
        properties.forEach((key, value) -> {
            Property property = doc.getProperty(key);
            property.setValue(value);
            property.setForceDirty(true);
        });

        session.saveDocument(doc);
    }
}
