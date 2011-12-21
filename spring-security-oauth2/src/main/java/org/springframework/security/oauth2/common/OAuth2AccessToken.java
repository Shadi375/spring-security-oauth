package org.springframework.security.oauth2.common;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

/**
 * Basic access token for OAuth 2.
 * 
 * @author Ryan Heaton
 * @author Dave Syer
 */
@JsonSerialize(include=Inclusion.NON_NULL)
public class OAuth2AccessToken implements Serializable {

	private static final long serialVersionUID = 914967629530462926L;

	public static String BEARER_TYPE_PARAMETER = "access_token";

	public static String BEARER_TYPE = "Bearer";

	public static String OAUTH2_TYPE = "OAuth2";

	@JsonProperty("access_token")
	private final String value;
	@JsonIgnore
	private Date expiration;
	@JsonProperty("token_type")
	private String tokenType = BEARER_TYPE.toLowerCase();
	@JsonProperty("refresh_token")
	private OAuth2RefreshToken refreshToken;
	@JsonIgnore
	private Set<String> scope;
	
	/**
	 * Create an access token from the value provided.
	 */
	public OAuth2AccessToken(String value) {
		this.value = value;
	}
	
	@SuppressWarnings("unused")
	private OAuth2AccessToken() {
		this(null);
	}

	/**
	 * The token value.
	 * 
	 * @return The token value.
	 */
	@JsonIgnore
	public String getValue() {
		return value;
	}
	
	@JsonProperty("expires_in")
	public int getExpiresIn() {
		return expiration!=null ? Long.valueOf((expiration.getTime() - System.currentTimeMillis())/1000L).intValue() : 0;
	}
	
	@JsonProperty("expires_in")
	protected void setExpiresIn(int delta) {
		setExpiration(new Date(System.currentTimeMillis() + delta));
	}

	/**
	 * The instant the token expires.
	 * 
	 * @return The instant the token expires.
	 */
	@JsonIgnore
	public Date getExpiration() {
		return expiration;
	}

	/**
	 * The instant the token expires.
	 * 
	 * @param expiration The instant the token expires.
	 */
	public void setExpiration(Date expiration) {
		this.expiration = expiration;
	}

	/**
	 * Convenience method for checking expiration
	 * 
	 * @return true if the expiration is befor ethe current time
	 */
	@JsonIgnore
	public boolean isExpired() {
		return expiration!=null && expiration.before(new Date());
	}

	/**
	 * The token type, as introduced in draft 11 of the OAuth 2 spec. The spec doesn't define (yet) that the valid token
	 * types are, but says it's required so the default will just be "undefined".
	 * 
	 * @return The token type, as introduced in draft 11 of the OAuth 2 spec.
	 */
	@JsonIgnore
	public String getTokenType() {
		return tokenType;
	}

	/**
	 * The token type, as introduced in draft 11 of the OAuth 2 spec.
	 * 
	 * @param tokenType The token type, as introduced in draft 11 of the OAuth 2 spec.
	 */
	public void setTokenType(String tokenType) {
		this.tokenType = tokenType;
	}

	/**
	 * The refresh token associated with the access token, if any.
	 * 
	 * @return The refresh token associated with the access token, if any.
	 */
	@JsonIgnore
	public OAuth2RefreshToken getRefreshToken() {
		return refreshToken;
	}

	/**
	 * The refresh token associated with the access token, if any.
	 * 
	 * @param refreshToken The refresh token associated with the access token, if any.
	 */
	public void setRefreshToken(OAuth2RefreshToken refreshToken) {
		this.refreshToken = refreshToken;
	}

	/**
	 * The scope of the token.
	 * 
	 * @return The scope of the token.
	 */
	public Set<String> getScope() {
		return scope;
	}

	/**
	 * The scope of the token.
	 * 
	 * @param scope The scope of the token.
	 */
	public void setScope(Set<String> scope) {
		this.scope = scope;
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && toString().equals(obj.toString());
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	@Override
	public String toString() {
		return getValue();
	}

}
