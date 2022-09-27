package com.springstudy.msb.s_10;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 内部类
 * @date 2022/4/25 11:34 上午
 */
@Component
@Configuration
@PropertySource("classPath:db.properties")
public class MyMember {

	@Value("#{jdbc.url}")
	private String url;

	@Component
	@Configuration
	@ComponentScan
	class innerClass {

		@Component
		@Configuration
		@ComponentScan
		class innerInnerClass{

		}

	}

}