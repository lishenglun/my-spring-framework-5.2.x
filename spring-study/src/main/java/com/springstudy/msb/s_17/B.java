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
public class B {

	@Autowired
	private A a;

	@PostConstruct
	public void init() {
		System.out.println("b...init...");
		 //a.init();
	}

}