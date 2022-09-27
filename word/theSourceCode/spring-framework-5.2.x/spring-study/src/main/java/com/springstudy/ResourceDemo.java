package com.springstudy;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2021/12/16 11:37 上午
 */
public class ResourceDemo {

	public static void main(String[] args) throws IOException {

		ClassLoader classLoader = ResourceDemo.class.getClassLoader();
		Enumeration<URL> resources = classLoader.getResources("msb/spring-06-selfTag.xml");
		System.out.println(resources);
	}

}