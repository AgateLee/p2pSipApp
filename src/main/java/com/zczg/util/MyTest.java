package com.zczg.util;

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
		logger.info("test");
		JDBCUtils db = new JDBCUtils();
		
		db.update("update p2puser set state = " + cur_env.getSettingsInt().get("user_offline"));
		db.update("truncate test");
	}
}
