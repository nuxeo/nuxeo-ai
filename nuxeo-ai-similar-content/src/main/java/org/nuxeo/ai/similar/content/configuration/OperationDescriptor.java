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

package org.nuxeo.ai.similar.content.configuration;

import java.util.Objects;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.Descriptor;

@XObject("deduplication-operation")
public class OperationDescriptor implements Descriptor {

    @XNode("@class")
    protected Class<?> operationClass;

    @Override
    public String getId() {
        Objects.requireNonNull(operationClass);
        if (!operationClass.isAnnotationPresent(Operation.class)) {
            throw new NuxeoException(
                    operationClass.getName() + " should be annotated with " + Operation.class.getSimpleName());
        }

        Operation annotation = operationClass.getAnnotation(Operation.class);
        return annotation.id();
    }
}
