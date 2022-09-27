package com.springstudy.msb.s_20.cycle;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/24 11:35 下午
 */
public class A {

	@Autowired
	private B b;

	public B getB() {
		return b;
	}

	public void setB(B b) {
		this.b = b;
	}


	public void testPoint(){
		System.out.println("a-开始调用a.testPoint()");

		System.out.println("准备调用b.testPoint()");
		b.testPoint();
		System.out.println("已经调用b.testPoint()");

		System.out.println("a-调用完毕a.testPoint()");
	}

}