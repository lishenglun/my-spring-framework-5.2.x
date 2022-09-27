package com.springstudy.msb.s_20.requiredType;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/23 11:49 上午
 */
public class Main {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-20-requiredType.xml");
		C ha = ac.getBean("ha", C.class);
		ac.close();
	}

}