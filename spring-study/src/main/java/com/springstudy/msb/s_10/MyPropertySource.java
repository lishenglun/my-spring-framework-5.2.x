package com.springstudy.msb.s_10;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/25 11:23 下午
 */
@Configuration
@PropertySource({"classpath:myconfig2.properties"})
public class MyPropertySource {

	@Value("${myconfig2.name}")
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}