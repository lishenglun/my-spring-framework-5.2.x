package com.springstudy.msb.s_10;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/26 9:37 上午
 */
public class Person {

	private String name;

	private Integer age;

	public Person() {
	}

	public Person(String name, Integer age) {
		this.name = name;
		this.age = age;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getAge() {
		return age;
	}

	public void setAge(Integer age) {
		this.age = age;
	}
}