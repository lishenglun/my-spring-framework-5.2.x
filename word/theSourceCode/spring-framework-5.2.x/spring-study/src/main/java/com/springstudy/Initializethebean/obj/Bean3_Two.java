package com.springstudy.Initializethebean.obj;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

/**
 * 实例化bean的三种方式测试。方法二：注解的方式
 * 		Bean3_Two不能使用@Bean在Bean3Config_Two中创建，TemplateInfo、ComponentInfo才能创建成功
 */
@Service
public class Bean3_Two {

	@Bean
	public static TemplateInfo templateInfo() {
		return new TemplateInfo();
	}

	@Bean
	public ComponentInfo componentInfo() {
		return new ComponentInfo();
	}

}