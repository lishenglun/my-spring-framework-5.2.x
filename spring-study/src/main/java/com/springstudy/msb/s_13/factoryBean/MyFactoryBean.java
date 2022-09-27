package com.springstudy.msb.s_13.factoryBean;

import org.springframework.beans.factory.FactoryBean;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description MyFactoryBean
 * @date 2022/5/1 11:41 上午
 */
public class MyFactoryBean implements FactoryBean<Hello> {

	@Override
	public Hello getObject() throws Exception {
		return new Hello();
	}

	@Override
	public Class<?> getObjectType() {
		return Hello.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}