/*
 * (C) Copyright 2018-2021 Nuxeo SA (http://nuxeo.com/).
 *   This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 *   Notice of copyright on this source code does not indicate publication.
 *
 *   Contributors:
 *       Nuxeo
 */

package org.nuxeo.ai.internal;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.nuxeo.ai.internal.InitDatasetDocuments.ID;
import static org.nuxeo.ecm.core.api.CoreInstance.openCoreSessionSystem;

@Operation(id = ID, category = "Insight", label = "Create the dataset documents")
public class InitDatasetDocuments {

    public static final String ID = "AICore.InitDatasetDocuments";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Object run() throws IOException {
        NuxeoPrincipal nxPrincipal = session.getPrincipal();
        // Requires administrator user
        if (!nxPrincipal.isAdministrator()) {
            return Response.status(404).build();
        }
        String managerName = "manager";
        String managers = "insight-managers";
        Framework.doPrivileged(() -> {
            UserManager um = Framework.getService(UserManager.class);
            DocumentModel groupModel = um.getGroupModel(managers);
            if (groupModel == null) {
                groupModel = um.getBareGroupModel();
                groupModel.setProperty("group", "groupname", managers);
                um.createGroup(groupModel);
            }
            DocumentModel userModel = um.getUserModel(managerName);
            if (userModel == null) {
                userModel = um.getBareUserModel();
                userModel.setProperty("user", "username", managerName);
                userModel.setProperty("user", "password", managerName);
                userModel.setProperty("user", "groups", Collections.singletonList(managers));
                um.createUser(userModel);
            }
        });
        try (CloseableCoreSession userSession = openCoreSessionSystem(session.getRepositoryName(), managerName)) {
            DocumentModel workspace = userSession.createDocumentModel("/default-domain/workspaces", "workspace",
                    "Workspace");
            userSession.createDocument(workspace);
            DocumentModel folder = userSession.createDocumentModel("/default-domain/workspaces/workspace", "folder",
                    "Folder");
            userSession.createDocument(folder);
            // Creating dataset document samples
            Blob image = new FileBlob(this.getClass().getResourceAsStream("/pink.jpg"), "image/jpeg");
            Blob pdf = new FileBlob(this.getClass().getResourceAsStream("/nxp.pdf"), "application/pdf");
            List<Blob> blobs = Arrays.asList(pdf, image);
            // Filling metadata here according to the outputs we will get for missions
            String[] uids = new String[20];
            for (int i = 0; i < 20; i++) {
                DocumentModel document = userSession.createDocumentModel(folder.getPathAsString(), "Example " + i,
                        "ExtraFile");
                document.setPropertyValue("dc:title", "Example " + i);
                document.setPropertyValue("dc:description", "Description " + i);
                document.setPropertyValue("dc:contributors", new String[] { "system", "Administrator" });
                document.setPropertyValue("dc:subjects", new String[] { "art/architecture", "art/danse" });
                document.setPropertyValue("file:content", (Serializable) blobs.get(i & 1));
                document.setPropertyValue("extrafile:docprop", folder.getId());
                uids[i] = userSession.createDocument(document).getId();
            }
            userSession.save();
            return uids;
        }
    }

}
