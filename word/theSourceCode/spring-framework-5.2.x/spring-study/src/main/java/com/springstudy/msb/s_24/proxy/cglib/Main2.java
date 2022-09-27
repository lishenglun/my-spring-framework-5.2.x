package com.springstudy.msb.s_24.proxy.cglib;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/8 11:31 上午
 */
public class Main2 {

	public static void main(String[] args) {
		MyCalculator myCalculator = new MyCalculator();
		MyCalculator proxy = (MyCalculator) Enhancer.create(myCalculator.getClass(), new MethodInterceptor() {
			@Override
			public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
				return method.invoke(myCalculator, objects);
			}
		});
		proxy.add(1, 1);
	}

}