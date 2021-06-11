/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.services;

import java.util.List;
import org.nuxeo.ai.metadata.Caption;
import org.nuxeo.ecm.core.api.Blob;

/**
 * Objects that implement this service must create a {@link Blob} from given {@link Caption}s
 */
public interface CaptionService {

    /**
     * @param captions to use for an appropriate caption format
     * @return {@link Blob} with caption
     */
    Blob write(List<Caption> captions);
}
