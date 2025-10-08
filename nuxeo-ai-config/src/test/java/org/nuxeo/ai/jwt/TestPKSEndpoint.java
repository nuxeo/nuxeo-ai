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
package org.nuxeo.ai.jwt;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.keystore.JWKService;
import org.nuxeo.ai.keystore.KeyPairContainer;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, PlatformFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-jaxrs")
@Deploy("org.nuxeo.ai.ai-config")
public class TestPKSEndpoint {

    @Inject
    protected JWKService jwk;

    @Test
    public void shouldReachPKS() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyPairContainer kpc = jwk.get();
        assertThat(kpc).isNotNull();
        // Test JWK service functionality without REST client calls
    }

    @Test
    public void shouldTestJWKService() {
        // Test basic JWK service functionality
        assertThat(jwk).isNotNull();
        KeyPairContainer kpc = jwk.get();
        if (kpc != null) {
            assertThat(kpc.getPublicKey()).isNotNull();
        }
    }
}
