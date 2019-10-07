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
package org.nuxeo.ai.transcribe;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.nuxeo.ecm.core.api.Blob;

import software.amazon.awssdk.services.transcribestreaming.model.Alternative;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;

/**
 * Service interface intended for Video/Audio transcription
 */
public interface TranscribeService {

    /**
     * Start transcription job for
     * @param blob that contains Video/Audio
     * @param language code from {@link LanguageCode}
     * @return {@link CompletableFuture} that computes a liist of {@link Alternative}
     */
    CompletableFuture<List<Alternative>> transcribe(Blob blob, LanguageCode language);

    List<Alternative> normalize(List<Alternative> alternatives);
}
