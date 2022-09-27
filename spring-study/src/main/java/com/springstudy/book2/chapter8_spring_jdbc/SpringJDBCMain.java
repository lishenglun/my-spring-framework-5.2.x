package com.springstudy.book2.chapter8_spring_jdbc;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/19 10:00
 */
public class SpringJDBCMain {

	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("book2/chapter8_spring_jdbc/spring-jdbc-config.xml");

		UserService userService = (UserService) ac.getBean("userService");
		//User user = new User();
		//user.setName("zhangsan");
		//user.setAge(20);
		//user.setSex("男");
		//userService.save(user);

		for (User userServiceUser : userService.getUsers()) {
			System.out.println(userServiceUser);
		}
	}

}