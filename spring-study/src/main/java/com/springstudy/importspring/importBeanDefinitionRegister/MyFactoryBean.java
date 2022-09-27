package com.springstudy.importspring.importBeanDefinitionRegister;

import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/7 8:32 下午
 */
class MyFactoryBean implements FactoryBean<Object>, InvocationHandler {

	Class<?> clazz;

	public MyFactoryBean(Class<?> clazz) {
		this.clazz = clazz;
	}

	@Override
	public Object getObject() throws Exception {
		Class<?>[] classes = {clazz};
		return Proxy.newProxyInstance(this.getClass().getClassLoader(), classes, this);
	}

	@Override
	public Class<?> getObjectType() {
		return clazz;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		System.out.println("调用了invoke()方法...");

		//Class<?>[] parameterTypes = null;
		//if (args != null) {
		//	parameterTypes = new Class<?>[args.length];
		//	for (int i = 0; i < args.length; i++) {
		//		parameterTypes[i] = args[i].getClass();
		//	}
		//}

		/* 获取sql语句 */
		//Method interfaceMethod = proxy.getClass().getInterfaces()[0].getMethod(method.getName(), parameterTypes);
		//Select annotation = interfaceMethod.getDeclaredAnnotation(Select.class);
		//System.out.println("sql语句为：" + annotation.value());

		return null;
	}
}