package com.springstudy.msb.other.condition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/10/5 17:30
 */
@Configuration
@Conditional(MacOsCondition.class)
public class ConditionConfiguration {

	@Bean
	public User user(){
		User user = new User();
		user.setName("zhangsan");
		return user;
	}

}