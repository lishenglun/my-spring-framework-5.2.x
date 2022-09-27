package com.springstudy.msb.other.myComponent;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/29 10:59 下午
 */
@MyComponent
public class User {

	private String name = "zhansgan";

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}


	@Override
	public String toString() {
		return "User{" +
				"name='" + name + '\'' +
				'}';
	}
}