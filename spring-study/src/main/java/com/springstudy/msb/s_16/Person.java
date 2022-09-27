package com.springstudy.msb.s_16;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/7 3:15 下午
 */
public class Person {

	private String name;

	private Integer id;

	public Person() {
	}

	public Person(Integer id) {
		this.id = id;
	}

	public Person(String name, Integer id) {
		this.name = name;
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return "Person{" +
				"name='" + name + '\'' +
				", id=" + id +
				'}';
	}
}