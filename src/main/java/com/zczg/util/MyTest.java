package com.zczg.util;

import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

public class MyTest {		
	private static Logger logger = Logger.getLogger(MyTest.class);
	private Integer id;
	private String name;
	private static CurEnv cur_env = new CurEnv();
	
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public static void main(String[] args)
	{
//		logger.info("test");
//		
//		System.out.println(cur_env.getSettingsInt().get("user_idle"));
//		Map<String,Object> map = JDBCUtils.queryForMap("select * from p2puser where name = 'bob'");
//
//		for(Map.Entry<String, Object> entry: map.entrySet())
//		{
//			System.out.println(entry.getKey() + " " + entry.getValue());
//		}
		
//		String nonce = "AWTF0BUAGVBOB4SXWCUQG9EN88ZQ33LI";
//		String name = "alice";
//		String passwd = "alice";
//		String realm = "10.109.247.126";
//		String url = "sip:10.109.247.126";
//		String method = "REGISTER";
//		System.out.println(cur_env.myDigest(name, realm, passwd, nonce, method, url));
		
//		 Iterator<String> headerNames = req.getHeaderNames();
//         while(headerNames.hasNext())
//         {
//           String headerName = (String)headerNames.next();
//           logger.info(headerName+" :  ");
//           logger.info(req.getHeader(headerName));
//         }
		
		String auth = "Digest username=\"alice\",realm=\"10.109.247.126\",nonce=\"G0RHUN3A1970LQK0DIDFU7NB1VU96IRZ\",uri=\"sip:10.109.247.126\",response=\"c24a8623ba290dc39d03552dacf1420d\",algorithm=MD5";
		int st = auth.indexOf("response=\"") + 10;
		int ed = auth.indexOf("\"", st);
		System.out.println("auth " + auth.substring(st, ed));
	}
}
