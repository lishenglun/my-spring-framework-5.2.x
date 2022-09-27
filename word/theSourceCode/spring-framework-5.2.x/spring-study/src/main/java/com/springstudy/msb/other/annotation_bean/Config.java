package com.springstudy.msb.other.annotation_bean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/8 8:44 下午
 */
@Configuration
public class Config {

	@Bean(autowireCandidate = false)
	public MyBean b() {
		MyBean myBean = new MyBean();
		myBean.setName("zhangsan");
		return myBean;
	}

}