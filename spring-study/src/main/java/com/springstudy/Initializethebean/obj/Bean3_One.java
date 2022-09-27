package com.springstudy.Initializethebean.obj;

import org.springframework.context.annotation.Bean;

/**
 * 实例化bean的三种方式测试。方法一：xml的方式
 */
public class Bean3_One {


	public static TemplateInfo templateInfo() {
		return new TemplateInfo();
	}

	public ComponentInfo componentInfo() {
		return new ComponentInfo();
	}


}