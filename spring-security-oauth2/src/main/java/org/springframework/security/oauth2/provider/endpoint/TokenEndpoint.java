/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.oauth2.provider.endpoint;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.UnsupportedGrantTypeException;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author Dave Syer
 * 
 */
@Controller
public class TokenEndpoint extends AbstractEndpoint {

	@RequestMapping(value = "/oauth/token")
	public ResponseEntity<OAuth2AccessToken> getAccessToken(Principal principal, @RequestParam("grant_type") String grantType,
			@RequestParam Map<String, String> parameters, @RequestHeader HttpHeaders headers) {

		if (!(principal instanceof UsernamePasswordAuthenticationToken)) {
			throw new InsufficientAuthenticationException(
					"There is no client authentication. Try adding an appropriate authentication filter.");
		}

		UsernamePasswordAuthenticationToken client = (UsernamePasswordAuthenticationToken) principal;
		if (!client.isAuthenticated()) {
			throw new InsufficientAuthenticationException("The client is not authenticated.");
		}
		String clientId = client.getName();
		// TODO: shouldn't be necessary
		String clientSecret = client.getCredentials() == null ? null : client.getCredentials().toString();

		Set<String> scope = OAuth2Utils.parseScope(parameters.get("scope"));

		OAuth2AccessToken token = getTokenGranter().grant(grantType, parameters, clientId, clientSecret, scope);
		if (token == null) {
			throw new UnsupportedGrantTypeException("Unsupported grant type: " + grantType);
		}

		return getResponse(token);

	}

	private ResponseEntity<OAuth2AccessToken> getResponse(OAuth2AccessToken accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Cache-Control", "no-store");
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new ResponseEntity<OAuth2AccessToken>(accessToken, headers, HttpStatus.OK);
	}

}
