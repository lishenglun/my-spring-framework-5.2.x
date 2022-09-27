package com.springstudy.msb.s_02.bean;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2021/12/13 12:28 下午
 */
public class A {

	private List<String> name;

	private Integer age;

	public A(List<String> name, Integer age) {
		this.name = name;
		this.age = age;
	}

	public A() {
	}
}