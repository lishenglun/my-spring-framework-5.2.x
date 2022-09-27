package com.springstudy.msb.s_17;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/15 5:27 下午
 */
@Component
public class A {

	@Autowired
	private B b;

	@PostConstruct
	public void init() {
		System.out.println("a...init...");
		b.init();
	}

}