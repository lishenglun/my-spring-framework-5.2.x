package com.springstudy.book2.chapter7_aop.cglib;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description cglib的使用示例
 * @date 2022/9/15 6:11 下午
 */
public class EnhancerDemo {

	public static void main(String[] args) {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(EnhancerDemo.class);
		enhancer.setCallback(new MethodInterceptorImpl());
		EnhancerDemo enhancerDemo = (EnhancerDemo) enhancer.create();
		enhancerDemo.test();
		System.out.println(enhancerDemo);
	}

	public void test() {
		System.out.println("EnhancerDemo test()");
	}

	private static class MethodInterceptorImpl implements MethodInterceptor {

		@Override
		public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
			System.out.println("before invoke" + method);
			Object result = methodProxy.invokeSuper(o, objects);
			System.out.println("after invoke" + method);
			return result;
		}
	}

}