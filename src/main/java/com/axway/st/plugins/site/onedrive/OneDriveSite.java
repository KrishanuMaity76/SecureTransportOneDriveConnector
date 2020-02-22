package com.axway.st.plugins.site.onedrive;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.net.ftp.FTPClient;
import org.json.JSONArray;

import com.axway.st.plugins.site.CustomSite;
import com.axway.st.plugins.site.DestinationFile;
import com.axway.st.plugins.site.FileItem;
import com.axway.st.plugins.site.FlowAttributesData;
import com.axway.st.plugins.site.SourceFile;
import com.axway.st.plugins.site.TransferFailedException;
import com.axway.st.plugins.site.expression.InvalidExpressionException;
import com.axway.st.plugins.site.onedrive.bean.OneDriveBean;
import com.axway.st.plugins.site.services.AdditionalInfoLoggingService;
import com.axway.st.plugins.site.services.CommandLoggingService;
import com.axway.st.plugins.site.services.ExpressionEvaluatorService;
import com.axway.st.plugins.site.services.LoggingService;
import com.axway.st.plugins.site.services.ProxyService;
import com.axway.st.plugins.site.services.proxy.ProxyInfo;
import com.jcraft.jsch.Session;
import com.tumbleweed.io.StreamUtil;
import com.tumbleweed.st.util.expressions.STExpressionEvaluator;
import com.tumbleweed.util.expressions.InvalidSyntaxException;

/**
 * This is custom transfer site class which needs to extend the CustomSite.
 * In this class there are several methods which are inherited from the CustomSite 
 * and those methods need to be implemented in order to custom transfer site to work properly
 */
public class OneDriveSite extends CustomSite {
	/** The UIBean implementation. */
	private OneDriveBean oneDriveBean = new OneDriveBean();

	private Session session;
	private FTPClient ftpClient;

	private STExpressionEvaluator stEE = new STExpressionEvaluator();

	/** Provides proxy server configuration. */
	@Inject
	private ProxyService mProxyService;

	private ProxyInfo mProxyInfo;
	/** Protocol commands logging service. */
	@Inject
	private CommandLoggingService mCommandLoggingService;

	/** Additional information logging service. */
	@Inject
	private AdditionalInfoLoggingService mAdditionalInfoLogService;

	/** Logging service, used to log messages on different levels. */
	@Inject
	private LoggingService mLoggingService;

	/** Flow attributes data, used to read/write flow attributes. */
	@Inject
	private FlowAttributesData mFlowAttributesData;

	/** Expression evaluator service, used to evaluate various expressions. */
	@Inject
	private ExpressionEvaluatorService mExpressionEvaluatorService;

	public OneDriveSite() {
		setUIBean(oneDriveBean);
	}

	

	@Override
	public void finalizeExecution() throws IOException {
		if (session != null) {
			mLoggingService.info("Disconnect from client.");
			session.disconnect();
			session = null;
		} else if (ftpClient.isConnected()) {
			ftpClient.logout();
			ftpClient.disconnect();
		}

	}
	
