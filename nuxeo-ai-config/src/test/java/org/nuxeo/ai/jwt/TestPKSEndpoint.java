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
import static org.assertj.core.api.AssertionsForClassTypes.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.keystore.JWKService;
import org.nuxeo.ai.keystore.KeyPairContainer;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, PlatformFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-jaxrs")
@Deploy("org.nuxeo.ai.ai-config")
public class TestPKSEndpoint extends BaseTest {

    @Inject
    protected JWKService jwk;

    @Test
    public void shouldReachPKS() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyPairContainer kpc = jwk.get();
        assertThat(kpc).isNotNull();
        WebResource webResource = client.resource(getBaseURL())
                                        .path("jwk")
                                        .path("pks")
                                        .path("keystore")
                                        .path(kpc.getKid());

        ClientResponse response = webResource.accept(MediaType.TEXT_PLAIN_TYPE)
                                             .type(MediaType.TEXT_PLAIN)
                                             .get(ClientResponse.class);
        assertThat(response.getStatus()).isEqualTo(200);

        byte[] bytes = null;
        try (InputStream is = response.getEntityInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            while (is.available() > 0) {
                baos.write(is.read());
            }

            bytes = baos.toByteArray();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertThat(bytes).isNotEmpty();

        PublicKey rsa = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        assertThat(rsa).isNotNull();
    }
}
