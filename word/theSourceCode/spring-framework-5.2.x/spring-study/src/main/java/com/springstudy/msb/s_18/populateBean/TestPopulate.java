package com.springstudy.msb.s_18.populateBean;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Map;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/15 8:35 下午
 */
public class TestPopulate {

	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-18-populate.xml");
		Person person = (Person) ac.getBean("person");
		Map<String, Object> maps = person.getMaps();
	}

}