package com.soprasteria.initiatives.auth.service;

import com.soprasteria.initiatives.auth.domain.User;
import com.soprasteria.initiatives.auth.utils.UrlUtils;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.OAuth2ClientProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.StringJoiner;

/**
 * Business service to obtain OAuth2 token
 *
 * @author jntakpe
 */
@Service
public class TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenService.class);

    private static final String AUTHORIZATION_HEADER_KEY = "Authorization";

    private static final String BASIC_PREFIX = "Basic ";

    private static final String OAUTH2_TOKEN_URL = "/oauth/token";

    private static final String TEST_URL = "http://localhost/";

    private static final String DEFAULT_URL = "http://localhost:";

    private final Environment environment;

    private final OAuth2ClientProperties oAuth2ClientProperties;

    @Autowired
    public TokenService(Environment environment, OAuth2ClientProperties oAuth2ClientProperties) {
        this.environment = environment;
        this.oAuth2ClientProperties = oAuth2ClientProperties;
    }

    public ResponseEntity<OAuth2AccessToken> authorize(User user, String requestUrl) {
        LOGGER.info("Getting access token for user : {}", user);
        try {
            return new RestTemplate().postForEntity(url(user, requestUrl), new HttpEntity(initializeHeaders()), OAuth2AccessToken.class);
        } catch (HttpClientErrorException e) {
            LOGGER.warn("Unable to obtain token {}", e);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    private String url(User user, String requestUrl) {
        String serverAddress;
        if (requestUrl.startsWith(TEST_URL)) {
            serverAddress = DEFAULT_URL + this.environment.getProperty("server.port");
        } else {
            serverAddress = UrlUtils.getServerAdressFromRequest(requestUrl);
        }
        return UriComponentsBuilder.fromHttpUrl(serverAddress + OAUTH2_TOKEN_URL).queryParams(requestParams(user)).toUriString();
    }

    private MultiValueMap<String, String> requestParams(User user) {
        MultiValueMap<String, String> requestParams = new LinkedMultiValueMap<>();
        requestParams.add("username", user.getUsername());
        requestParams.add("password", user.getPassword());
        requestParams.add("grant_type", "password");
        requestParams.add("scope", "openid");
        requestParams.add("client_id", oAuth2ClientProperties.getClientId());
        requestParams.add("secret", oAuth2ClientProperties.getClientSecret());
        return requestParams;
    }

    private MultiValueMap<String, String> initializeHeaders() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(AUTHORIZATION_HEADER_KEY, BASIC_PREFIX + base64ClientIdSecret());
        headers.add("Content-Type", "application/json");
        return headers;
    }

    private String base64ClientIdSecret() {
        String clientIdSecret = new StringJoiner(":")
                .add(oAuth2ClientProperties.getClientId())
                .add(oAuth2ClientProperties.getClientSecret()).toString();
        return Base64.encodeBase64String(clientIdSecret.getBytes());
    }
}