package com.springstudy.msb.s_16.initAndDestroy;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/12 10:45 下午
 */
public class Demo {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-16-initAndDestroy-config.xml");
		// ⚠️只有调用close()，才能触发<destroy-method>方法的执行
		ac.close();
	}

}