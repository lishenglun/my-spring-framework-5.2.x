package com.springstudy.msb.s_24.proxy.cglib;


import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description cglib的回调
 *
 * 查看cglib的所有回调类型：{@link org.springframework.cglib.proxy.CallbackInfo}
 *
 * @date 2022/6/6 6:10 下午
 */
public class MyCglibMethodInvocation implements MethodInterceptor {

	@Override
	public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
		Object o1 = methodProxy.invokeSuper(o, objects);
		return o1;
	}

}