package com.springstudy.msb.s_20.cycle;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/24 11:35 下午
 */
public class B {

	@Autowired
	private A a;

	public A getA() {
		return a;
	}

	public void setA(A a) {
		this.a = a;
	}

	public void testPoint(){
		System.out.println("b-开始调用b.testPoint()");

		System.out.println("准备调用a.testPoint()");
		a.testPoint();
		System.out.println("已经调用a.testPoint()");

		System.out.println("b-调用完毕b.testPoint()");
	}

}