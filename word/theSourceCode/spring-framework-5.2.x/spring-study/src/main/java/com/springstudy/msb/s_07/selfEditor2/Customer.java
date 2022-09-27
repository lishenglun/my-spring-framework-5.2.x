package com.springstudy.msb.s_07.selfEditor2;

import org.springframework.beans.factory.annotation.Value;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/15 11:10 下午
 */
//@PropertySource("classpath:msb/spring-07-customer.properties")
//@Component
public class Customer {

	@Value("${customer.name}")
	private String name;

	@Value("${customer.address}")
	private Address address;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Address getAddress() {
		return address;
	}

	public void setAddress(Address address) {
		this.address = address;
	}

	@Override
	public String toString() {
		return "Customer{" +
				"name='" + name + '\'' +
				", address=" + address +
				'}';
	}

}