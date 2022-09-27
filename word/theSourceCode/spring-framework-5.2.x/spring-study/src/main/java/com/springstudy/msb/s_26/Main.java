package com.springstudy.msb.s_26;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/13 6:07 下午
 */
public class Main {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext();
		ac.register(Main.class);
		ac.refresh();
	}

}