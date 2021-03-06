/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.autoconfigure.aad;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.microsoft.aad.adal4j.ClientCredential;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;


public class UserPrincipalTest {
    private AzureADGraphClient graphClientMock;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9519);

    private ClientCredential credential;
    private AADAuthenticationProperties aadAuthProps;
    private ServiceEndpointsProperties endpointsProps;
    private String accessToken;


    @Before
    public void setup() {
        accessToken = Constants.BEARER_TOKEN;
        aadAuthProps = new AADAuthenticationProperties();
        endpointsProps = new ServiceEndpointsProperties();
        final ServiceEndpoints serviceEndpoints = new ServiceEndpoints();
        serviceEndpoints.setAadMembershipRestUri("http://localhost:9519/memberOf");
        endpointsProps.getEndpoints().put("global", serviceEndpoints);
        credential = new ClientCredential("client", "pass");
    }


    @Test
    public void getAuthoritiesByUserGroups() throws Exception {

        aadAuthProps.getUserGroup().setAllowedGroups(Collections.singletonList("group1"));
        this.graphClientMock = new AzureADGraphClient(credential, aadAuthProps, endpointsProps);

        stubFor(get(urlEqualTo("/memberOf")).withHeader("Accept", equalTo("application/json;odata=minimalmetadata"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(Constants.USERGROUPS_JSON)));

        assertThat(graphClientMock.getGrantedAuthorities(Constants.BEARER_TOKEN)).isNotEmpty()
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_group1");

        verify(getRequestedFor(urlMatching("/memberOf")).withHeader("Authorization", equalTo(accessToken))
                .withHeader("Accept", equalTo("application/json;odata=minimalmetadata"))
                .withHeader("api-version", equalTo("1.6")));
    }

    @Test
    public void getGroups() throws Exception {

        aadAuthProps.setActiveDirectoryGroups(Arrays.asList("group1", "group2", "group3"));
        this.graphClientMock = new AzureADGraphClient(credential, aadAuthProps, endpointsProps);

        stubFor(get(urlEqualTo("/memberOf")).withHeader("Accept", equalTo("application/json;odata=minimalmetadata"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(Constants.USERGROUPS_JSON)));
        final Collection<? extends GrantedAuthority> authorities = graphClientMock
                .getGrantedAuthorities(Constants.BEARER_TOKEN);

        assertThat(authorities).isNotEmpty().extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_group1", "ROLE_group2", "ROLE_group3");

        verify(getRequestedFor(urlMatching("/memberOf")).withHeader("Authorization", equalTo(accessToken))
                .withHeader("Accept", equalTo("application/json;odata=minimalmetadata"))
                .withHeader("api-version", equalTo("1.6")));
    }

    @Test
    public void userPrinciplaIsSerializable() throws ParseException, IOException, ClassNotFoundException {
        final File tmpOutputFile = File.createTempFile("test-user-principal", "txt");

        try (final FileOutputStream fileOutputStream = new FileOutputStream(tmpOutputFile);
             final ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
             final FileInputStream fileInputStream = new FileInputStream(tmpOutputFile);
                final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);) {

            final JWSObject jwsObject = JWSObject.parse(Constants.JWT_TOKEN);
            final JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder().subject("fake-subject").build();
            final UserPrincipal principal = new UserPrincipal(jwsObject, jwtClaimsSet);

            objectOutputStream.writeObject(principal);

            final UserPrincipal serializedPrincipal = (UserPrincipal) objectInputStream.readObject();

            Assert.assertNotNull("Serialized UserPrincipal not null", serializedPrincipal);
            Assert.assertTrue("Serialized UserPrincipal kid not empty",
                    !StringUtils.isEmpty(serializedPrincipal.getKid()));
            Assert.assertNotNull("Serialized UserPrincipal claims not null.", serializedPrincipal.getClaims());
            Assert.assertTrue("Serialized UserPrincipal claims not empty.",
                    serializedPrincipal.getClaims().size() > 0);
        } finally {
            Files.deleteIfExists(tmpOutputFile.toPath());
        }
    }
}
