package com.springstudy.book2.chapter7_aop;

import lombok.Data;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 被拦截的bean
 * @date 2022/9/13 4:27 下午
 */
@Data
public class TestBean {

	private String testStr = "testStr";

	public void test() {
		System.out.println(testStr);
	}

}