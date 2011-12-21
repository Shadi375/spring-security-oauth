package org.springframework.security.oauth2.client.token;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.oauth2.client.resource.OAuth2AccessDeniedException;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.auth.ClientAuthenticationHandler;
import org.springframework.security.oauth2.client.token.auth.DefaultClientAuthenticationHandler;
import org.springframework.security.oauth2.common.DefaultOAuth2SerializationService;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2SerializationService;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Base support logic for obtaining access tokens.
 * 
 * @author Ryan Heaton
 * @author Dave Syer
 */
public abstract class OAuth2AccessTokenSupport implements InitializingBean {

	protected final Log logger = LogFactory.getLog(getClass());

	private static final FormHttpMessageConverter FORM_MESSAGE_CONVERTER = new FormHttpMessageConverter();

	private final RestTemplate restTemplate;

	private List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();

	private final OAuth2AccessTokenMessageConverter formAccessTokenMessageConverter = new OAuth2AccessTokenMessageConverter();

	private final OAuth2ErrorMessageConverter formErrorMessageConverter = new OAuth2ErrorMessageConverter();

	private OAuth2SerializationService serializationService = new DefaultOAuth2SerializationService();

	private ClientAuthenticationHandler authenticationHandler = new DefaultClientAuthenticationHandler();

