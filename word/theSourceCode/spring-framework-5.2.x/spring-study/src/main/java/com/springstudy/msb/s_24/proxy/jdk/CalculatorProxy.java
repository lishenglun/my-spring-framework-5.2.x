package com.springstudy.msb.s_24.proxy.jdk;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/6 4:34 下午
 */
public class CalculatorProxy {

	public static Calculator getProxy(final Calculator calculator) {
		// 准备好方法，准备好InvocationHandler属性
		// 然后变成一个class文件
		// 然后写入内存里面
		// 然后调用构造器，创建对象
		ClassLoader classLoader = calculator.getClass().getClassLoader();
		Class<?>[] interfaces = calculator.getClass().getInterfaces();
		InvocationHandler h = (proxy, method, args) -> {
			Object result = null;
			try {
				result = method.invoke(calculator, args);
			} catch (Exception e) {

			} finally {

			}
			return result;
		};
		return (Calculator) Proxy.newProxyInstance(classLoader,interfaces,h);
	}

}