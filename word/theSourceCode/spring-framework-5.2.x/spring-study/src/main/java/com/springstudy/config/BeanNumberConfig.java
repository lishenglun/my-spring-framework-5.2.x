package com.springstudy.config;

import com.springstudy.Initializethebean.obj.UserNameEntity;
import com.springstudy.Initializethebean.obj.UserPasswordEntity;
import org.springframework.context.annotation.Bean;

/**
 *
 */
//@Configuration
public class BeanNumberConfig {

	// 当没加@Configuration、userNameEntity是普通方法的时候，会打印两遍「UserNameEntity...」
	// 当加了@Configuration、userNameEntity是普通方法的时候，会打印一遍「UserNameEntity...」
	// 当userNameEntity是静态方法的时候、加或不加@Configuration，都会打印两遍「UserNameEntity...」
	@Bean
	public /*static*/ UserNameEntity userNameEntity() {
		return new UserNameEntity();
	}


	@Bean
	public UserPasswordEntity templateInfo() {
		userNameEntity();
		return new UserPasswordEntity();
	}

}