	/**
	 * This method is responsible to get the the file from the remote site, for example onedrive in this case. 
	 * 'inputStream' is the input stream of the file and 'inputStream' stored in secure transport directory.
	 */
	@Override
	public void getFile(DestinationFile file) throws IOException {
		mLoggingService.info(java.text.MessageFormat.format("Getting file {0}.", file.getName()));

		try {
			mLoggingService.info("File Download from Onedrive..."+file.getName());
			String downloadUrl = file.getFileMetadata().get("downloadUrl");
			OutputStream targetKeyOutputStream = null;
	        com.axway.st.plugins.site.RemotePartner paramRemotePartner = new com.axway.st.plugins.site.RemotePartner("host", "folder", true);
	        targetKeyOutputStream = file.getOutputStream(paramRemotePartner);
	        BufferedInputStream inputStream = new BufferedInputStream(new URL(downloadUrl).openStream());
	        StreamUtil.moveData(inputStream, targetKeyOutputStream, 32768);
	        if(inputStream != null)
	        	inputStream.close();
	        mLoggingService.info("Completed File download..."+file.getName());
		} catch (TransferFailedException e) {
			String msg = (new StringBuilder()).append("Onedrive Transfer Site pull failed. Error message is [").append(e.getMessage() != null ? e.getMessage() : "An unexpected error has occurred").append("]").toString();
			mLoggingService.error(msg, e);
            if(e instanceof TransferFailedException)
            {
                TransferFailedException exception = e;
                if(exception.isPermanentFailure())
                    throw exception;
            }
            throw new TransferFailedException(msg, e);
		}

	}
	/**
	 * 
	 * This method is implemented to list the files. In this case, onedrive APIs are called to get the list of files.
	 * Every file is a FileItem type of object which has file name, file received as, file metadata, etc.
     * @return list of fileitem.
	 * 
	 */
	public List<FileItem> list() throws IOException {
		mLoggingService.info("Performing list operation ...");
		try {
			List<FileItem> list = new ArrayList<FileItem>();
			String accessToken = getAccessToken(oneDriveBean.getOnedrive_appId(), oneDriveBean.getOnedrive_secret(), oneDriveBean.getOnedrive_tenantId());
			String userId = getUserID(accessToken, oneDriveBean.getOnedrive_userEmail());
			String driveId = getDriveID(accessToken, userId);
			list = getFilePaths(oneDriveBean.getOnedrive_downloadFolder(), driveId, accessToken);
			if (list.size() == 0) {
				mLoggingService.info(
						"Returning an empty list to get method as File download operation is not supported by this plugin");
			}
			return list;
		} catch (Exception e) {
			String msg = (new StringBuilder()).append("Onedrive Transfer Site file listing failed. Error message is [").append(e.getMessage() != null ? e.getMessage() : "An unexpected error has occurred").append("]").toString();
			mLoggingService.error(msg, e);
            if(e instanceof TransferFailedException)
            {
                TransferFailedException exception = (TransferFailedException)e;
                if(exception.isPermanentFailure())
                    throw exception;
            }
            throw new TransferFailedException(msg, e);
		}
	}
	
	/**
	 * 
	 * This method is implemented to put the file in the onedrive upload directory. ##TO-BE-IMPLEMENTED##
     * @param This method takes source file which needs to put in the upload directory.
	 * 
	 */
	@Override
	public void putFile(SourceFile file) throws IOException {
		try {
			mLoggingService.info(
					java.text.MessageFormat.format("Sending file {0} with size {1}.", file.getName(), file.getSize()));
			//connect();
			mLoggingService
					.info(java.text.MessageFormat.format("File (0) upload is not supported by this plugin", file));
		} catch (SecurityException e) {
			mLoggingService.error(
					java.text.MessageFormat.format("Exception occurred while uploading the file {0} -", file.getName())
							+ e.getMessage());
		}

	}
	
