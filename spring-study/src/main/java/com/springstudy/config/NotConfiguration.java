package com.springstudy.config;

import com.springstudy.dao.impl.RealNameDaoImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 测试：配置类没有@Configuration，但是@Bean一个对象
 */
public class NotConfiguration {

	@Bean
	public RealNameDaoImpl getRealNameDaoImpl(){
		return new RealNameDaoImpl();
	}


}