	protected OAuth2AccessTokenSupport() {
		this.restTemplate = new RestTemplate();
		this.restTemplate.setErrorHandler(new AccessTokenErrorHandler());
		this.restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
			@Override
			protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
				super.prepareConnection(connection, httpMethod);
				connection.setInstanceFollowRedirects(false);
			}
		});
		setMessageConverters(this.restTemplate.getMessageConverters());
	}

	public void afterPropertiesSet() throws Exception {
		Assert.notNull(restTemplate, "A RestTemplate is required.");
		Assert.notNull(serializationService, "OAuth2 serialization service is required.");
	}

	protected RestTemplate getRestTemplate() {
		return restTemplate;
	}

	protected OAuth2SerializationService getSerializationService() {
		return serializationService;
	}

	public void setSerializationService(OAuth2SerializationService serializationService) {
		this.serializationService = serializationService;
	}

	public void setAuthenticationHandler(ClientAuthenticationHandler authenticationHandler) {
		this.authenticationHandler = authenticationHandler;
	}

	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = new ArrayList<HttpMessageConverter<?>>(messageConverters);
		this.messageConverters.add(this.formAccessTokenMessageConverter);
		this.messageConverters.add(this.formErrorMessageConverter);
	}

	protected OAuth2AccessToken retrieveToken(MultiValueMap<String, String> form,
			OAuth2ProtectedResourceDetails resource) {

		try {
			// Prepare headers and form before going into rest template call in case the URI is affected by the result
			HttpHeaders headers = new HttpHeaders();
			authenticationHandler.authenticateTokenRequest(resource, form, headers);

			return getRestTemplate().execute(getAccessTokenUri(resource, form), getHttpMethod(),
					getRequestCallback(resource, form, headers), getResponseExtractor(), form.toSingleValueMap());

		}
		catch (OAuth2Exception oe) {
			throw new OAuth2AccessDeniedException("Access token denied.", resource, oe);
		}
		catch (RestClientException rce) {
			throw new OAuth2AccessDeniedException("Error requesting access token.", resource, rce);
		}

	}

	protected HttpMethod getHttpMethod() {
		return HttpMethod.POST;
	}

	protected String getAccessTokenUri(OAuth2ProtectedResourceDetails resource, MultiValueMap<String, String> form) {

		String accessTokenUri = resource.getAccessTokenUri();

		if (logger.isDebugEnabled()) {
			logger.debug("Retrieving token from " + accessTokenUri);
		}

		StringBuilder builder = new StringBuilder(accessTokenUri);

		if (getHttpMethod() == HttpMethod.GET) {
			String separator = "?";
			if (accessTokenUri.contains("?")) {
				separator = "&";
			}

			for (String key : form.keySet()) {
				builder.append(separator);
				builder.append(key + "={" + key + "}");
				separator = "&";
			}
		}

		return builder.toString();

	}

	protected ResponseExtractor<OAuth2AccessToken> getResponseExtractor() {
		return new HttpMessageConverterExtractor<OAuth2AccessToken>(OAuth2AccessToken.class, this.messageConverters);
	}

	protected RequestCallback getRequestCallback(OAuth2ProtectedResourceDetails resource,
			MultiValueMap<String, String> form, HttpHeaders headers) {
		return new OAuth2AuthTokenCallback(form, headers);
	}

	/**
	 * Request callback implementation that writes the given object to the request stream.
	 */
	private class OAuth2AuthTokenCallback implements RequestCallback {

		private final MultiValueMap<String, String> form;

		private final HttpHeaders headers;

		private OAuth2AuthTokenCallback(MultiValueMap<String, String> form, HttpHeaders headers) {
			this.form = form;
			this.headers = headers;
		}

		public void doWithRequest(ClientHttpRequest request) throws IOException {
			request.getHeaders().putAll(this.headers);
			request.getHeaders().setAccept(
					Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED));
			FORM_MESSAGE_CONVERTER.write(this.form, MediaType.APPLICATION_FORM_URLENCODED, request);
		}
	}

	private class AccessTokenErrorHandler extends DefaultResponseErrorHandler {

		@SuppressWarnings("unchecked")
		@Override
		public void handleError(ClientHttpResponse response) throws IOException {
			for (HttpMessageConverter<?> converter : messageConverters) {
				if (converter.canRead(OAuth2Exception.class, response.getHeaders().getContentType())) {
					throw ((HttpMessageConverter<OAuth2Exception>)converter).read(OAuth2Exception.class, response);
				}
			}
			super.handleError(response);
		}

	}

	private class OAuth2AccessTokenMessageConverter extends AbstractHttpMessageConverter<OAuth2AccessToken> {

		private OAuth2AccessTokenMessageConverter() {
			super(MediaType.APPLICATION_FORM_URLENCODED);
		}

		@Override
		protected boolean supports(Class<?> clazz) {
			return OAuth2AccessToken.class.isAssignableFrom(clazz);
		}

		@Override
		protected OAuth2AccessToken readInternal(Class<? extends OAuth2AccessToken> clazz, HttpInputMessage response)
				throws IOException, HttpMessageNotReadableException {
			// the spec currently says json is required, but facebook, for example, still returns form-encoded.
			MultiValueMap<String, String> map = FORM_MESSAGE_CONVERTER.read(null, response);
			return getSerializationService().deserializeAccessToken(map.toSingleValueMap());
		}

		@Override
		protected void writeInternal(OAuth2AccessToken oAuth2AccessToken, HttpOutputMessage outputMessage)
				throws IOException, HttpMessageNotWritableException {
			throw new HttpMessageNotWritableException("Access token support shouldn't need to write access tokens.");
		}
	}

	private class OAuth2ErrorMessageConverter extends AbstractHttpMessageConverter<OAuth2Exception> {

		private OAuth2ErrorMessageConverter() {
			super(MediaType.APPLICATION_FORM_URLENCODED);
		}

		@Override
		protected boolean supports(Class<?> clazz) {
			return OAuth2Exception.class.isAssignableFrom(clazz);
		}

		@Override
		protected OAuth2Exception readInternal(Class<? extends OAuth2Exception> clazz, HttpInputMessage response)
				throws IOException, HttpMessageNotReadableException {
			// the spec currently says json is required, but facebook, for example, still returns form-encoded.
			MultiValueMap<String, String> map = FORM_MESSAGE_CONVERTER.read(null, response);
			return getSerializationService().deserializeError(map.toSingleValueMap());
		}

		@Override
		protected void writeInternal(OAuth2Exception oAuth2AccessToken, HttpOutputMessage outputMessage)
				throws IOException, HttpMessageNotWritableException {
			throw new HttpMessageNotWritableException("Access token support shouldn't need to write errors.");
		}
	}

}
