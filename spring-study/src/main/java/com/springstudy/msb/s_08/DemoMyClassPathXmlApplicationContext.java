package com.springstudy.msb.s_08;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2021/12/13 10:44 下午
 */
public class DemoMyClassPathXmlApplicationContext {

	public static void main(String[] args) {
		// dtd文件 / xsd文件
		MyClassPathXmlApplicationContext myClassPathXmlApplicationContext = new MyClassPathXmlApplicationContext("msb/spring-08-config.xml");
	}

}