package com.zczg.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public class CurEnv {
	private Map<String, String> settings;
	private Map<String, Integer> settingsInt;
	
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