package com.springstudy.msb.s_15.supplier;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/7 9:26 上午
 */
public class User {

	private String username;

	public User(String username) {
		this.username = username;
	}

	public User() {
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public String toString() {
		return "User{" +
				"username='" + username + '\'' +
				'}';
	}

}