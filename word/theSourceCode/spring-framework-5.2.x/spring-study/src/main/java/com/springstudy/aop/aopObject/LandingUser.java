package com.springstudy.aop.aopObject;

import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/30 11:16 下午
 */
@Component
public class LandingUser {

	/**
	 * Aop
	 *
	 * @param username
	 * @param password
	 */
	public void login(String username, String password) {
		System.out.println("执行了登陆方法...");
		System.out.println(username);
		System.out.println(password);
	}

}