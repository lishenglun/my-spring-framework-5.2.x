package com.springstudy.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/11/24 5:41 下午
 */
@Configuration
//@Import(BaSaiNuoNaRegister.class)
public class BaSaiNuoNaRegisterConfigrution {

	@Bean
	public static BaSaiNuoNaRegister BaSaiNuoNaRegister() {
		return new BaSaiNuoNaRegister();
	}

}