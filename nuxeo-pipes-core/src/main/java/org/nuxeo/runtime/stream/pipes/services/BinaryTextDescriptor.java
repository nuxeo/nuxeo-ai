/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.runtime.stream.pipes.services;

import static org.nuxeo.ecm.core.api.AbstractSession.BINARY_TEXT_SYS_PROP;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BINARYTEXT_UPDATED;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

@XObject("text")
public class BinaryTextDescriptor {

    @XNode("@eventName")
    public String eventName = BINARYTEXT_UPDATED;

    @XNode("@propertyName")
    public String propertyName = BINARY_TEXT_SYS_PROP;

    /**
     * Size in Seconds
     */
    @XNode("@windowSize")
    public int windowSize = -1;

    @XNode("consumer")
    protected PipeDescriptor.Consumer consumer;
}