	/**
	 * 
	 * This method is a private method called from the public method of this class. This method returns onedrive user is based on the user email.
     * @param This method takes access token and the user email.
     * @return It returns onedrive user id.
	 * 
	 */
	private String getUserID(String accessToken, String email) throws IOException{
		try {
			mLoggingService.info("Start getUserID for email:"+email);
			URL url = new URL("https://graph.microsoft.com/v1.0/users");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Authorization", "Bearer "+accessToken);
			
			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}
			
			BufferedReader br = new BufferedReader(new InputStreamReader(
					(conn.getInputStream())));
			
			String output;
			String userId = null;
			while ((output = br.readLine()) != null) {
				//System.out.println(output);
				org.json.JSONObject jsonObject = new org.json.JSONObject(output);
				if(jsonObject.has("value")) {
					JSONArray users = jsonObject.getJSONArray("value");
					if(users.length()>0) {
						for(int i = 0 ; i < users.length(); i++) {
							if(users.getJSONObject(i).has("mail") && users.getJSONObject(i).getString("mail").toLowerCase().equals(email.toLowerCase())) {
								userId =  users.getJSONObject(i).getString("mail");
								break;
							}
						}
					}
					
				}
			}
			conn.disconnect();
			mLoggingService.info("UserID: "+userId+" is catptured");
			return userId;
		} catch (Exception e) {
			String msg = (new StringBuilder()).append("Onedrive Transfer Site getting userid failed. Error message is [").append(e.getMessage() != null ? e.getMessage() : "An unexpected error has occurred").append("]").toString();
			mLoggingService.error(msg, e);
            if(e instanceof TransferFailedException)
            {
                TransferFailedException exception = (TransferFailedException)e;
                if(exception.isPermanentFailure())
                    throw exception;
            }
            throw new TransferFailedException(msg, e);
		}
	}
	
	/**
	 * 
	 * This method is a private method called from the public method of this class. This method returns onedrive user is based on the user id.
     * @param This method takes access token and the user id.
     * @return It returns onedrive id.
	 * 
	 */
	private String getDriveID(String accessToken, String userId) throws IOException{
		try {
			mLoggingService.info("Start getDriveID");
			URL url = new URL("https://graph.microsoft.com/v1.0/users/"+userId+"/drive");
			//System.out.println(url);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Authorization", "Bearer "+accessToken);
			
			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}
			
			BufferedReader br = new BufferedReader(new InputStreamReader(
					(conn.getInputStream())));
			
			String output;
			String driveId = null;
			while ((output = br.readLine()) != null) {
				//System.out.println(output);
				org.json.JSONObject jsonObject = new org.json.JSONObject(output);
				if(jsonObject.has("id")) {
					driveId = jsonObject.getString("id");
				}
			}
			conn.disconnect();
			mLoggingService.info("DriveId "+driveId+" is captured.");
			return driveId;
		} catch (Exception e) {
			String msg = (new StringBuilder()).append("Onedrive Transfer Site getting driveId failed. Error message is [").append(e.getMessage() != null ? e.getMessage() : "An unexpected error has occurred").append("]").toString();
			mLoggingService.error(msg, e);
            if(e instanceof TransferFailedException)
            {
                TransferFailedException exception = (TransferFailedException)e;
                if(exception.isPermanentFailure())
                    throw exception;
            }
            throw new TransferFailedException(msg, e);
		}
	}
	
	/**
	 * 
	 * This method is a private method called from the public method of this class.  In this case, onedrive APIs are called to get the list of files.
	 * Every file is a FileItem type of object which has file name, file received as, file metadata, etc.
     * @param This method takes access token, drive id and download folder.
     * @return It returns the list of file item.
	 * 
	 */
	private List<FileItem> getFilePaths(String downloadFolder, String driveId, String accessToken) throws IOException{
		try {
			mLoggingService.info("Start getFilePaths");
			List<FileItem> files = new ArrayList<FileItem>();
			URL url = new URL("https://graph.microsoft.com/v1.0/drives/"+driveId+"/root:"+downloadFolder+":/children");
			//System.out.println(url);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Authorization", "Bearer "+accessToken);
			
			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}
			
			BufferedReader br = new BufferedReader(new InputStreamReader(
					(conn.getInputStream())));
			
			String output;
			mLoggingService.info("Output from Server .... \n");
			while ((output = br.readLine()) != null) {
				//System.out.println(output);
				org.json.JSONObject jsonObject = new org.json.JSONObject(output);
				if(jsonObject.has("value")) {
					JSONArray users = jsonObject.getJSONArray("value");
					if(users.length()>0) {
						for(int i = 0 ; i < users.length(); i++) {
							if(users.getJSONObject(i).has("@microsoft.graph.downloadUrl") ) {
								String downloadUrl = users.getJSONObject(i).getString("@microsoft.graph.downloadUrl");
								String name = users.getJSONObject(i).getString("name");
								Map<String, String> metadata = new HashMap<String, String>();
								metadata.put("downloadUrl", downloadUrl);
								FileItem file = new FileItem(name, name, metadata);
								files.add(file);
								mLoggingService.info("File added to the list..."+name);
							}
						}
					}
				}
			}
			
			conn.disconnect();
			return files;
		} catch (Exception e) {
			String msg = (new StringBuilder()).append("Onedrive Transfer Site getting file path failed. Error message is [").append(e.getMessage() != null ? e.getMessage() : "An unexpected error has occurred").append("]").toString();
			mLoggingService.error(msg, e);
            if(e instanceof TransferFailedException)
            {
                TransferFailedException exception = (TransferFailedException)e;
                if(exception.isPermanentFailure())
                    throw exception;
            }
            throw new TransferFailedException(msg, e);
		}
	}
	
	/**
	 * 
	 * This method is implemented to get access token using client id, secret and tenant id. This method calls onedrive oauth API to get the access token.
     * @param This method takes client id, secret and tenant id
     * @return It returns the access token.
     * 
     * Today we are not reusing the token, everytime this pluggable transfer site connects to onedrive, it generates a token. future plan is to reuse the token.
	 * 
	 */
	private String getAccessToken(String clientId, String secret, String tenantId) throws TransferFailedException{
		try {
			mLoggingService.info("Start connecting with Graph API to get the token");
			URL url = new URL("https://login.microsoftonline.com/"+tenantId+"/oauth2/v2.0/token");
			String urlParameters  = "grant_type=client_credentials&scope=https://graph.microsoft.com/.default&client_id="+clientId+"&client_secret="+secret;
			byte[] postData       = urlParameters.getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			mLoggingService.info("After connection");
			conn.setInstanceFollowRedirects( false );
			conn.setRequestMethod( "POST" );
			conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded"); 
			conn.setRequestProperty( "charset", "utf-8");
			//System.out.println(Integer.toString( postDataLength ));
			conn.setRequestProperty( "Content-Length", String.valueOf(postDataLength));
			conn.setUseCaches( false );
			conn.setDoOutput(true);
			mLoggingService.info("After setting params");
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) {
				   wr.write( postData );
				}
			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}
			
			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
			
			String output;
			//System.out.println("Output from Server .... \n");
			String accessToken = null;
			while ((output = br.readLine()) != null) {
				//System.out.println(output);
				org.json.JSONObject jsonObject = new org.json.JSONObject(output);
				accessToken = jsonObject.getString("access_token");
			}
			
			conn.disconnect();
			//mLoggingService.info("End connecting with Graph API to get the token");
			return accessToken;
		}catch(Exception e) {
			String msg = (new StringBuilder()).append("Onedrive Transfer Site aquiring access token has failed. Error message is [").append(e.getMessage() != null ? e.getMessage() : "An unexpected error has occurred").append("]").toString();
			mLoggingService.error(msg, e);
            if(e instanceof TransferFailedException)
            {
                TransferFailedException exception = (TransferFailedException)e;
                if(exception.isPermanentFailure())
                    throw exception;
            }
            throw new TransferFailedException(msg, e);
		}
	}
	
	/**
	 * Resolve the expression fields from UI bean. ##TO-BE-IMPLEMENTED##
	 * 
	 * @param mListingBean the UI bean
	 * @throws IOException
	 */
	private void resolveExpressionFields(OneDriveBean mListingBean)
			throws InvalidExpressionException, IOException {
		// first, load the expression evaluator service with context,
		// this context can be used with pluginContext variable
		mLoggingService.info("inside resolve expression field ...");
		mExpressionEvaluatorService.loadExpressionService(getFlowAttributesData().getFlowAttributes());

	}

	private String getExpressionEvaluatedValue(String expression) throws IOException {
		String evaluatedValue = null;
		try {
			evaluatedValue = this.stEE.evaluate(expression);
		} catch (InvalidSyntaxException | com.tumbleweed.util.expressions.InvalidExpressionException
				| com.tumbleweed.util.expressions.InvalidExpansionException
				| com.tumbleweed.util.expressions.InvalidEvaluationException e) {

			String errorMsg = "Could not evaluate value from expression: " + expression;

			mLoggingService.error(errorMsg, e);
			throw new IOException("Error during evaluating value from expression: " + e.getMessage(), e);
		}

		return evaluatedValue;
	}

	@Override
	public List<String> getProtocolCommands() {
		return mCommandLoggingService.getProtocolCommands();
	}

	@Override
	public String getAdditionalInfo() {
		return mAdditionalInfoLogService.getAdditionalInfo();
	}

	@Override
	public FlowAttributesData getFlowAttributesData() {
		return mFlowAttributesData;
	}

	@Override
	public ProxyInfo getProxyInfo() {
		return mProxyService.getProxy(mProxyInfo.getProxyType(), oneDriveBean.getOnedrive_networkZone());

	}

	/**
	 * Sets the ProxyService.
	 * 
	 * @param service the ProxyService.
	 */
	public void setProxyService(ProxyService service) {
		mProxyService = service;
	}

	/**
	 * Returns the ProxyService.
	 * 
	 * @return the ProxyService.
	 */
	public ProxyService getProxyService() {
		return mProxyService;
	}

}
