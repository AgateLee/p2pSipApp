package com.zczg.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.apache.log4j.Logger;

public class CurEnv {
	private Map<String, String> settings;
	private Map<String, Integer> settingsInt;
	private static Logger logger = Logger.getLogger(CurEnv.class);
	
	public CurEnv()
	{
		Para tp = new Para();
		settings = tp.getParaPair("sysstr", 0, 1);
		settingsInt = tp.getParaPairInt("sysint", 0, 1);
	}
	
	public String myMD5(String md5)
	{
		try{
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] arr = md.digest(md5.getBytes());
			StringBuffer sb = new StringBuffer();
			
			for(int i = 0; i < arr.length; i++)
			{
				sb.append(Integer.toHexString(arr[i] & 0xFF | 0x100).substring(1,3));
			}
			
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			
		}
		
		return null;
	}
	
	public String myDigest(String username, String realm, String passwd, String nonce, String method, String url)
	{
		logger.info("username:" + username);
		logger.info("realm:" + realm);
		logger.info("passwd:" + passwd);
		logger.info("nonce:" + nonce);
		logger.info("method:" + method);
		logger.info("url:" + url);
		
		String secret = myMD5(username + ":" + realm + ":" + passwd);
		String data = nonce + ":" + myMD5(method + ":" + url);
		return myMD5(secret + ":" + data);
	}
	
	public Map<String, String> getSettings() {
		return settings;
	}

	public void setSettings(Map<String, String> settings) {
		this.settings = settings;
	}

	public Map<String, Integer> getSettingsInt() {
		return settingsInt;
	}

	public void setSettingsInt(Map<String, Integer> settingsInt) {
		this.settingsInt = settingsInt;
	}	
}
