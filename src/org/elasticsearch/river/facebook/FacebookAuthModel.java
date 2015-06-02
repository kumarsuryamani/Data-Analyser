/**
 * 
 */
package org.elasticsearch.river.facebook;

/**
 * @author Surya
 *
 */
public class FacebookAuthModel {
	
	private boolean isDebugEnabled;
	private String oAuthAppId;
	private String oAuthAppSecret;
	private String oAuthAccessToken;
	private boolean isJsonStoreEnabled;
	private String oAuthPermissions;
	/**
	 * @return the isDebugEnabled
	 */
	public boolean isDebugEnabled() {
		return isDebugEnabled;
	}
	/**
	 * @param isDebugEnabled the isDebugEnabled to set
	 */
	public void setDebugEnabled(boolean isDebugEnabled) {
		this.isDebugEnabled = isDebugEnabled;
	}
	/**
	 * @return the oAuthAppId
	 */
	public String getoAuthAppId() {
		return oAuthAppId;
	}
	/**
	 * @param oAuthAppId the oAuthAppId to set
	 */
	public void setoAuthAppId(String oAuthAppId) {
		this.oAuthAppId = oAuthAppId;
	}
	/**
	 * @return the oAuthAppSecret
	 */
	public String getoAuthAppSecret() {
		return oAuthAppSecret;
	}
	/**
	 * @param oAuthAppSecret the oAuthAppSecret to set
	 */
	public void setoAuthAppSecret(String oAuthAppSecret) {
		this.oAuthAppSecret = oAuthAppSecret;
	}
	/**
	 * @return the oAuthAccessToken
	 */
	public String getoAuthAccessToken() {
		return oAuthAccessToken;
	}
	/**
	 * @param oAuthAccessToken the oAuthAccessToken to set
	 */
	public void setoAuthAccessToken(String oAuthAccessToken) {
		this.oAuthAccessToken = oAuthAccessToken;
	}
	/**
	 * @return the isJsonStoreEnabled
	 */
	public boolean getIsJsonStoreEnabled() {
		return isJsonStoreEnabled;
	}
	/**
	 * @param isJsonStoreEnabled the isJsonStoreEnabled to set
	 */
	public void setIsJsonStoreEnabled(boolean isJsonStoreEnabled) {
		this.isJsonStoreEnabled = isJsonStoreEnabled;
	}
	/**
	 * @return the oAuthPermissions
	 */
	public String getoAuthPermissions() {
		return oAuthPermissions;
	}
	/**
	 * @param oAuthPermissions the oAuthPermissions to set
	 */
	public void setoAuthPermissions(String oAuthPermissions) {
		this.oAuthPermissions = oAuthPermissions;
	}
	

}
