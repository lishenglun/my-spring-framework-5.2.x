package com.springstudy.msb.other.converter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/10/7 10:45
 */
@Configuration
public class ConversionConfiguration {

	@Bean
	public ConversionService conversionService(){
		GenericConversionService genericConversionService=new GenericConversionService();

		// 添加Converter
		//genericConversionService.addConverter();

		return genericConversionService;
	}

}