package com.springstudy.book2.chapter10_tx;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/18 10:22 下午
 */
public class User {

	private Integer id;

	private String name;

	private Integer age;

	private String sex;


	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public String getSex() {
		return sex;
	}

	public void setSex(String sex) {
		this.sex = sex;
	}

	@Override
	public String toString() {
		return "User{" +
				"id=" + id +
				", name='" + name + '\'' +
				", age=" + age +
				", sex='" + sex + '\'' +
				'}';
	}


}