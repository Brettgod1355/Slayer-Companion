package com.example;

import java.io.*;

/** Every line below must be caught by ci/check-hub-rules.sh; this file is a self-test, never compiled. */
public class Bad
{
	void a() throws Exception
	{
		Object c = new java.net.URL("https://example.com/x").openConnection();
		new File("/tmp/x").createNewFile();
		Thread.sleep(10);
		Object m = Bad.class.getMethods()[0];
		Runtime.getRuntime().exec("ls");
	}
}
